package com.mantimetrics.labeling;

import com.mantimetrics.analysis.ReleaseSnapshot;
import com.mantimetrics.git.ReleaseCommitData;
import com.mantimetrics.jira.JiraBugTicket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HistoricalBugLabelIndexBuilder}.
 */
class HistoricalBugLabelIndexBuilderTest {

    /**
     * Verifies that affected versions are preferred when they precede the fixing release.
     */
    @Test
    void usesAffectedVersionsWhenTheyAreConsistentWithTheFixRelease() {
        ReleaseTimeline timeline = new ReleaseTimeline(List.of("v1", "v2", "v3"));
        HistoricalBugLabelIndex index = new HistoricalBugLabelIndexBuilder().build(
                timeline,
                List.of("v1", "v2"),
                List.of(new JiraBugTicket("PROJ-1", Instant.parse("2020-01-01T00:00:00Z"), List.of("v2"))),
                List.of(
                        snapshot("v1", null, Map.of()),
                        snapshot("v2", "v1", Map.of()),
                        snapshot("v3", "v2", Map.of("src/main/java/com/acme/Sample.java", List.of("PROJ-1")))
                )
        );

        assertFalse(index.isBuggy("v1", "src/main/java/com/acme/Sample.java"));
        assertTrue(index.isBuggy("v2", "src/main/java/com/acme/Sample.java"));
        assertTrue(index.summary().ticketsUsingAffectedVersions() > 0);
    }

    /**
     * Verifies that the builder falls back to the Proportion strategy when affected versions are absent.
     * With no tag dates in the timeline, OV defaults to 0 and P defaults to 1.0, giving IV = OV = 0,
     * so the behaviour is identical to the former Total strategy.
     */
    @Test
    void fallsBackToProportionWhenAffectedVersionsAreMissing() {
        ReleaseTimeline timeline = new ReleaseTimeline(List.of("v1", "v2", "v3"));
        HistoricalBugLabelIndex index = new HistoricalBugLabelIndexBuilder().build(
                timeline,
                List.of("v1", "v2"),
                List.of(new JiraBugTicket("PROJ-2", Instant.parse("2020-01-01T00:00:00Z"), List.of())),
                List.of(
                        snapshot("v1", null, Map.of()),
                        snapshot("v2", "v1", Map.of()),
                        snapshot("v3", "v2", Map.of("src/main/java/com/acme/Sample.java", List.of("PROJ-2")))
                )
        );

        assertTrue(index.isBuggy("v1", "src/main/java/com/acme/Sample.java"));
        assertTrue(index.isBuggy("v2", "src/main/java/com/acme/Sample.java"));
        assertTrue(index.summary().ticketsUsingTotalFallback() > 0);
    }

    /**
     * Verifies that the Proportion algorithm predicts the injected version correctly
     * when one ticket trains P and a second ticket uses the predicted IV.
     *
     * <p>Timeline: v1 (2020-01-01) → v2 (2020-07-01) → v3 (2021-01-01)
     * <p>Trainer ticket PROJ-T: created 2020-01-15, fix at v3 (index 2), IV at v1 (index 0),
     *   OV = v1 (index 0). P = (2 - 0) / (2 - 0) = 1.0.
     * <p>Fallback ticket PROJ-F: created 2020-07-15, no affected versions, fix at v3 (index 2),
     *   OV = v2 (index 1). Predicted IV = round(2 - (2 - 1) * 1.0) = 1 → v2.
     *   So the file should be buggy at v2 but not v1.
     */
    @Test
    void proportionPredictsBuggyRangeUsingCalibratedP() {
        Map<String, Instant> tagDates = Map.of(
                "v1", Instant.parse("2020-01-01T00:00:00Z"),
                "v2", Instant.parse("2020-07-01T00:00:00Z"),
                "v3", Instant.parse("2021-01-01T00:00:00Z")
        );
        ReleaseTimeline timeline = new ReleaseTimeline(List.of("v1", "v2", "v3"), tagDates);

        List<JiraBugTicket> tickets = List.of(
                new JiraBugTicket("PROJ-T", Instant.parse("2020-01-15T00:00:00Z"), List.of("v1")),
                new JiraBugTicket("PROJ-F", Instant.parse("2020-07-15T00:00:00Z"), List.of())
        );

        String trainerFile = "src/Trainer.java";
        String fallbackFile = "src/Fallback.java";

        HistoricalBugLabelIndex index = new HistoricalBugLabelIndexBuilder().build(
                timeline,
                List.of("v1", "v2"),
                tickets,
                List.of(
                        snapshot("v1", null, Map.of()),
                        snapshot("v2", "v1", Map.of()),
                        snapshot("v3", "v2", Map.of(
                                trainerFile, List.of("PROJ-T"),
                                fallbackFile, List.of("PROJ-F")
                        ))
                )
        );

        // Trainer ticket: IV = v1 (index 0), fix = v3 (index 2) → buggy at v1 and v2
        assertTrue(index.isBuggy("v1", trainerFile));
        assertTrue(index.isBuggy("v2", trainerFile));

        // Fallback ticket: OV = v2 (index 1), P = 1.0, predicted IV = v2 (index 1), fix = v3 (index 2) → buggy only at v2
        assertFalse(index.isBuggy("v1", fallbackFile));
        assertTrue(index.isBuggy("v2", fallbackFile));

        assertTrue(index.summary().ticketsUsingAffectedVersions() > 0);
        assertTrue(index.summary().ticketsUsingTotalFallback() > 0);
    }

    /**
     * Creates a release snapshot tailored for the historical-labeling tests.
     *
     * @param tag release tag
     * @param previousTag previous release tag
     * @param fileToIssueKeys issue keys grouped by file path
     * @return synthetic release snapshot
     */
    private ReleaseSnapshot snapshot(String tag, String previousTag, Map<String, List<String>> fileToIssueKeys) {
        return new ReleaseSnapshot(tag, previousTag, new ReleaseCommitData(
                Map.of(),
                Map.of(),
                fileToIssueKeys,
                Map.of(),
                Map.of(),
                Map.of()
        ));
    }
}
