package com.mantimetrics.projectselector;

import com.mantimetrics.git.GitConfig;
import com.mantimetrics.labeling.Proportion;

/**
 * Parses the supported command-line options and translates them into {@link OptionsSelector}.
 * It also enforces the dependencies between repository-related options.
 */
public final class CliParser {
    private static final int DEFAULT_CLI_PERCENTAGE = 33;

    /**
     * Parses the raw JVM arguments.
     *
     * @param args raw command-line arguments
     * @return validated CLI options ready for the bootstrap phase
     * @throws IllegalArgumentException when an option is unknown, malformed or incomplete
     */
    public OptionsSelector parse(String[] args) {
        ParseState state = new ParseState();
        int index = 0;
        while (index < args.length) {
            index = consumeArg(args, index, state);
        }
        return new OptionsSelector(
                buildCliProject(state.repoUrl, state.jiraKey, state.percentage, state.sonarKey),
                state.useGithubIssues,
                Proportion.Variant.fromCli(state.proportionRaw),
                state.excludeChurnZero
        );
    }

    /**
     * Consumes one argument (and possibly its following value token) from the array,
     * updating the mutable parse state.
     *
     * @param args  full command-line argument array
     * @param index current position
     * @param state mutable accumulator for parsed values
     * @return next index to process
     */
    private int consumeArg(String[] args, int index, ParseState state) {
        String arg = args[index];

        if (arg.startsWith("--") && arg.contains("=")) {
            consumeInlineArg(arg, state);
            return index + 1;
        }

        return consumeSeparateArg(args, index, state, arg);
    }

    private void consumeInlineArg(String arg, ParseState state) {
        int separator = arg.indexOf('=');
        String option = arg.substring(0, separator);
        String value = arg.substring(separator + 1);

        switch (option) {
            case "--repo-url" -> state.repoUrl = value;
            case "--jira-key" -> state.jiraKey = value;
            case "--sonar-key" -> state.sonarKey = value;
            case "--percentage" -> state.percentage = parsePercentage(value);
            case "--proportion" -> state.proportionRaw = value;
            default -> throw unknownArgument(arg);
        }
    }

    private int consumeSeparateArg(
            String[] args,
            int index,
            ParseState state,
            String arg
    ) {
        switch (arg) {
            case "--repo-url", "-r" -> {
                state.repoUrl = nextValue(args, index + 1, arg);
                return index + 2;
            }
            case "--jira-key", "-j" -> {
                state.jiraKey = nextValue(args, index + 1, arg);
                return index + 2;
            }
            case "--sonar-key", "-s" -> {
                state.sonarKey = nextValue(args, index + 1, arg);
                return index + 2;
            }
            case "--percentage", "-p" -> {
                state.percentage = parsePercentage(
                        nextValue(args, index + 1, arg)
                );
                return index + 2;
            }
            case "--proportion" -> {
                state.proportionRaw = nextValue(args, index + 1, arg);
                return index + 2;
            }
            case "--exclude-churn-zero" -> {
                state.excludeChurnZero = true;
                return index + 1;
            }
            case "--github-issues" -> {
                state.useGithubIssues = true;
                return index + 1;
            }
            default -> throw unknownArgument(arg);
        }
    }

    private IllegalArgumentException unknownArgument(String arg) {
        return new IllegalArgumentException(
                "Argomento non riconosciuto: " + arg
        );
    }

    /** Mutable accumulator used while scanning CLI arguments. */
    private static final class ParseState {
        boolean useGithubIssues;
        boolean excludeChurnZero;
        String proportionRaw;
        String repoUrl;
        String jiraKey;
        String sonarKey;
        Integer percentage;
    }

    /**
     * Builds the project configuration derived from repository-specific command-line options.
     *
     * @param repoUrl repository URL passed on the CLI
     * @param jiraKey JIRA project key associated with the repository
     * @param percentage optional percentage of releases to analyze
     * @return a project configuration when a repository URL is present, otherwise {@code null}
     */
    private GitConfig buildCliProject(String repoUrl, String jiraKey, Integer percentage, String sonarKey) {
        if (repoUrl == null || repoUrl.isBlank()) {
            rejectOptionWithoutRepoUrl(jiraKey, "--jira-key");
            rejectOptionWithoutRepoUrl(percentage, "--percentage");
            return null;
        }
        if (jiraKey == null || jiraKey.isBlank()) {
            throw new IllegalArgumentException("Quando usi --repo-url devi specificare anche --jira-key");
        }
        return new GitConfig(
                null,
                null,
                repoUrl,
                percentage != null ? percentage : DEFAULT_CLI_PERCENTAGE,
                jiraKey,
                (sonarKey != null && !sonarKey.isBlank()) ? sonarKey : null
        );
    }

    /**
     * Rejects options that are only valid when a repository URL is also supplied.
     *
     * @param value option value to validate
     * @param optionName option name used in the validation error
     */
    private void rejectOptionWithoutRepoUrl(Object value, String optionName) {
        if (value != null) {
            throw new IllegalArgumentException(optionName + " richiede anche --repo-url");
        }
    }

    /**
     * Returns the next positional token and fails if the option has no following value.
     *
     * @param args full command-line argument array
     * @param index position expected to contain the option value
     * @param optionName option currently being resolved
     * @return the value token following the option
     */
    private String nextValue(String[] args, int index, String optionName) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Manca il valore dopo " + optionName);
        }
        return args[index];
    }

    /**
     * Parses and validates the release-percentage option.
     *
     * @param raw raw percentage string
     * @return integer percentage in the inclusive {@code 0..100} range
     * @throws IllegalArgumentException when the value is not numeric or out of range
     */
    private Integer parsePercentage(String raw) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 0 || parsed > 100) {
                throw new IllegalArgumentException("La percentage deve essere compresa tra 0 e 100");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Valore non valido per --percentage: " + raw, exception);
        }
    }
}
