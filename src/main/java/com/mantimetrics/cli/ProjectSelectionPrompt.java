package com.mantimetrics.cli;

import com.mantimetrics.git.ProjectConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Interactive CLI prompt used when the project is not fully specified through command-line options.
 */
public final class ProjectSelectionPrompt {
    private static final int DEFAULT_PERCENTAGE = 33;

    private final BufferedReader input;
    private final PrintStream output;

    /**
     * Creates a prompt backed by the provided streams.
     *
     * @param input stream used to read user answers
     * @param output stream used to print questions and validation messages
     */
    public ProjectSelectionPrompt(InputStream input, PrintStream output) {
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.output = output;
    }

    /**
     * Asks the user to choose one configured project or enter a custom repository.
     *
     * @param configuredProjects projects loaded from configuration
     * @return the selected or newly entered project configuration
     * @throws IOException when the CLI input stream closes unexpectedly
     */
    public ProjectConfig prompt(ProjectConfig[] configuredProjects) throws IOException {
        if (configuredProjects == null || configuredProjects.length == 0) {
            output.println("Nessun progetto configurato trovato. Inserisci un repository GitHub da analizzare.");
            return promptCustomProject();
        }

        while (true) {
            printMenu(configuredProjects);
            String rawChoice = readRequiredValue("Seleziona un progetto");
            int choice = parseChoice(rawChoice, configuredProjects.length + 1);
            if (choice >= 1 && choice <= configuredProjects.length) {
                return configuredProjects[choice - 1];
            }
            if (choice == configuredProjects.length + 1) {
                return promptCustomProject();
            }
            output.printf("Scelta non valida: %s%n", rawChoice);
        }
    }

    /**
     * Prints the project menu including the custom-repository option.
     *
     * @param configuredProjects projects available for selection
     */
    private void printMenu(ProjectConfig[] configuredProjects) {
        output.println("Scegli il progetto da analizzare:");
        for (int index = 0; index < configuredProjects.length; index++) {
            ProjectConfig config = configuredProjects[index];
            output.printf(
                    "%d. %s/%s (JIRA=%s, release=%d%%)%n",
                    index + 1,
                    config.owner(),
                    config.name(),
                    config.jiraProjectKey(),
                    config.percentage()
            );
        }
        output.printf("%d. Inserisci un repository GitHub custom%n", configuredProjects.length + 1);
    }

    /**
     * Collects the data required to analyze a custom GitHub repository.
     *
     * @return project configuration built from the values entered by the user
     * @throws IOException when the CLI input stream closes unexpectedly
     */
    private ProjectConfig promptCustomProject() throws IOException {
        String repoUrl = readRequiredValue("Repository GitHub URL");
        String jiraKey = readRequiredValue("JIRA key");
        String percentageRaw = readOptionalValue("Percentage release da analizzare [default 33]");
        Integer percentage = percentageRaw.isBlank() ? DEFAULT_PERCENTAGE : parsePercentage(percentageRaw);
        return new ProjectConfig(null, null, repoUrl, percentage, jiraKey);
    }

    /**
     * Parses a numeric menu choice and returns {@code -1} for invalid values.
     *
     * @param rawChoice raw user input
     * @param maxValue highest accepted menu item number
     * @return parsed selection or {@code -1} when the value is invalid
     */
    private int parseChoice(String rawChoice, int maxValue) {
        try {
            int parsed = Integer.parseInt(rawChoice.trim());
            if (parsed < 1 || parsed > maxValue) {
                return -1;
            }
            return parsed;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /**
     * Parses and validates the release percentage entered interactively.
     *
     * @param raw raw percentage input
     * @return validated percentage
     * @throws IllegalArgumentException when the value is not numeric or out of range
     */
    private int parsePercentage(String raw) {
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 0 || parsed > 100) {
                throw new IllegalArgumentException("La percentage deve essere compresa tra 0 e 100");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Valore non valido per la percentage: " + raw, exception);
        }
    }

    /**
     * Reads a non-blank input value, repeating the prompt until one is provided.
     *
     * @param label human-readable field label
     * @return trimmed non-blank value
     * @throws IOException when the input stream closes unexpectedly
     */
    private String readRequiredValue(String label) throws IOException {
        while (true) {
            String value = readOptionalValue(label);
            if (!value.isBlank()) {
                return value;
            }
            output.printf("%s non puo' essere vuoto.%n", label);
        }
    }

    /**
     * Reads an optional value from the CLI and trims surrounding whitespace.
     *
     * @param label human-readable field label
     * @return trimmed value entered by the user, possibly blank
     * @throws IOException when the input stream closes unexpectedly
     */
    private String readOptionalValue(String label) throws IOException {
        output.printf("%s: ", label);
        String line = input.readLine();
        if (line == null) {
            throw new IOException("Input CLI terminato inaspettatamente");
        }
        return line.trim();
    }
}
