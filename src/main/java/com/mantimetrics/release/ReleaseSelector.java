package com.mantimetrics.release;

import java.util.Collections;
import java.util.List;

/**
 * Select an initial percentage of tags (versions) from an ordered list of tags.
 */
public class ReleaseSelector {

    /**
     * Returns the first percentage of elements from the list.
     *
     * @param tags List of tags, ordered from oldest to newest.
     * @param percent Percentage of elements to be selected (integer between 0 and 100).
     * @return Sublist containing the first floor(tags.size() * percent / 100.0) elements,
     *                  or at least 1 if percent > 0, and the list is not empty.
     */
    public List<String> selectFirstPercent(List<String> tags, int percent) {
        System.out.println("DEBUG: Selecting first " + percent + "% of " + tags.size() + " tags.");
        if (tags.isEmpty() || percent <= 0) {
            return Collections.emptyList();
        }
        if (percent >= 100) {
            return List.copyOf(tags);
        }

        int total = tags.size();
        int count = (int) Math.floor(total * percent / 100.0);
        // Let us ensure that we have at least one element if percent > 0
        count = Math.max(count, 1);
        return tags.subList(0, Math.min(count, total));
    }
}
