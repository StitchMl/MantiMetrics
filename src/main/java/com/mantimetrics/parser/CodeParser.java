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

public class CodeParser {
    private static final Logger logger = LoggerFactory.getLogger(CodeParser.class);
    private final GitService gh;

    public CodeParser(GitService gh) {
        this.gh = gh;
    }

    /**
     * @param fileToKeys map file→pre-calculated JIRA-keys list
     */
    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String tag,
            MetricsCalculator calc,
            Map<String,List<String>> fileToKeys
    ) throws CodeParserException {

        logger.trace("Analysing {}/{}@{}", owner, repo, tag);
        Path repoRoot;
        try {
            repoRoot = gh.downloadAndUnzipRepo(owner, repo, tag);
        } catch (IOException e) {
            throw new CodeParserException("Download/Unzip failed for " + repo + "@" + tag, e);
        }

        List<MethodData> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(repoRoot)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        Path relPath = repoRoot.relativize(path);
                        if (relPath.getNameCount() > 1) {
                            relPath = relPath.subpath(1, relPath.getNameCount());
                        }
                        String rel = relPath.toString().replace('\\','/');

                        List<String> issueKeys = fileToKeys.getOrDefault(rel, List.of());

                        try {
                            String src = Files.readString(path);
                            new JavaParser().parse(src)
                                    .getResult()
                                    .ifPresent(cu -> cu.findAll(MethodDeclaration.class)
                                            .forEach(m -> m.getRange().ifPresent(r -> {
                                                var mets = calc.computeAll(m);
                                                MethodData md = new MethodData.Builder()
                                                        .projectName(repo)
                                                        .path("/" + rel + "/")
                                                        .methodSignature(m.getDeclarationAsString(true, true, true))
                                                        .releaseId(tag)
                                                        .versionId(tag)
                                                        .commitId(tag)
                                                        .metrics(mets)
                                                        .commitHashes(issueKeys)
                                                        .buggy(false)
                                                        .build();
                                                out.add(md);
                                            })));
                        } catch (IOException | ParseProblemException ex) {
                            logger.warn("Skipping {} due to {}", rel, ex.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new CodeParserException("Directory walk failed", e);
        } finally {
            // cleaning the temporary dir
            try (Stream<Path> stream = Files.walk(repoRoot)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                // on Windows often pack/.idx is still locked: it only logs to debug
                                logger.warn("Failed to delete {}: {}", p, e.getMessage());
                            }
                        });
                logger.trace("Deleted temp dir {}", repoRoot);
            } catch (IOException e) {
                logger.error("Failed to delete temp dir {}: {}", repoRoot, e.getMessage());
            }
        }

        return out;
    }
}