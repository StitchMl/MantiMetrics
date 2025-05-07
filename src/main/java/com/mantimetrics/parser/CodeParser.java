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

    public CodeParser(GitService git) { this.git = git; }

    /**
     * @param fileToKeys mappa immutabile file → JIRA-keys
     */
    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String tag,
            MetricsCalculator calc,
            Map<String,List<String>> fileToKeys)
            throws CodeParserException {

        LOG.trace("Analysing {}/{}@{}", owner, repo, tag);

        /* 1. unzip release */
        Path root;
        try {
            root = git.downloadAndUnzipRepo(owner, repo, tag, "release-" + tag);
        } catch (IOException io) {
            throw new CodeParserException("Download/Unzip failed for "+repo+'@'+tag, io);
        }

        List<MethodData> out = new ArrayList<>();

        /* 2. walk all .java files */
        try (Stream<Path> files = Files.walk(root)) {

            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                     /* ---- path normalisation ----
                        ALWAYS removes the first segment (ZIP root folder)   */
                        Path relPath = root.relativize(path);
                        if (relPath.getNameCount() > 1)
                            relPath = relPath.subpath(1, relPath.getNameCount());

                        String rel = relPath.toString().replace('\\', '/');

                        /* any JIRA keys already calculated */
                        List<String> issueKeys = fileToKeys.getOrDefault(rel, List.of());

                        /* parsing & metrics */
                        try {
                            var cuOpt = new JavaParser().parse(Files.readString(path)).getResult();
                            if (cuOpt.isEmpty()) return;

                            cuOpt.get().findAll(MethodDeclaration.class)
                                    .forEach(m -> m.getRange().ifPresent(r -> {

                                        var metrics = calc.computeAll(m);

                                        out.add(new MethodData.Builder()
                                                .projectName(repo)
                                                .path("/" + rel + '/')
                                                .methodSignature(
                                                        m.getDeclarationAsString(true,true,true))
                                                .releaseId(tag)
                                                .versionId(tag)
                                                .commitId(tag)
                                                .metrics(metrics)
                                                .commitHashes(issueKeys)
                                                .buggy(false)            // verrà aggiornato a valle
                                                .build());
                                    }));
                        } catch (IOException | ParseProblemException ex) {
                            LOG.warn("Skipping {} – {}", rel, ex.getMessage());
                        }
                    });

        } catch (IOException io) {
            throw new CodeParserException("I/O walking " + root, io);
        } finally {
            /* 3. cleanup temp dir */
            try (Stream<Path> w = Files.walk(root)) {
                w.sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); }
                        catch (IOException ignored) {} });
            } catch (IOException ignored) {}
            LOG.trace("Deleted temp dir {}", root);
        }

        return out;
    }
}