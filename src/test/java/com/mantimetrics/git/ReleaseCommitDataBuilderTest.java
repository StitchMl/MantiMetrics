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
                        "Alice",
                        Set.of(new ReleaseCommitDataBuilder.ReleaseCommitFile("src/main/java/com/acme/Sample.java", 10, 2)))));

        ReleaseCommitData secondRelease = ReleaseCommitDataBuilder.aggregate(List.of(
                new ReleaseCommitDataBuilder.ReleaseCommitSnapshot(
                        "sha-2",
                        "PROJ-2 Fix parser edge case",
                        "Bob",
                        Set.of(new ReleaseCommitDataBuilder.ReleaseCommitFile("src/main/java/com/acme/Sample.java", 3, 1)))));

        assertEquals(
                List.of("PROJ-1"),
                firstRelease.fileToIssueKeys().get("src/main/java/com/acme/Sample.java"));
        assertEquals(
                List.of("PROJ-2"),
                secondRelease.fileToIssueKeys().get("src/main/java/com/acme/Sample.java"));
        assertFalse(firstRelease.fileToIssueKeys()
                .get("src/main/java/com/acme/Sample.java")
                .contains("PROJ-2"));
        assertEquals(List.of("Alice"), firstRelease.authorMap().get("src/main/java/com/acme/Sample.java"));
        assertEquals(10, firstRelease.additionsFor("src/main/java/com/acme/Sample.java"));
        assertEquals(2, firstRelease.deletionsFor("src/main/java/com/acme/Sample.java"));
    }

    @Test
    void aggregateTracksTouchesEvenWithoutIssueKeys() {
        ReleaseCommitData data = ReleaseCommitDataBuilder.aggregate(List.of(
                new ReleaseCommitDataBuilder.ReleaseCommitSnapshot(
                        "sha-1",
                        "Refactor parser internals",
                        "Alice",
                        Set.of(
                                new ReleaseCommitDataBuilder.ReleaseCommitFile("src/main/java/com/acme/Sample.java", 4, 4),
                                new ReleaseCommitDataBuilder.ReleaseCommitFile("README.md", 1, 0)
                        ))));

        assertEquals(
                List.of("sha-1"),
                data.touchMap().get("src/main/java/com/acme/Sample.java"));
        assertFalse(data.fileToIssueKeys().containsKey("src/main/java/com/acme/Sample.java"));
        assertEquals(List.of("Alice"), data.authorMap().get("src/main/java/com/acme/Sample.java"));
        assertEquals(8, data.churnFor("src/main/java/com/acme/Sample.java"));
    }
}
