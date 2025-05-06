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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class CodeParser {
    private static final Logger logger = LoggerFactory.getLogger(CodeParser.class);
    private final GitService gh;

    public CodeParser(GitService gh) {
        this.gh = gh;
    }

    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String ref,
            MetricsCalculator calc) throws CodeParserException {

        logger.info("Local analysis of {}/{}@{}", owner, repo, ref);

        // 1) download and unpack all sources
        final Path repoRoot;
        try {
            repoRoot = gh.downloadAndUnzipRepo(owner, repo, ref);
        } catch (IOException e) {
            throw new CodeParserException("Error downloading zip of " + repo + "@" + ref, e);
        }

        // 2) retrieve branch and commit
        final String branch;
        final String commitId;
        try {
            branch   = gh.getDefaultBranch(owner, repo);
            commitId = gh.getLatestCommitSha(owner, repo);
        } catch (IOException e) {
            throw new CodeParserException("Branch/commit error for " + repo + "@" + ref, e);
        }

        List<MethodData> out = new ArrayList<>();

        // 3) walk the dir for .java
        try (Stream<Path> files = Files.walk(repoRoot)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        String rel = repoRoot.relativize(path).toString();
                        try {
                            // 3.1) read the file
                            String src = Files.readString(path);
                            // 3.2) Analyses AST
                            CompilationUnit cu = new JavaParser().parse(src).getResult().orElseThrow(() ->
                                    new ParseProblemException(List.of()));
                            // 3.3) extract methods
                            for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
                                m.getRange().ifPresent(r -> {
                                    var mets = calc.computeAll(m);
                                    MethodData md = new MethodData.Builder()
                                            .projectName(repo)
                                            .path("/" + rel + "/")
                                            .methodSignature(m.getDeclarationAsString(true,true,true))
                                            .releaseId(branch)
                                            .versionId(ref)
                                            .commitId(commitId)
                                            .metrics(mets)
                                            .commitHashes(Collections.emptyList())  // da popolare dopo
                                            .buggy(false)
                                            .build();
                                    out.add(md);
                                });
                            }
                            logger.debug("Analysed {} methods in {}", cu.findAll(MethodDeclaration.class).size(), rel);

                        } catch (IOException|ParseProblemException ex) {
                            logger.warn("Skip file {} for error: {}", rel, ex.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new CodeParserException("Unpacked dir walk failed", e);
        }

        logger.info("Total methods analysed in {}/{}@{}: {}", owner, repo, ref, out.size());
        return out;
    }
}