package com.mantimetrics.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mantimetrics.git.GitService;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.MethodData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CodeParser {

    private static final Logger logger = LoggerFactory.getLogger(CodeParser.class);

    private final GitService gh;

    public CodeParser(GitService gh) {
        this.gh = gh;
    }

    /**
     * Scarica, analizza e costruisce i MethodData per ogni metodo Java
     * alla reference (tag/branch/sha) specificata.
     */
    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String ref,
            MetricsCalculator calc) throws Exception {

        String branch   = gh.getDefaultBranch(owner, repo);
        String commitId = gh.getLatestCommitSha(owner, repo);
        List<MethodData> out = new ArrayList<>();

        List<String> paths = gh.listJavaFiles(owner, repo, ref);
        logger.debug("Trovati {} file Java in {}/{}@{}", paths.size(), owner, repo, ref);

        for (String path : paths) {
            try {
                // 1) retrieve JIRA keys from commits touching this file
                List<String> issueKeys = gh.getIssueKeysForFile(owner, repo, path);

                // 2) downloading and parsing the source
                String src = gh.fetchFileContent(owner, repo, ref, path);
                CompilationUnit cu = new JavaParser().parse(src).getResult().orElseThrow();

                // 3) for each method, compute metrics and create MethodData with the Builder
                for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
                    m.getRange().ifPresent(r -> {
                        var mets = calc.computeAll(m);
                        MethodData md = new MethodData.Builder()
                                .projectName(repo)
                                .path("/" + path + "/")
                                .methodSignature(m.getDeclarationAsString(true, true, true))
                                .releaseId(branch)
                                .versionId(ref)
                                .commitId(commitId)
                                .metrics(mets)
                                .commitHashes(issueKeys)
                                // JiraClient will set up buggy later
                                .buggy(false)
                                .build();
                        out.add(md);
                    });
                }
            } catch (Exception e) {
                logger.error("Errore parsing remoto file {}: {}", path, e.getMessage(), e);
                // continue with the next file
            }
        }

        logger.debug("Total methods analysed in {}/{}: {}", owner, repo, out.size());
        return out;
    }
}