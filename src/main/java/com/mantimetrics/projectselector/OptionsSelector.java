package com.mantimetrics.projectselector;

import com.mantimetrics.git.GitConfig;
import com.mantimetrics.labeling.Proportion;

/**
 * Immutable command-line configuration resolved before bootstrapping the application.
 *
 * @param cliProject project explicitly passed through the CLI, or {@code null} when the interactive
 *                   project selection must be used
 * @param useGithubIssues whether GitHub Issues are unioned with Jira tickets (flag --github-issues)
 * @param proportionVariant Proportion variant to estimate injected versions (flag --proportion)
 * @param excludeChurnZero whether to drop rows with zero churn (flag --exclude-churn-zero)
 */
public record OptionsSelector(GitConfig cliProject, boolean useGithubIssues, Proportion.Variant proportionVariant, boolean excludeChurnZero) {

    /**
     * Reports whether the user selected a project directly from the command line.
     *
     * @return {@code true} when a CLI project is available, {@code false} otherwise
     */
    public boolean hasCliProject() {
        return cliProject != null;
    }
}
