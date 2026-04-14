package com.mantimetrics.labeling;

import com.mantimetrics.jira.JiraClient;

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

    public ReleaseTimeline(List<String> orderedTags) {
        this.orderedTags = List.copyOf(Objects.requireNonNull(orderedTags, "orderedTags"));
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < this.orderedTags.size(); index++) {
            indexes.putIfAbsent(JiraClient.normalize(this.orderedTags.get(index)), index);
        }
        this.indexByNormalizedTag = Map.copyOf(indexes);
    }

    public List<String> orderedTags() {
        return orderedTags;
    }

    public int size() {
        return orderedTags.size();
    }

    @SuppressWarnings("unused")
    public String firstTag() {
        if (orderedTags.isEmpty()) {
            throw new IllegalStateException("Release timeline is empty");
        }
        return orderedTags.get(0);
    }

    public OptionalInt findIndex(String tagOrVersion) {
        if (tagOrVersion == null || tagOrVersion.isBlank()) {
            return OptionalInt.empty();
        }
        Integer index = indexByNormalizedTag.get(JiraClient.normalize(tagOrVersion));
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }
}
