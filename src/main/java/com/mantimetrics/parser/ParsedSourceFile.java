package com.mantimetrics.parser;

import java.util.List;

public record ParsedSourceFile(
        String relativePath,
        String source,
        List<String> jiraKeys
) {
}
