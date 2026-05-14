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
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
 for (String tag : tags) {
 if (alreadyScanned.contains(tag)) {
 bar.step(tag);
 continue;
 }
 try {
 scanRelease(owner, repo, tag, projectKey, organization, mvn);
 newScans++;
 } catch (Exception e) {
 LOG.warn("SonarCloud pre-scan failed for {} — skipping: {}", tag, e.getMessage());
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
 Path tempDir = Files.createTempDirectory("sonar-" + safeName + "-");
 try {
 gitService.extractReleaseFull(owner, repo, tag, tempDir);
 runMavenSonar(mvn, tempDir, projectKey, organization, tag);
 waitForAnalysis(projectKey, tag);
 LOG.info("SonarCloud pre-scan: {} ✓", tag);
 } finally {
 deleteTree(tempDir);
 }
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
 LOG.debug("mvn sonar:sonar output for {}:\n{}", tag, output);
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
 } catch (IOException | InterruptedException ignored) {
 // not on PATH
 }
 return null;
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
