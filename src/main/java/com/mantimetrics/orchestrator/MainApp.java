package com.mantimetrics.orchestrator;

import com.mantimetrics.orchestrator.StartAnalysis;
import com.mantimetrics.projectSelector.OptionsSelector;
import com.mantimetrics.projectSelector.CliParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the command-line application.
 * It parses the user arguments, bootstraps the application services and reports
 * usage errors without exposing stack traces to the console.
 */
public final class MainApp {
    private static final Logger LOG = LoggerFactory.getLogger(MainApp.class);
    private static final String USAGE = """
            Uso:
              --granularity=class|method|both
              --repo-url=<https://github.com/org/repo.git> --jira-key=<KEY> [--percentage=33]
            Note:
              se --granularity manca, il default e' class
              se --repo-url e' presente, il default di --percentage e' 33
              se --repo-url manca, la CLI chiede quale progetto analizzare
            """;

    /**
     * Prevents instantiation of the static entry-point holder.
     */
    private MainApp() {
        throw new AssertionError("Do not instantiate MainApp");
    }

    /**
     * Starts the application with the provided command-line arguments.
     *
     * @param args raw command-line arguments received from the JVM
     * @throws Exception when the bootstrap pipeline fails unexpectedly
     */
    public static void main(String[] args) throws Exception {
        try {
            OptionsSelector cliOptions = new CliParser().parse(args);
            new StartAnalysis().run(cliOptions);
            LOG.info("Done! All temporary files cleaned up.");
        } catch (IllegalArgumentException exception) {
            LOG.error("Errore: {}", exception.getMessage());
            LOG.error(USAGE);
        }
    }
}
