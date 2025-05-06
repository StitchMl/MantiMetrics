package com.mantimetrics.parser;

import com.github.javaparser.JavaParser;
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
     * @param fileToKeys pre-computed map: file path (relative) → list of JIRA keys
     */
    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String ref,
            MetricsCalculator calc,
            Map<String,List<String>> fileToKeys
    ) throws CodeParserException, IOException {

        // 1) download+unzip
        Path repoRoot = gh.downloadAndUnzipRepo(owner, repo, ref);

        // 2) branch & SHA
        String branch   = gh.getDefaultBranch(owner, repo);
        String commitId = gh.getLatestCommitSha(owner, repo);

        List<MethodData> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(repoRoot)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        String rel = repoRoot.relativize(path)
                                .toString()
                                .replace('\\','/');
                        boolean isBuggy = !fileToKeys.getOrDefault(rel, List.of()).isEmpty();

                        try {
                            String src = Files.readString(path);
                            CompilationUnit cu = new JavaParser().parse(src).getResult().orElseThrow();
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
                                            .commitHashes(List.of())  // se vuoi, puoi comunque popolarlo
                                            .buggy(isBuggy)
                                            .build();
                                    out.add(md);
                                });
                            }
                        } catch (Exception ex) {
                            logger.warn("Skip {} due to {}", rel, ex.getMessage());
                        }
                    });
        } catch (IOException e) {
            throw new CodeParserException("Directory walk failed", e);
        }

        return out;
    }
}