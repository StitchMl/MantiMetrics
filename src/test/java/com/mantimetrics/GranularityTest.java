package com.mantimetrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GranularityTest {

    @Test
    void parsesSupportedGranularitiesAndAliases() {
        assertEquals(Granularity.METHOD, Granularity.fromCli("method"));
        assertEquals(Granularity.METHOD, Granularity.fromCli("M"));
        assertEquals(Granularity.CLASS, Granularity.fromCli("class"));
        assertEquals(Granularity.CLASS, Granularity.fromCli(" c "));
    }

    @Test
    void parsesExecutionOptionsIncludingBoth() {
        assertEquals(GranularityOption.CLASS, GranularityOption.fromCli("class"));
        assertEquals(GranularityOption.METHOD, GranularityOption.fromCli("method"));
        assertEquals(GranularityOption.BOTH, GranularityOption.fromCli(" both "));
        assertEquals(List.of(Granularity.CLASS, Granularity.METHOD), GranularityOption.BOTH.granularities());
    }

    @Test
    void rejectsUnsupportedGranularity() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Granularity.fromCli("field"));

        assertTrue(ex.getMessage().contains("method|class"));
    }

    @Test
    void rejectsUnsupportedExecutionOption() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> GranularityOption.fromCli("field"));

        assertTrue(ex.getMessage().contains("method|class|both"));
    }
}
