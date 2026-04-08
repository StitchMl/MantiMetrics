package com.mantimetrics.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipExtractionUtilsTest {

    @Test
    void materializesOnlyProductionJavaSources() {
        assertTrue(ZipExtractionUtils.shouldMaterialize("repo/src/main/java/com/acme/App.java", false));
        assertTrue(ZipExtractionUtils.shouldMaterialize("repo/module-info.java", false));

        assertFalse(ZipExtractionUtils.shouldMaterialize("repo/README.md", false));
        assertFalse(ZipExtractionUtils.shouldMaterialize("repo/src/test/java/com/acme/AppTest.java", false));
        assertFalse(ZipExtractionUtils.shouldMaterialize("repo/src/main/java/com/acme/AppTest.java", false));
        assertFalse(ZipExtractionUtils.shouldMaterialize("repo/docs/", true));
    }
}
