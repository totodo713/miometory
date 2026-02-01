package com.worklog.infrastructure.csv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for StreamingCsvProcessor.
 * 
 * Verifies SC-005: CSV import processes 100 rows/second minimum
 * (100K rows should complete in <1000 seconds)
 * 
 * Task: T144 - Performance test for CSV import (100K rows in <1000s, SC-005)
 */
@Tag("performance")
class StreamingCsvProcessorPerformanceTest {

    private StreamingCsvProcessor processor;
    private CsvValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new CsvValidationService();
        processor = new StreamingCsvProcessor(validationService);
    }

    @Test
    @DisplayName("SC-005: CSV processing should handle 100K rows at minimum 100 rows/second")
    void processStream_100KRows_completesWithinTimeLimit() throws IOException {
        // Arrange - Generate 100K row CSV content
        int rowCount = 100_000;
        String csvContent = generateCsvWithRows(rowCount);
        byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);
        
        System.out.println("Generated CSV with " + rowCount + " rows, size: " + (csvBytes.length / 1024) + " KB");

        AtomicInteger processedRows = new AtomicInteger(0);
        AtomicInteger progressUpdates = new AtomicInteger(0);

        // Consumer that simulates minimal work (counting)
        StreamingCsvProcessor.RowConsumer rowConsumer = (date, projectCode, hours, notes) -> {
            processedRows.incrementAndGet();
        };

        // Progress callback
        StreamingCsvProcessor.ProgressCallback progressCallback = (total, valid, errors) -> {
            progressUpdates.incrementAndGet();
        };

        // Act - Measure processing time
        long startTime = System.currentTimeMillis();

        StreamingCsvProcessor.ProcessingResult result;
        try (InputStream inputStream = new ByteArrayInputStream(csvBytes)) {
            result = processor.processStream(inputStream, rowConsumer, progressCallback);
        }

        long endTime = System.currentTimeMillis();
        double elapsedTimeSeconds = (endTime - startTime) / 1000.0;
        double rowsPerSecond = rowCount / elapsedTimeSeconds;

        // Assert
        System.out.println("\n=== Performance Results ===");
        System.out.println("  Total rows generated: " + rowCount);
        System.out.println("  Rows processed: " + result.getTotalRows());
        System.out.println("  Valid rows: " + result.getValidRows());
        System.out.println("  Error rows: " + result.getErrorRows());
        System.out.println("  Elapsed time: " + String.format("%.2f", elapsedTimeSeconds) + " seconds");
        System.out.println("  Rows per second: " + String.format("%.2f", rowsPerSecond));
        System.out.println("  Progress updates: " + progressUpdates.get());
        System.out.println("  Target: 100 rows/second (1000 seconds max for 100K rows)");
        System.out.println("===========================\n");

        // Verify all rows were processed
        assertEquals(rowCount, result.getTotalRows(), "All rows should be processed");
        
        // SC-005: CSV import processes 100 rows/second minimum
        // 100K rows should complete in <1000 seconds
        double maxAllowedTimeSeconds = 1000.0;
        assertTrue(
            elapsedTimeSeconds < maxAllowedTimeSeconds,
            String.format(
                "CSV processing too slow: %.2fs exceeds %.0fs limit. Rate: %.2f rows/sec (minimum required: 100 rows/sec)",
                elapsedTimeSeconds, maxAllowedTimeSeconds, rowsPerSecond
            )
        );

        // Additional assertion: should actually be much faster than minimum
        // Typical streaming CSV should process 10,000+ rows/second
        assertTrue(
            rowsPerSecond >= 100,
            String.format(
                "CSV processing rate %.2f rows/sec is below minimum 100 rows/sec requirement (SC-005)",
                rowsPerSecond
            )
        );

        // Progress should be reported periodically
        assertTrue(progressUpdates.get() > 0, "Progress should be reported during processing");
    }

    @Test
    @DisplayName("CSV processing should maintain performance with validation errors")
    void processStream_withValidationErrors_maintainsPerformance() throws IOException {
        // Arrange - Generate CSV with 50K valid rows and 10K invalid rows
        int validRowCount = 50_000;
        int invalidRowCount = 10_000;
        String csvContent = generateCsvWithMixedValidity(validRowCount, invalidRowCount);
        byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);

        AtomicInteger processedRows = new AtomicInteger(0);

        StreamingCsvProcessor.RowConsumer rowConsumer = (date, projectCode, hours, notes) -> {
            processedRows.incrementAndGet();
        };

        // Act
        long startTime = System.currentTimeMillis();

        StreamingCsvProcessor.ProcessingResult result;
        try (InputStream inputStream = new ByteArrayInputStream(csvBytes)) {
            result = processor.processStream(inputStream, rowConsumer, null);
        }

        long endTime = System.currentTimeMillis();
        double elapsedTimeSeconds = (endTime - startTime) / 1000.0;
        int totalRows = validRowCount + invalidRowCount;
        double rowsPerSecond = totalRows / elapsedTimeSeconds;

        // Assert
        System.out.println("\n=== Mixed Validity Performance ===");
        System.out.println("  Valid rows generated: " + validRowCount);
        System.out.println("  Invalid rows generated: " + invalidRowCount);
        System.out.println("  Rows processed: " + result.getTotalRows());
        System.out.println("  Valid rows processed: " + result.getValidRows());
        System.out.println("  Error rows: " + result.getErrorRows());
        System.out.println("  Elapsed time: " + String.format("%.2f", elapsedTimeSeconds) + " seconds");
        System.out.println("  Rows per second: " + String.format("%.2f", rowsPerSecond));
        System.out.println("==================================\n");

        assertEquals(totalRows, result.getTotalRows(), "All rows should be processed");
        assertTrue(result.getErrorRows() > 0, "Some rows should have validation errors");
        
        // Performance should still meet minimum requirement
        assertTrue(
            rowsPerSecond >= 100,
            String.format("Processing rate %.2f rows/sec is below minimum (SC-005)", rowsPerSecond)
        );
    }

    @Test
    @DisplayName("CSV processing should scale linearly with row count")
    void processStream_scalingTest_linearPerformance() throws IOException {
        int[] rowCounts = {1_000, 10_000, 50_000};
        double[] timesPerThousand = new double[rowCounts.length];

        for (int i = 0; i < rowCounts.length; i++) {
            int rowCount = rowCounts[i];
            String csvContent = generateCsvWithRows(rowCount);
            byte[] csvBytes = csvContent.getBytes(StandardCharsets.UTF_8);

            StreamingCsvProcessor.RowConsumer rowConsumer = (date, projectCode, hours, notes) -> {};

            long startTime = System.currentTimeMillis();

            try (InputStream inputStream = new ByteArrayInputStream(csvBytes)) {
                processor.processStream(inputStream, rowConsumer, null);
            }

            long endTime = System.currentTimeMillis();
            double elapsedTimeSeconds = (endTime - startTime) / 1000.0;
            timesPerThousand[i] = (elapsedTimeSeconds / rowCount) * 1000;

            System.out.println(String.format(
                "%d rows: %.3fs (%.4f seconds per 1K rows)",
                rowCount, elapsedTimeSeconds, timesPerThousand[i]
            ));
        }

        // Verify scaling is approximately linear (later runs shouldn't be much slower per row)
        // Allow for some variance due to JIT warmup
        double maxVariance = 3.0; // Allow 3x variance
        for (int i = 1; i < timesPerThousand.length; i++) {
            assertTrue(
                timesPerThousand[i] < timesPerThousand[0] * maxVariance,
                String.format(
                    "Processing time should scale linearly. First: %.4f, Current: %.4f (variance: %.2fx)",
                    timesPerThousand[0], timesPerThousand[i], timesPerThousand[i] / timesPerThousand[0]
                )
            );
        }
    }

    /**
     * Generates CSV content with the specified number of valid rows.
     */
    private String generateCsvWithRows(int rowCount) {
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Project Code,Hours,Notes\n");

        String[] projectCodes = {"PRJ-001", "PRJ-002", "PRJ-003"};
        String[] hoursOptions = {"1.00", "2.00", "4.00", "8.00"};

        for (int i = 1; i <= rowCount; i++) {
            int month = ((i - 1) / 28) % 12 + 1;
            int day = (i - 1) % 28 + 1;
            int year = 2025 - ((i - 1) / (28 * 12));
            String projectCode = projectCodes[i % projectCodes.length];
            String hours = hoursOptions[i % hoursOptions.length];

            csv.append(String.format(
                "%04d-%02d-%02d,%s,%s,Performance test row %d\n",
                year, month, day, projectCode, hours, i
            ));
        }

        return csv.toString();
    }

    /**
     * Generates CSV content with a mix of valid and invalid rows.
     */
    private String generateCsvWithMixedValidity(int validRows, int invalidRows) {
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Project Code,Hours,Notes\n");

        String[] projectCodes = {"PRJ-001", "PRJ-002", "PRJ-003"};
        int totalRows = validRows + invalidRows;
        int invalidInterval = totalRows / invalidRows; // Spread invalid rows evenly

        int validCount = 0;
        int invalidCount = 0;

        for (int i = 1; i <= totalRows; i++) {
            if (i % invalidInterval == 0 && invalidCount < invalidRows) {
                // Generate invalid row
                csv.append(String.format(
                    "INVALID-DATE,INVALID-PRJ,999.99,Invalid row %d\n",
                    i
                ));
                invalidCount++;
            } else if (validCount < validRows) {
                // Generate valid row
                int month = ((i - 1) / 28) % 12 + 1;
                int day = (i - 1) % 28 + 1;
                int year = 2025 - ((i - 1) / (28 * 12));
                String projectCode = projectCodes[i % projectCodes.length];

                csv.append(String.format(
                    "%04d-%02d-%02d,%s,8.00,Valid row %d\n",
                    year, month, day, projectCode, i
                ));
                validCount++;
            }
        }

        return csv.toString();
    }
}
