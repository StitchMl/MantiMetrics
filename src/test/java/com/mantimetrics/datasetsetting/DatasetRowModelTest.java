package com.mantimetrics.datasetsetting;

import com.mantimetrics.feature.ClassMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for the lean class-level dataset row model.
 */
class DatasetRowModelTest {

    private DatasetClassData.Builder baseBuilder() {
        return new DatasetClassData.Builder()
                .projectName("proj")
                .path("/src/A.java/")
                .className("A")
                .releaseId("1")
                .metrics(new ClassMetrics(120, 15, 3))
                .commitHashes(List.of("abc"))
                .codeSmells(2)
                .startLine(1)
                .endLine(120);
    }

    @Test
    void buildsClassRowWithMetrics() {
        DatasetClassData row = baseBuilder().build();
        assertEquals("A", row.getClassName());
        assertEquals(120, row.getMetrics().getLoc());
        assertEquals(15, row.getMetrics().getWmc());
        assertEquals(3, row.getMetrics().getLcom());
        assertEquals(2, row.getNSmells());
        assertNotNull(row.toCsvLine());
    }

    @Test
    void toBuilderPreservesIdentity() {
        DatasetClassData original = baseBuilder().build();
        DatasetClassData rebuilt = original.toBuilder().build();
        assertEquals(original.getUniqueKey(), rebuilt.getUniqueKey());
        assertEquals(original.toCsvLine(), rebuilt.toCsvLine());
    }
}
