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
import java.net.SocketTimeoutException;
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
     * at the specified reference (tag/branch/sha).
     *
     * @throws CodeParserException only on unrecoverable parsing errors
     */
    public List<MethodData> parseAndComputeOnline(
            String owner,
            String repo,
            String ref,
            MetricsCalculator calc) throws CodeParserException {

        logger.info("Starting analysis of {}/{} @ {}", owner, repo, ref);

        // 1) recupero branch e ultimo commit
        final String branch;
        final String commitId;
        try {
            branch   = gh.getDefaultBranch(owner, repo);
            commitId = gh.getLatestCommitSha(owner, repo);
            logger.debug("Default branch = {}, latest commit = {}", branch, commitId);
        } catch (Exception e) {
            throw new CodeParserException(
                    "Branch/commit recovery error for " + owner + "/" + repo + "@" + ref, e);
        }

        // 2) elenco file .java
        final List<String> paths;
        try {
            paths = gh.listJavaFiles(owner, repo, ref);
            logger.debug("Trovati {} file Java in {}/{}@{}", paths.size(), owner, repo, ref);
        } catch (Exception e) {
            throw new CodeParserException(
                    "ListJavaFiles error for " + owner + "/" + repo + "@" + ref, e);
        }

        List<MethodData> out = new ArrayList<>();

        // 3) per ogni file scarico, parsifico e costruisco i MethodData
        for (String path : paths) {
            try {
                // 3.1) JIRA keys
                List<String> issueKeys = gh.getIssueKeysForFile(owner, repo, path);
                logger.trace("File {}: trovate {} issueKeys", path, issueKeys.size());

                // 3.2) download e parsing
                String src = gh.fetchFileContent(owner, repo, ref, path);
                CompilationUnit cu = new JavaParser().parse(src)
                        .getResult()
                        .orElseThrow(() -> {
                            logger.error("Parsing AST restituisce vuoto per {}", path);
                            return new CodeParserException("AST parsing failed for " + path);
                        });

                // 3.3) estrazione metodi
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
                                .buggy(false)  // verrà impostato in seguito
                                .build();
                        out.add(md);
                    });
                }
                logger.debug("File {}: analizzati {} metodi", path, cu.findAll(MethodDeclaration.class).size());

            } catch (SocketTimeoutException ste) {
                logger.warn("Timeout durante fetch di {}: skip del file", path);

            } catch (IOException ioe) {
                logger.warn("I/O error su '{}': {}; skip del file", path, ioe.getMessage());

            } catch (ParseProblemException ppe) {
                throw new CodeParserException("Parsing AST failed for " + path, ppe);

            } catch (CodeParserException cpe) {
                // Propaga le nostre eccezioni dedicate
                throw cpe;

            } catch (Exception e) {
                throw new CodeParserException("Unexpected error on '" + path + "': " + e.getMessage(), e);
            }
        }

        logger.info("Completata l'analisi di {}/{}@{}, trovati {} metodi",
                owner, repo, ref, out.size());
        return out;
    }
}