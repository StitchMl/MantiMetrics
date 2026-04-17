package com.mantimetrics.git;

import java.util.List;
import java.util.Map;

/**
 * Aggregated commit metadata for one release range grouped by relative source path.
 *
 * @param touchMap commit SHAs touching each file
 * @param issueTouchMap commit SHAs touching each file with a Jira issue key
 * @param fileToIssueKeys Jira issue keys associated with each file
 * @param authorMap authors touching each file
 * @param additionsMap added lines per file
 * @param deletionsMap deleted lines per file
 */
public record ReleaseCommitData(
        Map<String, List<String>> touchMap,
        Map<String, List<String>> issueTouchMap,
        Map<String, List<String>> fileToIssueKeys,
        Map<String, List<String>> authorMap,
        Map<String, Integer> additionsMap,
        Map<String, Integer> deletionsMap
) {
    /**
     * Returns the commit SHAs touching a file in the current release range.
     *
     * @param relativePath normalized relative source path
     * @return touching commit SHAs
     */
    public List<String> touchesFor(String relativePath) {
        return touchMap.getOrDefault(relativePath, List.of());
    }

    /**
     * Returns the issue-linked commit SHAs touching a file in the current release range.
     *
     * @param relativePath normalized relative source path
     * @return issue-linked touching commit SHAs
     */
    public List<String> issueTouchesFor(String relativePath) {
        return issueTouchMap.getOrDefault(relativePath, List.of());
    }

    /**
     * Returns the Jira issue keys associated with a file in the current release range.
     *
     * @param relativePath normalized relative source path
     * @return associated issue keys
     */
    @SuppressWarnings("unused")
    public List<String> issueKeysFor(String relativePath) {
        return fileToIssueKeys.getOrDefault(relativePath, List.of());
    }

    /**
     * Returns the authors touching a file in the current release range.
     *
     * @param relativePath normalized relative source path
     * @return touching authors
     */
    public List<String> authorsFor(String relativePath) {
        return authorMap.getOrDefault(relativePath, List.of());
    }

    /**
     * Returns the added lines accumulated for a file in the current release range.
     *
     * @param relativePath normalized relative source path
     * @return added lines
     */
    public int additionsFor(String relativePath) {
        return additionsMap.getOrDefault(relativePath, 0);
    }

    /**
     * Returns the deleted lines accumulated for a file in the current release range.
     *
     * @param relativePath normalized relative source path
     * @return deleted lines
     */
    public int deletionsFor(String relativePath) {
        return deletionsMap.getOrDefault(relativePath, 0);
    }

    /**
     * Returns the total churn accumulated for a file in the current release range.
     *
     * @param relativePath normalized relative source path
     * @return added plus deleted lines
     */
    public int churnFor(String relativePath) {
        return additionsFor(relativePath) + deletionsFor(relativePath);
    }
}
