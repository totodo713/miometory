package com.worklog.infrastructure.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Processes CSV files in a streaming fashion to handle large files efficiently.
 * Uses Apache Commons CSV for parsing.
 */
@Component
public class StreamingCsvProcessor {

    private final CsvValidationService validationService;

    public StreamingCsvProcessor(CsvValidationService validationService) {
        this.validationService = validationService;
    }

    /**
     * Processes a CSV file from an InputStream, validating each row and calling the consumer for valid rows.
     *
     * @param inputStream The CSV file input stream
     * @param rowConsumer Consumer to process each valid CSV row
     * @param progressCallback Callback for progress updates (row number, total processed, errors)
     * @return ProcessingResult with statistics and errors
     * @throws IOException if there's an error reading the file
     */
    public ProcessingResult processStream(
            InputStream inputStream, RowConsumer rowConsumer, ProgressCallback progressCallback) throws IOException {

        List<CsvValidationService.ValidationResult> validationErrors = new ArrayList<>();
        int totalRows = 0;
        int validRows = 0;
        int errorRows = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                CSVParser csvParser = new CSVParser(
                        reader,
                        CSVFormat.DEFAULT
                                .builder()
                                .setHeader("Date", "Project Code", "Hours", "Notes")
                                .setSkipHeaderRecord(true)
                                .setIgnoreEmptyLines(true)
                                .setTrim(true)
                                .build())) {

            for (CSVRecord record : csvParser) {
                totalRows++;
                // CSVRecord.getRecordNumber() returns physical file record index (header=1, first data row=2)
                // CsvValidationService expects rowNumber as "1-based, excluding header"
                // So we subtract 1 to convert from file position to data row number
                int rowNumber = (int) record.getRecordNumber() - 1;

                // Extract values
                String date = record.get("Date");
                String projectCode = record.get("Project Code");
                String hours = record.get("Hours");
                String notes = record.size() > 3 ? record.get("Notes") : "";

                // Validate row
                CsvValidationService.ValidationResult validationResult =
                        validationService.validateRow(rowNumber, date, projectCode, hours, notes);

                if (validationResult.isValid()) {
                    // Process valid row
                    try {
                        rowConsumer.accept(date, projectCode, hours, notes);
                        validRows++;
                    } catch (Exception e) {
                        // Processing error (e.g., database error)
                        validationErrors.add(new CsvValidationService.ValidationResult(
                                rowNumber, List.of("Processing error: " + e.getMessage())));
                        errorRows++;
                    }
                } else {
                    // Validation error
                    validationErrors.add(validationResult);
                    errorRows++;
                }

                // Report progress every 10 rows
                if (totalRows % 10 == 0 && progressCallback != null) {
                    progressCallback.onProgress(totalRows, validRows, errorRows);
                }
            }
        }

        // Final progress update
        if (progressCallback != null) {
            progressCallback.onProgress(totalRows, validRows, errorRows);
        }

        return new ProcessingResult(totalRows, validRows, errorRows, validationErrors);
    }

    /**
     * Consumer interface for processing valid CSV rows.
     */
    @FunctionalInterface
    public interface RowConsumer {
        void accept(String date, String projectCode, String hours, String notes) throws Exception;
    }

    /**
     * Callback interface for progress updates.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int totalRows, int validRows, int errorRows);
    }

    /**
     * Result of CSV processing.
     */
    public static class ProcessingResult {
        private final int totalRows;
        private final int validRows;
        private final int errorRows;
        private final List<CsvValidationService.ValidationResult> validationErrors;

        public ProcessingResult(
                int totalRows,
                int validRows,
                int errorRows,
                List<CsvValidationService.ValidationResult> validationErrors) {
            this.totalRows = totalRows;
            this.validRows = validRows;
            this.errorRows = errorRows;
            this.validationErrors = validationErrors;
        }

        public int getTotalRows() {
            return totalRows;
        }

        public int getValidRows() {
            return validRows;
        }

        public int getErrorRows() {
            return errorRows;
        }

        public List<CsvValidationService.ValidationResult> getValidationErrors() {
            return validationErrors;
        }

        public boolean hasErrors() {
            return errorRows > 0;
        }
    }
}
