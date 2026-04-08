package com.mantimetrics.parser;

import com.mantimetrics.util.AnalysisPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

final class JavaSourceScanner {
    private static final Logger LOG = LoggerFactory.getLogger(JavaSourceScanner.class);
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    private static final String JAVA_EXT = ".java";

    private final PathMatcher testDirMatcher = FileSystems.getDefault()
            .getPathMatcher("glob:**/src/test/java/**");
    private final PathMatcher testClassMatcher = FileSystems.getDefault()
            .getPathMatcher("glob:**/*{Test,IT}.java");
    private final PathMatcher ignoreMatcher = FileSystems.getDefault()
            .getPathMatcher("glob:**/{target,build,generated-sources}/**");
    private final PathMatcher otherMatcher = FileSystems.getDefault()
            .getPathMatcher("glob:**/{common,utils,examples}/**");
    private final PathMatcher infraMatcher = FileSystems.getDefault()
            .getPathMatcher("glob:**/{api,internal}/**");
    private final PathMatcher dtoIgnore = FileSystems.getDefault()
            .getPathMatcher("glob:**/{dto,model}/**");
    private final PathMatcher resourcesIgnore = FileSystems.getDefault()
            .getPathMatcher("glob:**/{resources,config}/**");
    private final PathMatcher genProto = FileSystems.getDefault()
            .getPathMatcher("glob:**/{gen-src,generated-sources,grpc}/**");

    SourceScanResult scan(Path root, Map<String, List<String>> fileToKeys) {
        long totalFiles = countJavaFiles(root);
        List<ParsedSourceFile> includedFiles = new ArrayList<>();

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(this::isEligibleJavaFile)
                    .forEach(path -> buildParsedSourceFile(root, path, fileToKeys)
                            .ifPresent(includedFiles::add));
        } catch (IOException ioException) {
            throw new UncheckedIOException("I/O walking " + root, ioException);
        }

        return new SourceScanResult(
                root.toAbsolutePath().normalize().toString(),
                totalFiles,
                List.copyOf(includedFiles));
    }

    private long countJavaFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(JAVA_EXT))
                    .count();
        } catch (IOException ignored) {
            return -1;
        }
    }

    private boolean isEligibleJavaFile(Path path) {
        return path.toString().endsWith(JAVA_EXT)
                && !testDirMatcher.matches(path)
                && !testClassMatcher.matches(path)
                && !ignoreMatcher.matches(path)
                && !otherMatcher.matches(path)
                && !infraMatcher.matches(path)
                && !dtoIgnore.matches(path)
                && !resourcesIgnore.matches(path)
                && !genProto.matches(path);
    }

    private Optional<ParsedSourceFile> buildParsedSourceFile(
            Path root,
            Path path,
            Map<String, List<String>> fileToKeys
    ) {
        try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                LOG.warn("Skipping VERY large file {}", path);
                return Optional.empty();
            }
        } catch (IOException exception) {
            LOG.warn("Cannot stat {}, skipping - {}", path, exception.getMessage());
            return Optional.empty();
        }

        String relativePath = AnalysisPathUtils.toRelativeSourcePath(root, path);
        try {
            return Optional.of(new ParsedSourceFile(
                    relativePath,
                    Files.readString(path),
                    fileToKeys.getOrDefault(relativePath, List.of())));
        } catch (IOException exception) {
            LOG.warn("Cannot read {}, skipping - {}", path, exception.getMessage());
            return Optional.empty();
        }
    }
}
