package com.mantimetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MantiMetrics {
    private static final Logger LOG = LoggerFactory.getLogger(MantiMetrics.class);
    private static final String USAGE = """
            Uso:
              --granularity=class|method|both
              --repo-url=<https://github.com/org/repo.git> --jira-key=<KEY> [--percentage=33]
            Note:
              se --granularity manca, il default e' class
              se --repo-url e' presente, il default di --percentage e' 33
              se --repo-url manca, la CLI chiede quale progetto analizzare
            """;

    private MantiMetrics() {
        throw new AssertionError("Do not instantiate MantiMetrics");
    }

    public static void main(String[] args) throws Exception {
        try {
            CliOptions cliOptions = new CliOptionsParser().parse(args);
            new ApplicationBootstrap().run(cliOptions);
            LOG.info("Done! All temporary files cleaned up.");
        } catch (IllegalArgumentException exception) {
            LOG.error("Errore: {}", exception.getMessage());
            LOG.error(USAGE);
        }
    }
}
