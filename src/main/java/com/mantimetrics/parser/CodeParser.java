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
     * Downloads, analyzes and constructs the MethodData for each Java method
     * to the specified reference (tag/branch/sha).
     *
     * @throws CodeParserException in case of I/O or parsing errors
     */
    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String ref,
            MetricsCalculator calc) throws CodeParserException {

        String branch;
        String commitId;
        try {
            branch   = gh.getDefaultBranch(owner, repo);
            commitId = gh.getLatestCommitSha(owner, repo);
        } catch (Exception e) {
            throw new CodeParserException("Branch/commit recovery error for " + owner + "/" + repo + "@" + ref, e);
        }

        List<MethodData> out = new ArrayList<>();
        List<String> paths;
        try {
            paths = gh.listJavaFiles(owner, repo, ref);
        } catch (Exception e) {
            throw new CodeParserException("ListJavaFiles error for " + owner + "/" + repo + "@" + ref, e);
        }

        logger.debug("Found {} Java files in{}/{}@{}", paths.size(), owner, repo, ref);

        for (String path : paths) {
            try {
                // 1) retrieve JIRA keys
                List<String> issueKeys = gh.getIssueKeysForFile(owner, repo, path);

                // 2) download and parse source
                String src = gh.fetchFileContent(owner, repo, ref, path);
                CompilationUnit cu = new JavaParser().parse(src)
                        .getResult()
                        .orElseThrow(() ->
                                new CodeParserException("Parsing AST fallito per " + path));

                // 3) for each method, building MethodData
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
                                .buggy(false)
                                .build();
                        out.add(md);
                    });
                }
            } catch (CodeParserException e) {
                // propagate our own
                throw e;
            } catch (Exception e) {
                throw new CodeParserException("Remote file parsing error " + path + ": " + e.getMessage(), e);
                // I ignore a single file and continue
            }
        }

        logger.debug("Total methods analysed in {}/{}: {}", owner, repo, out.size());
        return out;
    }
}