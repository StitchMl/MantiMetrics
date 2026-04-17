package com.mantimetrics.cli;

import com.mantimetrics.analysis.Granularity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CLI granularity parsing.
 */
class GranularityTest {

    /**
     * Verifies that the internal granularity parser accepts supported names and aliases.
     */
    @Test
    void parsesSupportedGranularitiesAndAliases() {
        assertEquals(Granularity.METHOD, Granularity.fromCli("method"));
        assertEquals(Granularity.METHOD, Granularity.fromCli("M"));
        assertEquals(Granularity.CLASS, Granularity.fromCli("class"));
        assertEquals(Granularity.CLASS, Granularity.fromCli(" c "));
    }

    /**
     * Verifies that the CLI execution options include the combined class-and-method mode.
     */
    @Test
    void parsesExecutionOptionsIncludingBoth() {
        assertEquals(GranularityOption.CLASS, GranularityOption.fromCli("class"));
        assertEquals(GranularityOption.METHOD, GranularityOption.fromCli("method"));
        assertEquals(GranularityOption.BOTH, GranularityOption.fromCli(" both "));
        assertEquals(List.of(Granularity.CLASS, Granularity.METHOD), GranularityOption.BOTH.granularities());
    }

    /**
     * Verifies that unsupported internal granularities are rejected.
     */
    @Test
    void rejectsUnsupportedGranularity() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Granularity.fromCli("field"));

        assertTrue(ex.getMessage().contains("method|class"));
    }

    /**
     * Verifies that unsupported CLI execution options are rejected.
     */
    @Test
    void rejectsUnsupportedExecutionOption() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> GranularityOption.fromCli("field"));

        assertTrue(ex.getMessage().contains("method|class|both"));
    }
}
