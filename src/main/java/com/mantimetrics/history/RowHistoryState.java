package com.mantimetrics.history;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Cumulative, release-to-release historical state for a dataset entity.
 */
public record RowHistoryState(
        int totalTouches,
        int totalIssueTouches,
        int totalChurn,
        List<String> authors,
        int ageInReleases
) {
    public RowHistoryState {
        authors = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(authors, "authors")));
    }

    public int totalAuthors() {
        return authors.size();
    }
}
