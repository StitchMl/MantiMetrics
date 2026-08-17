package com.mantimetrics.javaparsing;

import java.util.List;
import java.util.Set;

/**
 * Result of scanning a source tree for eligible production Java files.
 *
 * @param id stable identifier of the scanned source set
 * @param totalJavaFiles total Java files found before filtering
 * @param includedFiles files retained for analysis
 */
public record ScanResult(
        String id,
        long totalJavaFiles,
        List<ParsedFileRappresentation> includedFiles
) {
    /**
     * Returns a filtered scan result containing only the requested relative paths.
     *
     * @param relativePaths relative paths to keep
     * @return filtered scan result
     */
    @SuppressWarnings("unused")
    public ScanResult filterTo(Set<String> relativePaths) {
        List<ParsedFileRappresentation> filtered = includedFiles.stream()
                .filter(source -> relativePaths.contains(source.relativePath()))
                .toList();
        return new ScanResult(id + "#filtered", filtered.size(), filtered);
    }
}
