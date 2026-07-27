package com.mantimetrics.orchestrator;

import com.mantimetrics.git.GitReleaseSnapshot;

/**
 * Immutable release checkpoint used both for dataset generation and for historical labeling.
 *
 * @param tag current release tag
 * @param previousTag immediately preceding release tag, or {@code null} for the first release
 * @param commitData commit-range metadata associated with the release
 */
public record ReleaseSnapshot(
        String tag,
        String previousTag,
        GitReleaseSnapshot commitData
) {
}
