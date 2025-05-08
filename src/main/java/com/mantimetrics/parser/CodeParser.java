package com.mantimetrics.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mantimetrics.git.GitService;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.MethodData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class CodeParser {

    private static final Logger LOG = LoggerFactory.getLogger(CodeParser.class);
    private final GitService git;

    /** a single instance of parser, reusable and thread-safe */
    private static final JavaParser PARSER = new JavaParser();
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    public CodeParser(GitService git) {
        this.git = git;
    }

    /**
     * @param fileToKeys immutable map file → JIRA keys, prepared upstream
     */
    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String tag,
            MetricsCalculator calc,
            Map<String, List<String>> fileToKeys) throws CodeParserException {

        LOG.trace("Analysing {}/{}@{}", owner, repo, tag);

        /* 1 ─ download & unpack */
        final Path root;
        try {
            root = git.downloadAndUnzipRepo(owner, repo, tag, "release-" + tag);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new CodeParserException("Interrupted while downloading " + tag, ie);
        } catch (IOException ioe) {
            throw new CodeParserException("Download/Unzip failed for " + repo + '@' + tag, ioe);
        }

        List<MethodData> out = new ArrayList<>();

        /* 2 ─ lazy walk of .java files */
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        Optional<String> relOpt = shouldSkip(root, p, repo);
                        if (relOpt.isEmpty()) return;

                        String relUnix = relOpt.get();
                        List<String> jiraKeys = fileToKeys.getOrDefault(relUnix, List.of());

                        collectMethods(p, relUnix, repo, tag, jiraKeys, calc, out);
                    });

        } catch (IOException io) {
            throw new CodeParserException("I/O walking " + root, io);
        } finally {
            deleteRecursively(root);
        }
        return out;
    }

    /** Returns the normalized unix-style path, or empty() if the file must be skipped. */
    private static Optional<String> shouldSkip(Path root, Path path, String repo) {
        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                LOG.warn("Skipping VERY large file {}", path);
                return Optional.empty();
            }
        } catch (IOException ex) {
            LOG.warn("Cannot stat {}, skipping – {}", path, ex.getMessage());
            return Optional.empty();
        }

        Path rel = root.relativize(path);
        if (rel.getNameCount() > 1 && rel.getName(0).toString().startsWith(repo + "-"))
            rel = rel.subpath(1, rel.getNameCount());

        return Optional.of(rel.toString().replace('\\', '/'));
    }

    /** Parses the file, computes metrics, adds MethodData objects to *sink*. */
    private static void collectMethods(Path file,
                                       String relUnix,
                                       String repo,
                                       String tag,
                                       List<String> jiraKeys,
                                       MetricsCalculator calc,
                                       List<MethodData> sink) {
        try {
            PARSER.parse(Files.readString(file))
                    .getResult()
                    .ifPresent(cu -> cu.findAll(MethodDeclaration.class)
                            .forEach(m -> m.getRange().ifPresent(r ->
                                    sink.add(new MethodData.Builder()
                                            .projectName(repo)
                                            .path('/' + relUnix + '/')
                                            .methodSignature(m.getDeclarationAsString(true, true, true))
                                            .releaseId(tag)
                                            .versionId(tag)
                                            .commitId(tag)
                                            .metrics(calc.computeAll(m))
                                            .commitHashes(jiraKeys)
                                            .buggy(false)
                                            .build()))));
        } catch (ParseProblemException ex) {
            LOG.warn("Failed to parse {}: {}", file, ex.getMessage());
        } catch (IOException ex) {
            LOG.warn("Failed to read {}: {}", file, ex.getMessage());
        } catch (RuntimeException ex) {
            LOG.warn("Failed to process {}: {}", file, ex.getMessage());
        }
    }

    /** Best-effort recursive delete (no exceptions propagated). */
    private static void deleteRecursively(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignore) {
                    LOG.warn("[INT] Failed to delete {}", p);
                }
            });
            LOG.trace("Deleted temp dir {}", dir);
        } catch (IOException ignore) {
            LOG.warn("[EXT] Failed to delete {}", dir);
        }
    }
}