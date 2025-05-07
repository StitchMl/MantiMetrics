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

public final class CodeParser {

    private static final Logger LOG = LoggerFactory.getLogger(CodeParser.class);
    private final GitService git;

    public CodeParser(GitService git) {
        this.git = git;
    }

    /**
     * @param fileToKeys immutable map file → list&lt;JIRA-keys&gt;
     */
    public List<MethodData> parseAndComputeOnline(String owner,
                                                  String repo,
                                                  String tag,
                                                  MetricsCalculator calc,
                                                  Map<String,List<String>> fileToKeys)
            throws CodeParserException {

        LOG.trace("Analysing {}/{}@{}", owner, repo, tag);

        /* 1) download – now uses the 4-arg overload so every release
               is unpacked in its own deterministic subdirectory       */
        Path root;
        try {
            root = git.downloadAndUnzipRepo(owner, repo, tag, "release-" + tag);
        } catch (IOException io) {
            throw new CodeParserException("Download/Unzip failed for " + repo + '@' + tag, io);
        }

        List<MethodData> out = new ArrayList<>();

        /* 2) stream all .java files – Files.walk is still the most memory-efficient
              way to traverse big trees and is recommended by Oracle docs */
        try (Stream<Path> files = Files.walk(root)) {

            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        // path relative to repository root (same form used by compare API)
                        String rel = toUnixPath(root.relativize(path));

                        // JIRA keys for that file (maybe empty)
                        List<String> issueKeys = fileToKeys.getOrDefault(rel, List.of());

                     /* 3) parse and extract methods – JavaParser’s
                           CompilationUnit#findAll is O(nodes) and very fast :contentReference[oaicite:1]{index=1} */
                        try {
                            var cuOpt = new JavaParser().parse(Files.readString(path)).getResult();
                            if (cuOpt.isEmpty()) return;

                            cuOpt.get().findAll(MethodDeclaration.class).forEach(m -> m.getRange().ifPresent(r -> {
                                var metrics = calc.computeAll(m);

                                out.add(new MethodData.Builder()
                                        .projectName(repo)
                                        .path("/" + rel + '/')
                                        .methodSignature(m.getDeclarationAsString(true,true,true))
                                        .releaseId(tag)
                                        .versionId(tag)
                                        .commitId(tag)
                                        .metrics(metrics)
                                        .commitHashes(issueKeys)
                                        .buggy(false)
                                        .build());
                            }));
                        } catch (IOException | ParseProblemException ex) {
                            LOG.warn("Skipping {} – {}", rel, ex.getMessage());
                        }
                    });

        } catch (IOException io) {
            throw new CodeParserException("I/O walking " + root, io);
        } finally {
            /* 4) always try to delete the temp directory;
                  Files.walk + reverseOrder avoids the “directory not empty” trap
                  and is the idiom recommended in recent articles */
            try (Stream<Path> w = Files.walk(root)) {
                w.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException e) {
                                LOG.warn("Failed to delete {}: {}", p, e.getMessage());
                            }
                        });
                LOG.trace("Deleted temp dir {}", root);
            } catch (IOException ignore) {
                LOG.warn("Failed to delete temp dir {}", root);
            }
        }

        return out;
    }

    /* ------------------------------------------------- helpers -------- */

    /** Ensures the path is stored with `/` separators, as GitHub compare API returns. */
    private static String toUnixPath(Path p) {
        return p.toString().replace('\\', '/');
    }
}