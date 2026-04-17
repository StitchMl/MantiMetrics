package com.mantimetrics.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseStart;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Providers;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Parses Java source strings into JavaParser compilation units.
 */
final class JavaCompilationUnitLoader {
    private static final Logger LOG = LoggerFactory.getLogger(JavaCompilationUnitLoader.class);
    private final JavaParser parser = new JavaParser(new ParserConfiguration());

    /**
     * Parses one Java source file, logging failures instead of throwing parsing exceptions.
     *
     * @param source raw Java source
     * @param sourceId source identifier used in logs
     * @param logPrefix log prefix identifying the caller
     * @return parsed compilation unit when parsing succeeds
     */
    Optional<CompilationUnit> parse(String source, String sourceId, String logPrefix) {
        try {
            return parser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(source)).getResult();
        } catch (ParseProblemException exception) {
            LOG.warn("[{}] Failed to parse {}: {}", logPrefix, sourceId, exception.getMessage());
        }
        return Optional.empty();
    }
}
