package com.mantimetrics.gitIssue;

/**
 * Configuration for the GitHub Issues integration (flag {@code use_github_issues}).
 * GitHub Issues are used as a complementary bug source to Jira for the ablation comparison.
 */
public final class GitIssueConfig {
    /** Only closed issues are considered resolved bugs. */
    public static final String STATE = "closed";
    /** Issues must carry this label (GitHub's conventional bug label) to count as defects. */
    public static final String BUG_LABEL = "bug";
    /** GitHub REST API base URL. */
    public static final String API_BASE = "https://api.github.com";

    private GitIssueConfig() {
        throw new AssertionError("Do not instantiate GitIssueConfig");
    }
}
