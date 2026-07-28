package com.mantimetrics.git;

import java.util.List;

/**
 * Opaque holder of the raw per-release commit snapshots. It lets the aggregation — which depends on
 * the issue-key source (Jira only vs Jira + GitHub) — be recomputed for each dataset variant without
 * re-fetching the commits from GitHub. The orchestrator treats this as an opaque token: it caches it
 * once and passes it back to {@link GitFacade#aggregate(RawReleaseCommits, boolean)} per variant.
 */
public final class RawReleaseCommits {
    final List<GitPrevReleaseBuilder.ReleaseCommitSnapshot> commits;

    RawReleaseCommits(List<GitPrevReleaseBuilder.ReleaseCommitSnapshot> commits) {
        this.commits = List.copyOf(commits);
    }
}
