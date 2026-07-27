package com.mantimetrics.labeling;

/**
 * Tecnica Proportion per stimare la Injected Version.
 * Varianti previste (flag proportion_variant): TOTAL, INCREMENTAL.
 * P = (FV - IV) / (FV - OV) sui ticket con IV certa.
 * TODO Fase 2: implementare calcolo di P per variante.
 */
public final class Proportion {
    /** Varianti supportate della tecnica Proportion. */
    public enum Variant { TOTAL, INCREMENTAL }

    private Proportion() {
        // TODO Fase 2
    }
}
