package com.mantimetrics.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.transport.TagOpt;


import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.nio.file.Path;
import org.eclipse.jgit.revwalk.RevWalk;

public final class LocalRepoCache {
    private final Git git; // JGit handle
    public LocalRepoCache(String url, Path workDir) throws GitAPIException {
        // shallow clone, blob-less, solo 1 branch
        this.git = Git.cloneRepository()
                .setURI(url)
                .setDirectory(workDir.toFile())
                .setBare(true)
                .call();
        git.fetch().setTagOpt(TagOpt.FETCH_TAGS).call();
    }

    /** File modificati in un commit (solo *.java) */
    public Set<String> filesOf(RevCommit c) throws IOException {
        // root-commit o commit senza parent in shallow clone
        if (c.getParentCount() == 0) {
            // Lista tutti i file del tree del commit
            try (TreeWalk tw = new TreeWalk(git.getRepository())) {
                tw.addTree(c.getTree());
                tw.setRecursive(true);
                Set<String> paths = new HashSet<>();
                while (tw.next()) {
                    String p = tw.getPathString();
                    if (p.endsWith(".java")) paths.add(p);
                }
                return paths;
            }
        }

        // caso normale: diff col primo parent
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(git.getRepository());
            return df.scan(c.getParent(0), c).stream()
                    .map(DiffEntry::getNewPath)
                    .filter(p -> p.endsWith(".java"))
                    .collect(Collectors.toSet());
        }
    }

    /** Contenuto di un file in un commit */
    public RevCommit lookup(String sha) throws IOException {
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            ObjectId id = git.getRepository().resolve(sha);
            return walk.parseCommit(id);
        }
    }
}

