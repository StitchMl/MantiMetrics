package com.mantimetrics.release;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Select an initial percentage of tags (versions) from a list,
 * always starting from the earliest semantic version.
 */
public class ReleaseSelector {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseSelector.class);

    /**
     * Returns the first percentage of elements from the list,
     * ordered by semantic version (oldest → newest).
     *
     * @param tags    List of tags (e.g. "4.0.0", "release-4.1.2", "v4.2.0"), in qualsiasi ordine.
     * @param percent Percentage of elements to be selected (integer between 0 and 100).
     * @return Sublist containing the first floor(tags.size() * percent / 100.0) elements
     *         (at least 1 if percent>0), or the full list if percent≥100, or empty list otherwise.
     */
    public List<String> selectFirstPercent(List<String> tags, int percent) {
        logger.debug("Selecting first {}% of {} total tags", percent, tags.size());

        // Validation of the percentage parameter
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be between 0 and 100");
        }

        if (tags.isEmpty() || percent == 0) {
            logger.trace("No tags selected (empty list or percent = {})", percent);
            return Collections.emptyList();
        }

        // 1) semantically ascending clone and order
        List<String> sorted = new ArrayList<>(tags);
        sorted.sort(ReleaseSelector::compareSemver);
        logger.trace("Tags sorted (oldest→newest): {}", sorted);

        // 2) calculation of how many to take
        int total = sorted.size();
        int count = (percent == 100)
                ? total
                : Math.max(1, (int) Math.floor(total * percent / 100.0));

        logger.debug("Selecting first {}% → {} of {} tags", percent, count, total);

        List<String> selected = sorted.subList(0, count);
        logger.trace("Selected first {} out of {} tags", selected.size(), total);
        return new ArrayList<>(selected);
    }

    /**
     * Comparator semantic version: extracts numbers from 'X.Y.Z' (ignores non-numeric prefixes)
     * and compares them numeric piece by piece.
     */
    private static int compareSemver(String a, String b) {
        String cleanA = a.replaceAll("[^0-9.]", "");
        String cleanB = b.replaceAll("[^0-9.]", "");
        String[] partsA = cleanA.split("\\.");
        String[] partsB = cleanB.split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int va = i < partsA.length && !partsA[i].isEmpty() ? Integer.parseInt(partsA[i]) : 0;
            int vb = i < partsB.length && !partsB[i].isEmpty() ? Integer.parseInt(partsB[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        // if they are exactly equal numerically, fallback to string comparison
        return a.compareTo(b);
    }
}