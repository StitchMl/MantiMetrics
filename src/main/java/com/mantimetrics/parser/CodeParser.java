package com.mantimetrics.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mantimetrics.git.GitService;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.MethodData;

import java.util.ArrayList;
import java.util.List;

public class CodeParser {

    private final GitService gh;

    public CodeParser(GitService gh) {
        this.gh = gh;
    }



    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String ref,
            MetricsCalculator calc) throws Exception {

        String branch = gh.getDefaultBranch(owner, repo);
        String commitId = gh.getLatestCommitSha(owner, repo);
        List<MethodData> out = new ArrayList<>();
        List<String> paths = gh.listJavaFiles(owner, repo, ref);

        System.out.println("Found " + paths.size() + " Java files in repo " + repo);
        for (String path : paths) {
            try {
                // 1) extract JIRA keys from the commits to the file
                List<String> issueKeys = gh.getIssueKeysForFile(owner, repo, path);
                // System.out.println("Found " + issueKeys.size() + " JIRA keys for a file " + path);

                // 2) download and parse the source
                String src = gh.fetchFileContent(owner, repo, ref, path);
                CompilationUnit cu = new JavaParser().parse(src).getResult().orElseThrow();
                // System.out.println("Parsed file " + path + " successfully.");

                // 3) for each method, create MethodData including issueKeys
                // System.out.println("Computing metrics for file " + path + "...");
                for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
                    if (m.getRange().isEmpty()) continue;
                    var mets = calc.computeAll(m);
                    var md = new MethodData(
                            repo,
                            "/" + path + "/",
                            m.getDeclarationAsString(true, true, true),
                            branch,
                            ref,             // ref come identificatore di versione
                            commitId,
                            mets,
                            issueKeys    // <— here we pass the JIRA keys
                    );
                    out.add(md);
                }
            } catch (Exception e) {
                System.err.println("ERROR parsing remote file " + path + ": " + e.getMessage());
            }
            //System.out.println("Parsed " + out.size() + " methods from repo " + repo);
        }
        System.out.println("Parsed " + out.size() + " methods from repo " + repo);
        return out;
    }
}