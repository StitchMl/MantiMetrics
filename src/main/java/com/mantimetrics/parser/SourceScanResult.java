package com.mantimetrics.parser;

import java.util.List;
import java.util.Set;

public record SourceScanResult(
        String id,
        long totalJavaFiles,
        List<ParsedSourceFile> includedFiles
) {
    public SourceScanResult filterTo(Set<String> relativePaths) {
        List<ParsedSourceFile> filtered = includedFiles.stream()
                .filter(source -> relativePaths.contains(source.relativePath()))
                .toList();
        return new SourceScanResult(id + "#filtered", filtered.size(), filtered);
    }
}
