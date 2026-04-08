package com.mantimetrics.cli;

import com.mantimetrics.git.ProjectConfig;

public final class CliOptionsParser {
    private static final int DEFAULT_CLI_PERCENTAGE = 33;

    public CliOptions parse(String[] args) {
        String granularityRaw = null;
        String repoUrl = null;
        String jiraKey = null;
        Integer percentage = null;

        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            if (arg.startsWith("--granularity=")) {
                granularityRaw = arg.substring("--granularity=".length());
                continue;
            }
            if (arg.equals("--granularity") || arg.equals("-g")) {
                granularityRaw = nextValue(args, ++index, arg);
                continue;
            }
            if (arg.startsWith("--repo-url=")) {
                repoUrl = arg.substring("--repo-url=".length());
                continue;
            }
            if (arg.equals("--repo-url") || arg.equals("-r")) {
                repoUrl = nextValue(args, ++index, arg);
                continue;
            }
            if (arg.startsWith("--jira-key=")) {
                jiraKey = arg.substring("--jira-key=".length());
                continue;
            }
            if (arg.equals("--jira-key") || arg.equals("-j")) {
                jiraKey = nextValue(args, ++index, arg);
                continue;
            }
            if (arg.startsWith("--percentage=")) {
                percentage = parsePercentage(arg.substring("--percentage=".length()));
                continue;
            }
            if (arg.equals("--percentage") || arg.equals("-p")) {
                percentage = parsePercentage(nextValue(args, ++index, arg));
                continue;
            }
            throw new IllegalArgumentException("Argomento non riconosciuto: " + arg);
        }

        return new CliOptions(
                granularityRaw == null ? GranularityOption.CLASS : GranularityOption.fromCli(granularityRaw),
                buildCliProject(repoUrl, jiraKey, percentage)
        );
    }

    private ProjectConfig buildCliProject(String repoUrl, String jiraKey, Integer percentage) {
        if (repoUrl == null || repoUrl.isBlank()) {
            rejectOptionWithoutRepoUrl(jiraKey, "--jira-key");
            rejectOptionWithoutRepoUrl(percentage, "--percentage");
            return null;
        }
        if (jiraKey == null || jiraKey.isBlank()) {
            throw new IllegalArgumentException("Quando usi --repo-url devi specificare anche --jira-key");
        }
        return new ProjectConfig(
                null,
                null,
                repoUrl,
                percentage != null ? percentage : DEFAULT_CLI_PERCENTAGE,
                jiraKey
        );
    }

    private void rejectOptionWithoutRepoUrl(Object value, String optionName) {
        if (value != null) {
            throw new IllegalArgumentException(optionName + " richiede anche --repo-url");
        }
    }

    private String nextValue(String[] args, int index, String optionName) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Manca il valore dopo " + optionName);
        }
        return args[index];
    }

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
