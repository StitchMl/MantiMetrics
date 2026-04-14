package com.mantimetrics.history;

import java.util.HashMap;
import java.util.Map;

/**
 * Small mutable store used while walking the release timeline in chronological order.
 */
public final class RowHistoryStore {
    private final Map<String, RowHistoryState> states = new HashMap<>();

    public RowHistoryState get(String uniqueKey) {
        return states.get(uniqueKey);
    }

    public void put(String uniqueKey, RowHistoryState state) {
        states.put(uniqueKey, state);
    }
}
