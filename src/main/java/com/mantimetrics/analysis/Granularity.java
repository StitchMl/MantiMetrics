package com.mantimetrics.analysis;

public enum Granularity {
    METHOD, CLASS;

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
