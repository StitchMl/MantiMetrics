package com.mantimetrics.clone;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class SourceCollector {

    private SourceCollector() {
        // Prevent instantiation
    }

    /**
     * Ritorna la lista di tutti i Path ai file .java sotto src/main/java.
     *
     * @throws SourceCollectionException se fallisce l'accesso al filesystem
     */
    public static List<Path> collectJavaSources() throws SourceCollectionException {
        try (Stream<Path> stream = Files.walk(Paths.get("src/main/java"))) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        } catch (IOException e) {
            throw new SourceCollectionException(
                    "Errore nella raccolta dei sorgenti Java sotto src/main/java", e);
        }
    }
}
