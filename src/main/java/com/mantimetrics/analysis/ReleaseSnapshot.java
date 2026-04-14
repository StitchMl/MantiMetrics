package com.mantimetrics.analysis;

import com.mantimetrics.git.ReleaseCommitData;

/**
 * Immutable release checkpoint used both for dataset generation and for historical labeling.
 */
public record ReleaseSnapshot(
        String tag,
        String previousTag,
        ReleaseCommitData commitData
) {
}
