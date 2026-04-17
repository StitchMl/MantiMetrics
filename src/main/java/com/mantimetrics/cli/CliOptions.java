package com.mantimetrics.cli;

import com.mantimetrics.git.ProjectConfig;

/**
 * Immutable command-line configuration resolved before bootstrapping the application.
 *
 * @param granularityOption requested analysis granularity selection
 * @param cliProject project explicitly passed through the CLI, or {@code null} when the interactive
 *                   project selection must be used
 */
public record CliOptions(GranularityOption granularityOption, ProjectConfig cliProject) {

    /**
     * Reports whether the user selected a project directly from the command line.
     *
     * @return {@code true} when a CLI project is available, {@code false} otherwise
     */
    public boolean hasCliProject() {
        return cliProject != null;
    }
}
