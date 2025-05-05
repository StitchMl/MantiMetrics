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
import java.util.concurrent.*;

public class CodeParser {

    private static final Logger logger = LoggerFactory.getLogger(CodeParser.class);
    private static final int MAX_RETRIES = 3;
    private static final long TIMEOUT_SECONDS = 30;
    private final GitService gh;
    private final ExecutorService executor;

    public CodeParser(GitService gh) {
        this.gh = gh;
        this.executor = Executors.newCachedThreadPool();
    }

    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String ref,
            MetricsCalculator calc) throws CodeParserException, InterruptedException {

        String branch;
        String commitId;
        try {
            branch = gh.getDefaultBranch(owner, repo);
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

        logger.debug("Found {} Java files in {}/{}@{}", paths.size(), owner, repo, ref);

        for (String path : paths) {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            processFile(path, owner, repo, ref, branch, commitId, calc, out);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }, executor);

                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    break; // Success, exit retry loop
                    
                } catch (TimeoutException e) {
                    if (attempt == MAX_RETRIES) {
                        throw new CodeParserException("Failed to process file " + path + " after " + MAX_RETRIES + " attempts", e);
                    }
                    logger.warn("Attempt {} failed for file {}, retrying...", attempt, path);
                    Thread.sleep(1000 * attempt); // Exponential backoff
                } catch (Exception e) {
                    throw new CodeParserException("Error processing file " + path, e);
                }
            }
        }

        logger.debug("Total methods analysed in {}/{}: {}", owner, repo, out.size());
        return out;
    }

    private void processFile(String path, String owner, String repo, String ref, 
                           String branch, String commitId, MetricsCalculator calc, 
                           List<MethodData> out) throws Exception {
        List<String> issueKeys = gh.getIssueKeysForFile(owner, repo, path);
        String src = gh.fetchFileContent(owner, repo, ref, path);
        
        CompilationUnit cu = new JavaParser().parse(src)
                .getResult()
                .orElseThrow(() -> new CodeParserException("Failed to parse AST for " + path));

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
                synchronized (out) {
                    out.add(md);
                }
            });
        }
    }
}