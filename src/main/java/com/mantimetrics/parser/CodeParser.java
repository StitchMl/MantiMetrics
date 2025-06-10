package com.mantimetrics.parser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseStart;
import com.github.javaparser.JavaParser;
import com.github.javaparser.Providers;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mantimetrics.clone.SourceCollectionException;
import com.mantimetrics.git.GitService;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.MethodData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class CodeParser {
    private static final Logger LOG = LoggerFactory.getLogger(CodeParser.class);
    private final GitService git;
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    /**
     * @param git GitService instance to download and unzip the repository
     */
    public CodeParser(GitService git) {
        this.git = git;
    }

    /**
     * Download and unpack the release without deleting it.
     */
    public Path downloadRelease(String owner, String repo, String tag) throws CodeParserException {
        try {
            return git.downloadAndUnzipRepo(owner, repo, tag, "release-" + tag);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CodeParserException("Interrupted downloading " + tag, ie);
        } catch (IOException ioe) {
            throw new CodeParserException("Download/Unzip failed for " + repo + '@' + tag, ioe);
        }
    }

    /**
     * Parsing and metrics on an already downloaded root; **do not** delete the folder.
     */
    public List<MethodData> parseFromDirectory(
            Path root,
            String repo,
            String tag,
            MetricsCalculator calc,
            Map<String, List<String>> fileToKeys) {
        // Count all .java before filters
        long totalFiles;
        try (Stream<Path> all = Files.walk(root)) {
            totalFiles = all
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();
        } catch (IOException e) {
            totalFiles = -1;
        }

        List<MethodData> out = new ArrayList<>();

        // 1) Define the matcher for test directories
        PathMatcher testDirMatcher = FileSystems.getDefault()
                .getPathMatcher("glob:**/src/test/java/**");

        // 2) Matcher for test files by name (*Test.java, *IT.java)
        PathMatcher testClassMatcher = FileSystems.getDefault()
                .getPathMatcher("glob:**/*{Test,IT}.java");

        // 3) Matcher for output and generation directories
        PathMatcher ignoreMatcher = FileSystems.getDefault()
                .getPathMatcher("glob:**/{target,build,generated-sources}/**");

        // 4) Matcher for common directories
        PathMatcher otherMatcher = FileSystems.getDefault()
                .getPathMatcher("glob:**/{common,utils,examples}/**");

        // 5) Matcher for infrastructure directories
        PathMatcher infraMatcher = FileSystems.getDefault()
                .getPathMatcher("glob:**/{api,internal}/**");

        // 6) Matcher for DTO and model directories
        PathMatcher dtoIgnore = FileSystems.getDefault()
                .getPathMatcher("glob:**/{dto,model}/**");

        // 7) Matcher for resources and config directories
        PathMatcher resourcesIgnore = FileSystems.getDefault()
                .getPathMatcher("glob:**/{resources,config}/**");

        // 8) Matcher for generated sources
        PathMatcher genProto = FileSystems.getDefault()
                .getPathMatcher("glob:**/{gen-src,generated-sources,grpc}/**");

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !testDirMatcher.matches(p))
                    .filter(p -> !testClassMatcher.matches(p))
                    .filter(p -> !ignoreMatcher.matches(p))
                    .filter(p -> !otherMatcher.matches(p))
                    .filter(p -> !infraMatcher.matches(p))
                    .filter(p -> !dtoIgnore.matches(p))
                    .filter(p -> !resourcesIgnore.matches(p))
                    .filter(p -> !genProto.matches(p))
                    .forEach(p -> {
                        Optional<String> relOpt = shouldSkip(root, p);
                        relOpt.ifPresent(relUnix -> {
                            List<String> jiraKeys = fileToKeys.getOrDefault(relUnix, List.of());
                            collectMethods(p, relUnix, repo, tag, jiraKeys, calc, out);
                        });
                    });
        } catch (IOException io) {
            throw new UncheckedIOException("I/O walking " + root, io);
        } finally {
            deleteRecursively(root);
        }

        long processedFiles = out.stream()
                .map(MethodData::getPath)
                .distinct()
                .count();

        LOG.info("release={} filesTotali={} filesProcessati={}",
                tag, totalFiles, processedFiles);

        return out;
    }

    /** Returns the normalized unix-style path, or empty() if the file must be skipped. */
    private static Optional<String> shouldSkip(Path root, Path path) {
        // 1) Skip files too big
        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                LOG.warn("Skipping VERY large file {}", path);
                return Optional.empty();
            }
        } catch (IOException ex) {
            LOG.warn("Cannot stat {}, skipping – {}", path, ex.getMessage());
            return Optional.empty();
        }

        // 2) Calculate the relevant route
        Path rel = root.relativize(path);
        String relUnix = rel.toString().replace('\\', '/');

        // 3) Always removes the first segment (e.g. "repo-1.0.0/src/..." -> "src/...")
        int slash = relUnix.indexOf('/');
        if (slash >= 0) {
            relUnix = relUnix.substring(slash + 1);
        }

        // 4) Returns the normalized path
        return Optional.of(relUnix);
    }

    /** Parses the file, computes metrics, adds MethodData objects to *sink*. */
    private static void collectMethods(Path file,
                                       String relUnix,
                                       String repo,
                                       String tag,
                                       List<String> jiraKeys,
                                       MetricsCalculator calc,
                                       List<MethodData> sink) {
        ParserConfiguration config = new ParserConfiguration();
        JavaParser parser = new JavaParser(config);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            parser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(reader))
                    .getResult()
                    .ifPresent(cu -> cu.findAll(MethodDeclaration.class)
                            .forEach(m -> m.getRange().ifPresent(r ->
                            {
                                try {
                                    sink.add(new MethodData.Builder()
                                            .projectName(repo)
                                            .path('/' + relUnix + '/')
                                            .methodSignature(m.getDeclarationAsString(true, true, true))
                                            .releaseId(tag)
                                            .metrics(calc.computeAll(m))
                                            .commitHashes(jiraKeys)
                                            .buggy(false)
                                            .startLine(r.begin.line)
                                            .endLine(r.end.line)
                                            .build());
                                } catch (SourceCollectionException | IOException sce) {
                                    LOG.warn("Failed to compute metrics for {}: {}", file, sce.getMessage());
                                }
                            })));
        } catch (ParseProblemException ppe) {
            LOG.warn("Failed to parse {}: {}", file, ppe.getMessage());
        } catch (IOException ioe) {
            LOG.warn("Failed to read {}: {}", file, ioe.getMessage());
        }
    }

    /** Best-effort recursive delete (no exceptions propagated). */
    private static void deleteRecursively(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            LOG.warn("Failed to delete {}: {}", p, e.getMessage());
                        }
                    });
            LOG.trace("Deleted temp dir {}", dir);
        } catch (IOException ignore) {
            LOG.warn("Failed to delete {}", dir);
        }
    }
}