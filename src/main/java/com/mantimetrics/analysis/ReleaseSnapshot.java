package com.mantimetrics.analysis;

import com.mantimetrics.git.ReleaseCommitData;

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
        ReleaseCommitData commitData
) {
}
