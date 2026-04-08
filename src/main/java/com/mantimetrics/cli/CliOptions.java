package com.mantimetrics.cli;

import com.mantimetrics.git.ProjectConfig;

public record CliOptions(GranularityOption granularityOption, ProjectConfig cliProject) {

    public boolean hasCliProject() {
        return cliProject != null;
    }
}
