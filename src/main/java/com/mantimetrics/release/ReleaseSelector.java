package com.mantimetrics.release;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Select an initial percentage of tags (versions) from an ordered list of tags.
 */
public class ReleaseSelector {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseSelector.class);

    /**
     * Returns the first percentage of elements from the list.
     *
     * @param tags    List of tags, ordered from oldest to newest.
     * @param percent Percentage of elements to be selected (integer between 0 and 100).
     * @return Sublist containing the first floor(tags.size() * percent / 100.0) elements,
     *         or at least 1 if percent > 0, and the list is not empty.
     */
    public List<String> selectFirstPercent(List<String> tags, int percent) {
        logger.debug("Selecting first {}% of {} tags.", percent, tags.size());

        if (tags.isEmpty() || percent <= 0) {
            logger.debug("No tags to select (empty list or non‐positive percentage).");
            return Collections.emptyList();
        }
        if (percent >= 100) {
            logger.debug("Percentage >= 100, returning all tags.");
            return List.copyOf(tags);
        }

        int total = tags.size();
        int count = (int) Math.floor(total * percent / 100.0);
        // ensure at least one element if percent > 0
        count = Math.max(count, 1);
        List<String> selected = tags.subList(0, Math.min(count, total));
        logger.debug("Selected {} tags out of {}.", selected.size(), total);
        return selected;
    }

    // prevent instantiation
    private ReleaseSelector() { }
}