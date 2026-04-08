package com.mantimetrics.cli;

import com.mantimetrics.analysis.Granularity;
import java.util.List;

public enum GranularityOption {
    CLASS(List.of(Granularity.CLASS)),
    METHOD(List.of(Granularity.METHOD)),
    BOTH(List.of(Granularity.CLASS, Granularity.METHOD));

    private final List<Granularity> granularities;

    GranularityOption(List<Granularity> granularities) {
        this.granularities = List.copyOf(granularities);
    }

    public List<Granularity> granularities() {
        return granularities;
    }

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
