package com.mantimetrics.git;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReleaseCommitDataBuilderTest {

    @Test
    void aggregateKeepsBugLabelsScopedToTheCurrentReleaseRange() {
        ReleaseCommitData firstRelease = ReleaseCommitDataBuilder.aggregate(List.of(
                new ReleaseCommitDataBuilder.ReleaseCommitSnapshot(
                        "sha-1",
                        "PROJ-1 Fix parser regression",
                        Set.of("src/main/java/com/acme/Sample.java"))));

        ReleaseCommitData secondRelease = ReleaseCommitDataBuilder.aggregate(List.of(
                new ReleaseCommitDataBuilder.ReleaseCommitSnapshot(
                        "sha-2",
                        "PROJ-2 Fix parser edge case",
                        Set.of("src/main/java/com/acme/Sample.java"))));

        assertEquals(
                List.of("PROJ-1"),
                firstRelease.fileToIssueKeys().get("src/main/java/com/acme/Sample.java"));
        assertEquals(
                List.of("PROJ-2"),
                secondRelease.fileToIssueKeys().get("src/main/java/com/acme/Sample.java"));
        assertFalse(firstRelease.fileToIssueKeys()
                .get("src/main/java/com/acme/Sample.java")
                .contains("PROJ-2"));
    }

    @Test
    void aggregateTracksTouchesEvenWithoutIssueKeys() {
        ReleaseCommitData data = ReleaseCommitDataBuilder.aggregate(List.of(
                new ReleaseCommitDataBuilder.ReleaseCommitSnapshot(
                        "sha-1",
                        "Refactor parser internals",
                        Set.of("src/main/java/com/acme/Sample.java", "README.md"))));

        assertEquals(
                List.of("sha-1"),
                data.touchMap().get("src/main/java/com/acme/Sample.java"));
        assertFalse(data.fileToIssueKeys().containsKey("src/main/java/com/acme/Sample.java"));
    }
}
