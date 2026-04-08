package com.mantimetrics.release;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseSelectorTest {

    private final ReleaseSelector selector = new ReleaseSelector();

    @Test
    void preservesChronologicalOrderPassedByCaller() {
        List<String> selected = selector.selectFirstPercent(List.of("v0.9", "v1.0", "v2.0"), 67);

        assertEquals(List.of("v0.9", "v1.0"), selected);
    }

    @Test
    void rejectsInvalidPercentages() {
        assertThrows(IllegalArgumentException.class,
                () -> selector.selectFirstPercent(List.of("v1"), -1));
        assertThrows(IllegalArgumentException.class,
                () -> selector.selectFirstPercent(List.of("v1"), 101));
    }
}
