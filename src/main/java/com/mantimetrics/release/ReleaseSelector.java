package com.mantimetrics.release;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReleaseSelector {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseSelector.class);

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
