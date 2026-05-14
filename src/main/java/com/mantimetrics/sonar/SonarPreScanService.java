package com.mantimetrics.sonar;

import com.mantimetrics.git.GitService;
import com.mantimetrics.util.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs {@code mvn sonar:sonar} for every release tag that has not yet been analysed on SonarCloud.
 *
 * <p>Each scan sets {@code sonar.projectVersion} to the release tag so that
 * {@link SonarSmellIndex#getSmellsForTag(String)} can later look up the exact snapshot.
 *
 * <p>The service is a no-op when:
 * <ul>
 * <li>{@code SONAR_TOKEN} environment variable is absent, or</li>
 * <li>no Maven executable ({@code mvn} / {@code mvn.cmd}) can be found via
 * {@code MAVEN_HOME}, {@code M2_HOME} or {@code PATH}.</li>
 * </ul>
 * In both cases a warning is logged and the caller continues with whatever analyses already exist.
 */
public final class SonarPreScanService {
 private static final Logger LOG = LoggerFactory.getLogger(SonarPreScanService.class);

 /** File name of the Maven project descriptor. */
 private static final String POM_XML = "pom.xml";

 /** Seconds between polls when waiting for an analysis to appear on SonarCloud. */
 private static final int POLL_INTERVAL_SEC = 20;
 /** Maximum seconds to wait for one analysis to be indexed after Maven exits. */
 private static final int POLL_TIMEOUT_SEC = 300;
 /** Maximum minutes allowed for one {@code mvn sonar:sonar} process. */
 private static final int MVN_TIMEOUT_MIN = 20;

 private final GitService gitService;
 private final SonarCloudClient sonarClient;
 private final String sonarToken;

 /**
 * Creates the service.
 *
 * @param gitService Git service used to download and fully extract release ZIPs
 * @param sonarClient SonarCloud REST client used to check existing analyses
 * @param sonarToken SonarCloud authentication token (may be {@code null} — pre-scan is skipped)
 */
 public SonarPreScanService(GitService gitService, SonarCloudClient sonarClient, String sonarToken) {
 this.gitService = gitService;
 this.sonarClient = sonarClient;
 this.sonarToken = sonarToken;
 }

 /**
 * Scans every release tag in {@code tags} that does not yet have a versioned analysis on
 * SonarCloud. Already-scanned tags are skipped so the phase is fully resumable.
 *
 * @param owner GitHub repository owner
 * @param repo GitHub repository name
 * @param tags all release tags to consider (ordered oldest → newest)
 * @param projectKey SonarCloud project key (e.g. {@code StitchMl_avro})
 * @param bar progress bar that is stepped once per tag (scanned or skipped)
 * @return number of releases newly scanned
 * @throws SonarCloudException when existing analyses cannot be fetched
 */
 public int scanMissingReleases(
 String owner, String repo,
 List<String> tags, String projectKey,
 ProgressBar bar
 ) throws SonarCloudException {

 if (sonarToken == null || sonarToken.isBlank()) {
 LOG.warn("SONAR_TOKEN not set — skipping SonarCloud pre-scan. " +
 "Set the env variable and re-run to get per-release smell counts.");
 tags.forEach(bar::step);
 return 0;
 }

 String mvn = findMavenExecutable();
 if (mvn == null) {
 LOG.warn("Maven executable not found (set MAVEN_HOME/M2_HOME or add mvn to PATH) — " +
 "skipping per-release pre-scan.");
 tags.forEach(bar::step);
 return 0;
 }

 // Derive organization: everything before the first '_', lowercased
 String organization = projectKey.contains("_")
 ? projectKey.substring(0, projectKey.indexOf('_')).toLowerCase()
 : projectKey.toLowerCase();

 // Collect version tags that already exist on SonarCloud
 Set<String> alreadyScanned = new HashSet<>();
 for (SonarAnalysis a : sonarClient.fetchAnalyses(projectKey)) {
 if (a.projectVersion() != null && !a.projectVersion().isBlank()) {
 alreadyScanned.add(a.projectVersion());
 }
 }
 LOG.info("SonarCloud {}: {}/{} releases already scanned",
 projectKey, alreadyScanned.size(), tags.size());

 int newScans = 0;
 boolean autoAnalysisBlocked = false;
 for (String tag : tags) {
 if (alreadyScanned.contains(tag) || autoAnalysisBlocked) {
 bar.step(tag);
 continue;
 }
 try {
 scanRelease(owner, repo, tag, projectKey, organization, mvn);
 newScans++;
 } catch (AutomaticAnalysisEnabledException e) {
 autoAnalysisBlocked = true;
 LOG.warn("┌─────────────────────────────────────────────────────────────────");
 LOG.warn("│ SonarCloud Automatic Analysis is ON — manual scans are blocked.");
 LOG.warn("│ Disable it here: https://sonarcloud.io/project/analysis_method?id={}", projectKey);
 LOG.warn("│ Select \"Other CI tools\", save, then re-run.");
 LOG.warn("│ Pre-scan skipped for all remaining releases.");
 LOG.warn("└─────────────────────────────────────────────────────────────────");
 } catch (InterruptedException e) {
 Thread.currentThread().interrupt();
 LOG.debug("SonarCloud pre-scan interrupted for {} — {}", tag, e.getMessage());
 } catch (Exception e) {
 LOG.debug("SonarCloud pre-scan skipped for {} — {}", tag, e.getMessage());
 }
 bar.step(tag);
 }
 return newScans;
 }

 // ── private ──────────────────────────────────────────────────────────────

 /**
 * Fully extracts the release ZIP, runs {@code mvn sonar:sonar} and waits until the
 * analysis appears on SonarCloud.
 */
 private void scanRelease(
 String owner, String repo, String tag,
 String projectKey, String organization, String mvn
 ) throws IOException, InterruptedException, SonarCloudException {

 LOG.info("SonarCloud pre-scan: scanning {}...", tag);

 // Sanitize tag for use in directory name (remove characters invalid on Windows)
 String safeName = tag.replaceAll("[^a-zA-Z0-9._-]", "_");
 @SuppressWarnings("java:S5443")
 Path tempDir = Files.createTempDirectory("sonar-" + safeName + "-");
 boolean r = tempDir.toFile().setReadable(true, true);
 boolean w = tempDir.toFile().setWritable(true, true);
 boolean x = tempDir.toFile().setExecutable(true, true);
 if (!r || !w || !x) {
 LOG.debug("Could not set all permissions on temp directory: {}", tempDir);
 }
 try {
 gitService.extractReleaseFull(owner, repo, tag, tempDir);

 // Locate the Maven project root (may be a subdirectory, e.g. lang/java/ for Avro)
 Path mavenRoot = findJavaPom(tempDir)
 .orElseThrow(() -> new IOException("No pom.xml found in extracted ZIP for " + tag));
 LOG.debug("SonarCloud pre-scan: using pom.xml at {}", tempDir.relativize(mavenRoot));

 runMavenSonar(mvn, mavenRoot, projectKey, organization, tag);
 waitForAnalysis(projectKey, tag);
 LOG.info("SonarCloud pre-scan: {} ✓", tag);
 } finally {
 deleteTree(tempDir);
 }
 }

 /**
 * Finds the directory containing the best {@code pom.xml} for a Java analysis inside {@code root}.
 *
 * <p>Strategy (first match wins):
 * <ol>
 * <li>Root {@code pom.xml} + {@code src/main/java} present → pure Java project, use root.</li>
 * <li>A {@code pom.xml} whose path contains a {@code java}-named segment (e.g.
 * {@code lang/java/pom.xml}) → multi-language project layout (Avro, etc.), use that.</li>
 * <li>Root {@code pom.xml} without {@code src/main/java} → top-level aggregator, still use root.</li>
 * <li>Shallowest non-noisy {@code pom.xml} anywhere in the tree.</li>
 * </ol>
 *
 * @param root extraction root of the release ZIP
 * @return parent directory of the selected {@code pom.xml}, or empty when none is found
 */
 private static Optional<Path> findJavaPom(Path root) throws IOException {
 // 1) Root pom.xml with src/main/java present → straightforward Java project
 if (Files.isRegularFile(root.resolve(POM_XML))
 && Files.isDirectory(root.resolve("src/main/java"))) {
 return Optional.of(root);
 }

 // 2) Look for a pom.xml inside a directory named "java" (multi-language layouts)
 try (Stream<Path> walk = Files.walk(root, 5)) {
 Optional<Path> javaDirPom = walk
 .filter(p -> POM_XML.equals(p.getFileName() != null ? p.getFileName().toString() : "")
 && Files.isRegularFile(p))
 .filter(p -> hasJavaSegment(root.relativize(p)))
 .filter(p -> !isNoisyPath(root.relativize(p)))
 .min(Comparator.comparingInt(Path::getNameCount))
 .map(Path::getParent);
 if (javaDirPom.isPresent()) {
 return javaDirPom;
 }
 }

 // 3) Root pom.xml exists (aggregator without src/main/java) → still valid
 if (Files.isRegularFile(root.resolve(POM_XML))) {
 return Optional.of(root);
 }

 // 4) Shallowest non-noisy pom.xml anywhere in the tree
 try (Stream<Path> walk = Files.walk(root)) {
 return walk
 .filter(p -> POM_XML.equals(p.getFileName() != null ? p.getFileName().toString() : "")
 && Files.isRegularFile(p))
 .filter(p -> !isNoisyPath(root.relativize(p)))
 .min(Comparator.comparingInt(Path::getNameCount))
 .map(Path::getParent);
 }
 }

 /**
 * Returns {@code true} when any non-final path segment of {@code relative} is exactly {@code java}.
 * This identifies multi-language layouts where the Java module lives under e.g. {@code lang/java/}.
 */
 private static boolean hasJavaSegment(Path relative) {
 for (int i = 0; i < relative.getNameCount() - 1; i++) {
 if ("java".equalsIgnoreCase(relative.getName(i).toString())) {
 return true;
 }
 }
 return false;
 }

 /**
 * Returns {@code true} when the relative path contains a path segment that signals
 * it is not the main project pom (doc, example, test, archetype, sample, etc.).
 */
 private static boolean isNoisyPath(Path relative) {
 for (int i = 0; i < relative.getNameCount(); i++) {
 String seg = relative.getName(i).toString().toLowerCase();
 if (seg.equals("doc") || seg.equals("docs")
 || seg.contains("example") || seg.contains("sample")
 || seg.contains("archetype") || seg.contains("test")) {
 return true;
 }
 }
 return false;
 }

 /**
 * Builds and executes {@code mvn sonar:sonar}, waiting for the process to exit.
 * Tests are skipped so the scan is fast; SCM is disabled to avoid Git access issues inside
 * the extracted temp directory.
 */
 private void runMavenSonar(
 String mvn, Path workDir,
 String projectKey, String organization, String tag
 ) throws IOException, InterruptedException {

 List<String> cmd = new ArrayList<>();
 cmd.add(mvn);
 cmd.add("sonar:sonar");
 cmd.add("-Dsonar.projectKey=" + projectKey);
 cmd.add("-Dsonar.organization=" + organization);
 cmd.add("-Dsonar.projectVersion=" + tag);
 cmd.add("-Dsonar.host.url=https://sonarcloud.io");
 cmd.add("-Dsonar.token=" + sonarToken);
 cmd.add("-Dsonar.scm.disabled=true");
 // Allow source-only analysis: avoids "binary files not found" when the project
 // has never been compiled inside this temp directory
 cmd.add("-Dsonar.java.binaries=.");
 cmd.add("-DskipTests=true");
 cmd.add("-Dmaven.test.skip=true");
 // Suppress most Maven output — sonar goal is still verbose but this keeps it manageable
 cmd.add("--batch-mode");
 cmd.add("--no-transfer-progress");

 ProcessBuilder pb = new ProcessBuilder(cmd);
 pb.directory(workDir.toFile());
 pb.redirectErrorStream(true); // merge stderr into stdout
 Process proc = pb.start();

 // Drain output (prevents buffer-full deadlock); log at DEBUG
 StringBuilder output = new StringBuilder();
 try (InputStream is = proc.getInputStream()) {
 byte[] buf = new byte[4096];
 int n;
 while ((n = is.read(buf)) != -1) {
 output.append(new String(buf, 0, n, StandardCharsets.UTF_8));
 }
 }

 boolean finished = proc.waitFor(MVN_TIMEOUT_MIN, TimeUnit.MINUTES);
 if (!finished) {
 proc.destroyForcibly();
 throw new IOException("mvn sonar:sonar timed out after " + MVN_TIMEOUT_MIN + " min for " + tag);
 }
 if (proc.exitValue() != 0) {
 String outputStr = output.toString();
 // Detect the "Automatic Analysis enabled" configuration conflict — actionable, fatal for all tags
 if (outputStr.contains("Automatic Analysis is enabled")) {
 throw new AutomaticAnalysisEnabledException();
 }
 LOG.debug("mvn sonar:sonar output for {}:\n{}", tag, outputStr);
 throw new IOException("mvn sonar:sonar exited with code " + proc.exitValue() + " for " + tag);
 }
 }

 /**
 * Polls SonarCloud until an analysis tagged with {@code expectedVersion} appears,
 * or the timeout expires.
 */
 private void waitForAnalysis(String projectKey, String expectedVersion)
 throws InterruptedException, SonarCloudException {
 long deadline = System.currentTimeMillis() + (long) POLL_TIMEOUT_SEC * 1_000;
 while (System.currentTimeMillis() < deadline) {
 boolean found = sonarClient.fetchAnalyses(projectKey).stream()
 .anyMatch(a -> expectedVersion.equals(a.projectVersion()));
 if (found) return;
 Thread.sleep(POLL_INTERVAL_SEC * 1_000L);
 }
 LOG.warn("SonarCloud: analysis for version '{}' did not appear within {}s — continuing anyway",
 expectedVersion, POLL_TIMEOUT_SEC);
 }

 /**
 * Locates the Maven executable.
 * Checks {@code MAVEN_HOME/bin/mvn[.cmd]} and {@code M2_HOME/bin/mvn[.cmd]} first,
 * then falls back to PATH.
 *
 * @return absolute path string, or {@code null} when not found
 */
 static String findMavenExecutable() {
 boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
 String suffix = windows ? "\\bin\\mvn.cmd" : "/bin/mvn";
 String binaryName = windows ? "mvn.cmd" : "mvn";

 // 1) MAVEN_HOME / M2_HOME environment variable
 for (String envVar : new String[]{"MAVEN_HOME", "M2_HOME"}) {
 String home = System.getenv(envVar);
 if (home != null && !home.isBlank()) {
 Path bin = Path.of(home + suffix);
 if (Files.isRegularFile(bin)) {
 LOG.debug("Maven found via {}: {}", envVar, bin);
 return bin.toString();
 }
 }
 }

 // 2) Assume it's on PATH — try a quick version check
 try {
 Process probe = new ProcessBuilder(binaryName, "--version")
 .redirectErrorStream(true)
 .start();
 // Drain output so it doesn't block
 try (InputStream is = probe.getInputStream()) {
 is.transferTo(OutputStream.nullOutputStream());
 }
 if (probe.waitFor(10, TimeUnit.SECONDS)) {
 LOG.debug("Maven found on PATH");
 return binaryName;
 }
 } catch (InterruptedException ignored) {
 Thread.currentThread().interrupt();
 } catch (IOException ignored) {
 // not on PATH
 }
 return null;
 }

 /**
 * Thrown when {@code mvn sonar:sonar} fails because SonarCloud Automatic Analysis
 * is still enabled on the project, blocking all manual scans.
 */
 private static final class AutomaticAnalysisEnabledException extends IOException {
 AutomaticAnalysisEnabledException() {
 super("SonarCloud Automatic Analysis is enabled — manual scan blocked");
 }
 }

 /** Recursively deletes a directory tree, silently ignoring errors. */
 private static void deleteTree(Path root) {
 try {
 Files.walk(root)
 .sorted(Comparator.reverseOrder())
 .forEach(p -> p.toFile().delete());
 } catch (IOException ignored) { /* cleanup best-effort */ }
 }
}
