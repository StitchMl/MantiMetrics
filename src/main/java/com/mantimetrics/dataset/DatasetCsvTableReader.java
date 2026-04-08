package com.mantimetrics.dataset;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DatasetCsvTableReader {

    public DatasetTable read(Path csvPath) throws IOException {
        try (CSVReader reader = new CSVReader(Files.newBufferedReader(csvPath, StandardCharsets.UTF_8))) {
            String[] headerRow = reader.readNext();
            if (headerRow == null) {
                return new DatasetTable(List.of(), List.of());
            }

            List<String> header = List.of(headerRow);
            List<Map<String, String>> rows = new ArrayList<>();
            String[] nextRow;
            while ((nextRow = reader.readNext()) != null) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < header.size(); index++) {
                    row.put(header.get(index), index < nextRow.length ? nextRow[index] : "");
                }
                rows.add(row);
            }
            return new DatasetTable(header, rows);
        } catch (CsvValidationException exception) {
            throw new IOException("Failed to read CSV dataset from " + csvPath, exception);
        }
    }
}
