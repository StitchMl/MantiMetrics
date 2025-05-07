package com.mantimetrics.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
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

        logger.info("Analysing {}/{}@{}", owner, repo, tag);

        // 1) unzip the release
        Path repoRoot;
        try {
            repoRoot = gh.downloadAndUnzipRepo(owner, repo, tag);
        } catch (IOException e) {
            throw new CodeParserException("Download/Unzip failed for " + repo + "@" + tag, e);
        }

        // 2) releaseId and commitId = tag

        List<MethodData> out = new ArrayList<>();

        try (Stream<Path> files = Files.walk(repoRoot)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        // relative path calculation without the root folder created by the ZIP
                        Path relPath = repoRoot.relativize(path);
                        if (relPath.getNameCount() > 1) {
                            relPath = relPath.subpath(1, relPath.getNameCount());
                        }
                        String rel = relPath.toString().replace('\\','/');

                        // JIRA-keys for this file
                        List<String> issueKeys = fileToKeys.getOrDefault(rel, List.of());

                        try {
                            String src = Files.readString(path);
                            CompilationUnit cu = new JavaParser().parse(src).getResult().orElseThrow(() ->
                                    new CodeParserException("Parsing failed"));

                            cu.findAll(MethodDeclaration.class).forEach(m -> m.getRange().ifPresent(r -> {
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
                                        .buggy(false)  // will be overwritten in MantiMetrics
                                        .build();
                                out.add(md);
                            }));

                            logger.debug("File {}: parsed {} methods", rel, cu.findAll(MethodDeclaration.class).size());
                        } catch (IOException | ParseProblemException | CodeParserException ex) {
                            logger.warn("Skipping {} due to {}", rel, ex.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new CodeParserException("Directory walk failed", e);
        }

        return out;
    }
}