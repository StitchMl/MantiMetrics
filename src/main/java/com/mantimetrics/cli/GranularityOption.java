package com.mantimetrics.cli;

import com.mantimetrics.analysis.Granularity;
import java.util.List;

/**
 * High-level CLI granularity choices exposed to the user.
 */
public enum GranularityOption {
    CLASS(List.of(Granularity.CLASS)),
    METHOD(List.of(Granularity.METHOD)),
    BOTH(List.of(Granularity.CLASS, Granularity.METHOD));

    private final List<Granularity> granularities;

    /**
     * Creates an option backed by an immutable list of internal granularities.
     *
     * @param granularities concrete granularities enabled by the CLI option
     */
    GranularityOption(List<Granularity> granularities) {
        this.granularities = List.copyOf(granularities);
    }

    /**
     * Returns the internal granularities enabled by this CLI selection.
     *
     * @return immutable list of granularities to execute
     */
    public List<Granularity> granularities() {
        return granularities;
    }

    /**
     * Parses a CLI token into the corresponding option.
     *
     * @param raw raw value provided by the user
     * @return matching granularity option
     * @throws IllegalArgumentException when the value is missing or unsupported
     */
    public static GranularityOption fromCli(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("granularity is null");
        }
        return switch (raw.trim().toLowerCase()) {
            case "class", "c" -> CLASS;
            case "method", "m" -> METHOD;
            case "both", "b" -> BOTH;
            default -> throw new IllegalArgumentException(
                    "Valore non valido per --granularity: " + raw + " (usa method|class|both)"
            );
        };
    }
}
