package com.mantimetrics.parser;

import java.util.List;
import java.util.Set;

/**
 * Result of scanning a source tree for eligible production Java files.
 *
 * @param id stable identifier of the scanned source set
 * @param totalJavaFiles total Java files found before filtering
 * @param includedFiles files retained for analysis
 */
public record SourceScanResult(
        String id,
        long totalJavaFiles,
        List<ParsedSourceFile> includedFiles
) {
    /**
     * Returns a filtered scan result containing only the requested relative paths.
     *
     * @param relativePaths relative paths to keep
     * @return filtered scan result
     */
    @SuppressWarnings("unused")
    public SourceScanResult filterTo(Set<String> relativePaths) {
        List<ParsedSourceFile> filtered = includedFiles.stream()
                .filter(source -> relativePaths.contains(source.relativePath()))
                .toList();
        return new SourceScanResult(id + "#filtered", filtered.size(), filtered);
    }
}
