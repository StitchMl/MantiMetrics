package com.mantimetrics.util;

/**
 * Single-line ASCII progress bar that updates in place using a carriage-return trick.
 *
 * <p>Output is written directly to {@code System.out} (not via any logging framework) so that
 * it does not interleave with SLF4J/Log4j2 output, which is routed to {@code System.err}.
 *
 * <p>Typical usage:
 * <pre>{@code
 * try (ProgressBar bar = new ProgressBar("Git history", 42)) {
 *     for (String tag : tags) {
 *         bar.step(tag);   // advances counter, re-renders the bar in place
 *     }
 * }  // auto-calls finish() → appends final newline
 * }</pre>
 */
public final class ProgressBar implements AutoCloseable {

    private static final int BAR_WIDTH   = 28;
    private static final int LABEL_WIDTH = 22;
    private static final int LINE_WIDTH  = 100;
    private static final String FILL     = "█";
    private static final String EMPTY    = "░";

    private final String label;
    private final int    total;
    private int          current = 0;
    private boolean      finished = false;

    /**
     * Creates and immediately renders an empty progress bar.
     *
     * @param label short description shown left of the bar (max ~22 chars)
     * @param total total number of steps
     */
    public ProgressBar(String label, int total) {
        this.label = label;
        this.total = Math.max(1, total);
        render("");
    }

    /**
     * Advances the bar by one step and re-renders with the given detail string.
     *
     * @param detail short string shown to the right of the counter (e.g. current item name)
     */
    public void step(String detail) {
        if (finished) return;
        current = Math.min(current + 1, total);
        render(detail);
    }

    /** Advances the bar by one step without a detail string. */
    public void step() {
        step("");
    }

    /**
     * Marks the bar as complete: fills it to 100 % and appends a newline so the
     * next output starts on a fresh line.
     */
    public void finish() {
        if (finished) return;
        finished = true;
        current = total;
        render("");
        System.out.println();
        System.out.flush();
    }

    /** Delegates to {@link #finish()} so the bar can be used in try-with-resources. */
    @Override
    public void close() {
        finish();
    }

    // ── private ──────────────────────────────────────────────────────────────

    private void render(String detail) {
        int filled = (int) Math.round((double) current / total * BAR_WIDTH);
        int empty  = BAR_WIDTH - filled;
        int pct    = (int) Math.round((double) current / total * 100);

        String detailPart = "";
        if (detail != null && !detail.isEmpty()) {
            String d = detail.length() > 36 ? detail.substring(0, 33) + "…" : detail;
            detailPart = "  " + d;
        }

        String bar = String.format(
                "  %-" + LABEL_WIDTH + "s [%s%s] %3d%% (%d/%d)%s",
                label,
                FILL.repeat(filled),
                EMPTY.repeat(empty),
                pct, current, total,
                detailPart);

        // Pad to LINE_WIDTH to overwrite leftover chars from a previously longer line.
        if (bar.length() < LINE_WIDTH) {
            bar = String.format("%-" + LINE_WIDTH + "s", bar);
        }

        System.out.print("\r" + bar);
        System.out.flush();
    }
}
