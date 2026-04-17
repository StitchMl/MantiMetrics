package com.mantimetrics.analysis;

/**
 * Dataset granularities supported by the analysis pipeline.
 */
public enum Granularity {
    METHOD, CLASS;

    /**
     * Parses a CLI token into the matching internal granularity.
     *
     * @param raw raw granularity value provided by the user
     * @return matching granularity
     * @throws IllegalArgumentException when the value is missing or unsupported
     */
    public static Granularity fromCli(String raw) {
        if (raw == null) throw new IllegalArgumentException("granularity is null");
        return switch (raw.trim().toLowerCase()) {
            case "method", "m" -> METHOD;
            case "class", "c" -> CLASS;
            default -> throw new IllegalArgumentException(
                    "Valore non valido per --granularity: " + raw + " (usa method|class)"
            );
        };
    }
}
