package com.mantimetrics.labeling;

import com.mantimetrics.analysis.ReleaseSnapshot;
import com.mantimetrics.git.ReleaseCommitData;
import com.mantimetrics.jira.JiraBugTicket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricalBugLabelIndexBuilderTest {

    @Test
    void usesAffectedVersionsWhenTheyAreConsistentWithTheFixRelease() {
        ReleaseTimeline timeline = new ReleaseTimeline(List.of("v1", "v2", "v3"));
        HistoricalBugLabelIndex index = new HistoricalBugLabelIndexBuilder().build(
                timeline,
                List.of("v1", "v2"),
                List.of(new JiraBugTicket("PROJ-1", List.of("v2"))),
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

    @Test
    void fallsBackToTotalHistoryWhenAffectedVersionsAreMissing() {
        ReleaseTimeline timeline = new ReleaseTimeline(List.of("v1", "v2", "v3"));
        HistoricalBugLabelIndex index = new HistoricalBugLabelIndexBuilder().build(
                timeline,
                List.of("v1", "v2"),
                List.of(new JiraBugTicket("PROJ-2", List.of())),
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
