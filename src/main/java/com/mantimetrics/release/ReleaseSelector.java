package com.mantimetrics.release;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Selects the oldest prefix of releases required by the assignment percentage rule.
 */
public class ReleaseSelector {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseSelector.class);

    /**
     * Selects the first percentage of tags from an already ordered release list.
     *
     * @param orderedTags chronologically ordered release tags
     * @param percent percentage of releases to keep
     * @return mutable list containing the selected oldest releases
     * @throws IllegalArgumentException when {@code percent} is outside the {@code 0..100} range
     */
    public List<String> selectFirstPercent(List<String> orderedTags, int percent) {
        logger.debug("Selecting first {}% of {} total tags", percent, orderedTags.size());

        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be between 0 and 100");
        }

        if (orderedTags.isEmpty() || percent == 0) {
            logger.trace("No tags selected (empty list or percent = {})", percent);
            return Collections.emptyList();
        }

        int total = orderedTags.size();
        int count = percent == 100
                ? total
                : Math.max(1, (int) Math.floor(total * percent / 100.0));

        logger.debug("Selecting first {}% -> {} of {} tags", percent, count, total);
        return new ArrayList<>(orderedTags.subList(0, count));
    }
}
