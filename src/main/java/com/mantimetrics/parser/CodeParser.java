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

    public CodeParser(GitService git) {
        this.git = git;
    }

    /**
     * @param fileToKeys map *immutable* file → JIRA keys calculated upstream
     */
    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String tag,
            MetricsCalculator calc,
            Map<String,List<String>> fileToKeys) throws CodeParserException {

        LOG.trace("Analysing {}/{}@{}", owner, repo, tag);

        /* 1. download and unpack the tag */
        final Path root;
        try {
            root = git.downloadAndUnzipRepo(owner, repo, tag, "release-" + tag);
        } catch (IOException | InterruptedException io) {
            throw new CodeParserException("Download/Unzip failed for "+repo+'@'+tag, io);
        }

        List<MethodData> result = new ArrayList<>();

        /* 2. visit all .java (a walk is lazy, uses little memory) */
        try (Stream<Path> files = Files.walk(root)) {

            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {

                        /* normalizes the relative path */
                        Path rel = root.relativize(path);
                        // if the first segment is <repo>-<tag>/ we skip it
                        if (rel.getNameCount() > 1 &&
                                rel.getName(0).toString().startsWith(repo + "-"))
                            rel = rel.subpath(1, rel.getNameCount());

                        String relUnix = rel.toString().replace('\\', '/');

                        List<String> issueKeys = fileToKeys.getOrDefault(relUnix, List.of());

                        /* files > 8 MB → skip to avoid OOME / very slow parsing */
                        try {
                            if (Files.size(path) > 8 * 1024 * 1024) {
                                LOG.warn("Skipping VERY large file {}", relUnix);
                                return;
                            }
                        } catch (IOException sizeEx) {
                            LOG.warn("IOException sizeEx - Skipping {} – {}", relUnix, sizeEx.getMessage());
                        }

                        /* parsing & metrics */
                        try {
                            var cuOpt = PARSER.parse(Files.readString(path)).getResult();
                            if (cuOpt.isEmpty()) return;

                            cuOpt.get().findAll(MethodDeclaration.class)
                                    .forEach(m -> m.getRange().ifPresent(r -> result.add(
                                            new MethodData.Builder()
                                                    .projectName(repo)
                                                    .path("/" + relUnix + '/')
                                                    .methodSignature(
                                                            m.getDeclarationAsString(true, true, true))
                                                    .releaseId(tag)
                                                    .versionId(tag)
                                                    .commitId(tag)
                                                    .metrics(calc.computeAll(m))
                                                    .commitHashes(issueKeys)
                                                    .buggy(false)
                                                    .build())));
                        } catch (IOException | ParseProblemException ex) {
                            LOG.warn("Skipping {} – {}", relUnix, ex.getMessage());
                        }
                    });

        } catch (IOException io) {
            throw new CodeParserException("I/O walking " + root, io);
        } finally {
            /* 3. cleaning the temporary dir */
            try (Stream<Path> w = Files.walk(root)) {
                w.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            } catch (IOException ignored) {}
            LOG.trace("Deleted temp dir {}", root);
        }

        return result;
    }
}