package com.mantimetrics.javaparsing;

import java.util.List;

/**
 * Immutable representation of one source file selected for analysis.
 *
 * @param relativePath normalized relative source path
 * @param source raw file contents
 * @param jiraKeys Jira issue keys associated with the file in the current release
 */
public record ParsedFileRappresentation(
        String relativePath,
        String source,
        List<String> jiraKeys
) {
}
