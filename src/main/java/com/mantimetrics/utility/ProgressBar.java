package com.mantimetrics.utility;

import org.jetbrains.annotations.NotNull;

/**
 * Single-line ASCII progress bar that updates in place using a carriage-return trick.
 *
 * <p>All output is written to {@code System.out} while being protected by {@link #CONSOLE_LOCK}.
 * The companion {@link ProgressBarLogAppender} acquires the same lock before printing any log line,
 * so log messages and progress-bar updates are always serialized and never interleave on the
 * terminal — even when Log4j2 fires a WARN from a rate-limit retry mid-update.
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
@SuppressWarnings("java:S106")
public final class ProgressBar implements AutoCloseable {

    // ── shared console lock ───────────────────────────────────────────────────
    /** Shared monitor used by both ProgressBar and {@link ProgressBarLogAppender}. */
    static final Object CONSOLE_LOCK = new Object();

    /** The currently active (not yet finished) progress bar, or {@code null}. */
    private static volatile ProgressBar active = null;

    // ── layout constants ──────────────────────────────────────────────────────
    private static final int    BAR_WIDTH   = 28;
    private static final int    LABEL_WIDTH = 22;
    private static final int    LINE_WIDTH  = 100;
    private static final String FILL        = "█";
    private static final String EMPTY       = "░";

    // ── instance state ────────────────────────────────────────────────────────
    private final String label;
    private final int    total;
    private int          current    = 0;
    private boolean      finished   = false;
    private String       lastDetail = "";

    /**
     * Creates and immediately renders an empty progress bar.
     *
     * @param label short description shown left of the bar (max ~22 chars)
     * @param total total number of steps
     */
    public ProgressBar(String label, int total) {
        this.label = label;
        this.total = Math.max(1, total);
        synchronized (CONSOLE_LOCK) {
            active = this;
            doRender("");
        }
    }

    /**
     * Advances the bar by one step and re-renders with the given detail string.
     *
     * @param detail short string shown to the right of the counter (e.g. current item name)
     */
    public void step(String detail) {
        synchronized (CONSOLE_LOCK) {
            if (finished) return;
            current = Math.min(current + 1, total);
            lastDetail = detail;
            doRender(detail);
        }
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
        synchronized (CONSOLE_LOCK) {
            if (finished) return;
            finished  = true;
            active    = null;   // unregister before final render
            current   = total;
            doRender("");
            System.out.println();
            System.out.flush();
        }
    }

    /** Delegates to {@link #finish()} so the bar can be used in try-with-resources. */
    @Override
    public void close() {
        finish();
    }

    // ── package-private helpers used by ProgressBarLogAppender ──────────────────

    /**
     * Erases the active bar's line so a log message can be printed cleanly.
     * <strong>Must be called while holding {@link #CONSOLE_LOCK}.</strong>
     */
    static void clearLine() {
        if (active != null) {
            String blank = " ".repeat(LINE_WIDTH + 2);
            System.out.print("\r" + blank + "\r");
            System.out.flush();
        }
    }

    /**
     * Redraws the active bar after a log message has been printed.
     * <strong>Must be called while holding {@link #CONSOLE_LOCK}.</strong>
     */
    static void redraw() {
        ProgressBar bar = active;
        if (bar != null) {
            bar.doRender(bar.lastDetail);
        }
    }

    // ── private ──────────────────────────────────────────────────────────────

    /**
     * Low-level render — writes the current bar state to {@code System.out}.
     * Caller is responsible for holding {@link #CONSOLE_LOCK}.
     */
    private void doRender(String detail) {
        int filled = (int) Math.round((double) current / total * BAR_WIDTH);
        int empty  = BAR_WIDTH - filled;
        String bar = buildBarString(detail, filled, empty);

        // Pad to LINE_WIDTH to overwrite leftover chars from a previously longer line.
        if (bar.length() < LINE_WIDTH) {
            bar = String.format("%-" + LINE_WIDTH + "s", bar);
        }

        System.out.print("\r" + bar);
        System.out.flush();
    }

    @NotNull
    private String buildBarString(String detail, int filled, int empty) {
        int pct = (int) Math.round((double) current / total * 100);

        String detailPart = "";
        if (detail != null && !detail.isEmpty()) {
            String d = detail.length() > 36 ? detail.substring(0, 33) + "…" : detail;
            detailPart = "  " + d;
        }

        return String.format(
                "  %-" + LABEL_WIDTH + "s [%s%s] %3d%% (%d/%d)%s",
                label,
                FILL.repeat(filled),
                EMPTY.repeat(empty),
                pct, current, total,
                detailPart);
    }
}
