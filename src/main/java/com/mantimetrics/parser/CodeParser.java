package com.mantimetrics.parser;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseStart;
import com.github.javaparser.JavaParser;
import com.github.javaparser.Providers;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.body.MethodDeclaration;
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
     * @param fileToKeys immutable map file → JIRA keys, prepared upstream
     */
    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String tag,
            MetricsCalculator calc,
            Map<String, List<String>> fileToKeys) throws CodeParserException {
        LOG.trace("Analysing {}/{}@{}", owner, repo, tag);

        // 1. Download & unzip
        Path root = downloadRelease(owner, repo, tag);

        List<MethodData> out = parseFromDirectory(root, repo, tag, calc, fileToKeys);

        deleteRecursively(root);

        return out;
    }

    /**
     * Parsing and metrics on an already downloaded root; **does not** delete the folder.
     */
    public List<MethodData> parseFromDirectory(
            Path root,
            String repo,
            String tag,
            MetricsCalculator calc,
            Map<String, List<String>> fileToKeys) {
        List<MethodData> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        Optional<String> relOpt = shouldSkip(root, p, repo);
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
        if (rel.getNameCount() > 1 && rel.getName(0).toString().startsWith(repo + "-")) {
            rel = rel.subpath(1, rel.getNameCount());
        }
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
        ParserConfiguration config = new ParserConfiguration();
        JavaParser parser = new JavaParser(config);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            parser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(reader))
                    .getResult()
                    .ifPresent(cu -> cu.findAll(MethodDeclaration.class)
                            .forEach(m -> m.getRange().ifPresent(r ->
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
                                            .build()))));
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