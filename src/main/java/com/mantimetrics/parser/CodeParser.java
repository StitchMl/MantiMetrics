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

    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String ref,
            MetricsCalculator calc
    ) throws CodeParserException {

        logger.info("Analysing {}/{}@{}", owner, repo, ref);

        // 1) download+extract the entire repo in ZIP
        Path repoRoot;
        try {
            repoRoot = gh.downloadAndUnzipRepo(owner, repo, ref);
        } catch (IOException e) {
            throw new CodeParserException("ZIP download error for " + repo+"@"+ref, e);
        }

        // 2) retrieve branch and SHA
        String branch;
        String commitId;
        try {
            branch   = gh.getDefaultBranch(owner, repo);
            commitId = gh.getLatestCommitSha(owner, repo);
        } catch (IOException e) {
            throw new CodeParserException("Branch/commit error for "+repo+"@"+ref, e);
        }

        // 3) **Pre-calculate** the map file→JIRA-keys in one go
        Map<String,List<String>> fileToKeys;
        try {
            fileToKeys = gh.getFileToIssueKeysMap(owner, repo);
        } catch (IOException e) {
            logger.warn("Cannot build file→issueKey map: {}", e.getMessage());
            fileToKeys = Collections.emptyMap();
        }

        List<MethodData> out = new ArrayList<>();

        // 4) search all .java
        try (Stream<Path> files = Files.walk(repoRoot)) {
            Map<String, List<String>> finalFileToKeys = fileToKeys;
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        String rel = repoRoot.relativize(path).toString().replace('\\','/');
                        try {
                            String src = Files.readString(path);
                            CompilationUnit cu = new JavaParser().parse(src).getResult().orElseThrow();

                            // ✅ determines whether the file is 'buggy'.
                            boolean isBuggy = !finalFileToKeys.getOrDefault(rel, List.of()).isEmpty();

                            // extract methods
                            for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
                                m.getRange().ifPresent(r -> {
                                    var mets = calc.computeAll(m);
                                    MethodData md = new MethodData.Builder()
                                            .projectName(repo)
                                            .path("/"+rel+"/")
                                            .methodSignature(m.getDeclarationAsString(true,true,true))
                                            .releaseId(branch)
                                            .versionId(ref)
                                            .commitId(commitId)
                                            .metrics(mets)
                                            .commitHashes(Collections.emptyList())
                                            .buggy(isBuggy)
                                            .build();
                                    out.add(md);
                                });
                            }
                            logger.debug("File {}: {} metodi", rel, cu.findAll(MethodDeclaration.class).size());
                        } catch (IOException|ParseProblemException ex) {
                            logger.warn("Skip {} due to {}", rel, ex.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new CodeParserException("Directory walk failed", e);
        }

        logger.info("Analysed {} methods in {}/{}@{}", out.size(), owner, repo, ref);
        return out;
    }
}