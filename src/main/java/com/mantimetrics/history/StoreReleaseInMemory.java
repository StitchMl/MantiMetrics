package com.mantimetrics.history;

import java.util.HashMap;
import java.util.Map;

/**
 * Small mutable store used while walking the release timeline in chronological order.
 */
public final class StoreReleaseInMemory {
    private final Map<String, ComulationMetricsCalculator> states = new HashMap<>();

    /**
     * Returns the historical state associated with a dataset row key.
     *
     * @param uniqueKey stable dataset row identifier
     * @return stored history state, or {@code null} when the entity was not seen before
     */
    public ComulationMetricsCalculator get(String uniqueKey) {
        return states.get(uniqueKey);
    }

    /**
     * Stores or replaces the historical state associated with a dataset row key.
     *
     * @param uniqueKey stable dataset row identifier
     * @param state history state to store
     */
    public void put(String uniqueKey, ComulationMetricsCalculator state) {
        states.put(uniqueKey, state);
    }
}
