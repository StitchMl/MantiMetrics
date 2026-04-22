package com.mantimetrics.labeling;

import com.mantimetrics.jira.JiraClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Chronological release timeline shared by snoring, ticket-window resolution and dataset generation.
 */
public final class ReleaseTimeline {
    private final List<String> orderedTags;
    private final Map<String, Integer> indexByNormalizedTag;
    private final Map<String, Instant> tagDates;

    /**
     * Creates a normalized release timeline preserving the original chronological order.
     *
     * @param orderedTags chronologically ordered release tags
     */
    public ReleaseTimeline(List<String> orderedTags) {
        this(orderedTags, Map.of());
    }

    /**
     * Creates an enriched release timeline with per-tag commit dates for Proportion-based IV prediction.
     *
     * @param orderedTags chronologically ordered release tags
     * @param tagDates commit date keyed by raw tag name; may be empty when dates are unavailable
     */
    public ReleaseTimeline(List<String> orderedTags, Map<String, Instant> tagDates) {
        this.orderedTags = List.copyOf(Objects.requireNonNull(orderedTags, "orderedTags"));
        this.tagDates = Map.copyOf(Objects.requireNonNull(tagDates, "tagDates"));
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < this.orderedTags.size(); index++) {
            indexes.putIfAbsent(JiraClient.normalize(this.orderedTags.get(index)), index);
        }
        this.indexByNormalizedTag = Map.copyOf(indexes);
    }

    /**
     * Returns the chronological release tags.
     *
     * @return immutable ordered tag list
     */
    public List<String> orderedTags() {
        return orderedTags;
    }

    /**
     * Returns the number of releases in the timeline.
     *
     * @return release count
     */
    public int size() {
        return orderedTags.size();
    }

    /**
     * Returns the first release tag in the timeline.
     *
     * @return oldest release tag
     * @throws IllegalStateException when the timeline is empty
     */
    @SuppressWarnings("unused")
    public String firstTag() {
        if (orderedTags.isEmpty()) {
            throw new IllegalStateException("Release timeline is empty");
        }
        return orderedTags.get(0);
    }

    /**
     * Finds the chronological index of a tag or Jira version after normalization.
     *
     * @param tagOrVersion tag or Jira version identifier to resolve
     * @return optional release index
     */
    public OptionalInt findIndex(String tagOrVersion) {
        if (tagOrVersion == null || tagOrVersion.isBlank()) {
            return OptionalInt.empty();
        }
        Integer index = indexByNormalizedTag.get(JiraClient.normalize(tagOrVersion));
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }

    /**
     * Finds the index of the most recent release whose tag date is on or before the given instant.
     * Used to determine the Opening Version (OV) for the Proportion injected-version algorithm.
     * Returns 0 when no tag dates are available or no tag precedes the instant.
     *
     * @param createdDate ticket creation date
     * @return opening version index
     */
    public int findOpeningVersionIndex(Instant createdDate) {
        Objects.requireNonNull(createdDate, "createdDate");
        if (tagDates.isEmpty()) {
            return 0;
        }
        int result = 0;
        for (int i = 0; i < orderedTags.size(); i++) {
            Instant tagDate = tagDates.get(orderedTags.get(i));
            if (tagDate != null && !tagDate.isAfter(createdDate)) {
                result = i;
            } else if (tagDate != null) {
                break;  // tags are chronologically sorted; no later match possible
            }
        }
        return result;
    }
}
