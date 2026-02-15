"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import type { CsvImportProgress } from "@/services/csvService";
import { importCsv, subscribeToImportProgress } from "@/services/csvService";

// Common MIME types for CSV files across different browsers/OSes
const CSV_MIME_TYPES = [
  "text/csv",
  "application/csv",
  "application/vnd.ms-excel",
  "text/plain",
  "text/x-csv",
  "", // Some browsers report empty MIME type
];

/**
 * Check if a file is a valid CSV file.
 * Accepts common CSV MIME variants and relies on .csv extension as fallback.
 */
function isCsvFile(file: File): boolean {
  // Check by extension first (most reliable)
  if (file.name.toLowerCase().endsWith(".csv")) {
    return true;
  }
  // Fallback to MIME type check
  return CSV_MIME_TYPES.includes(file.type);
}

interface CsvUploaderProps {
  memberId: string;
  onImportComplete?: () => void;
}

export function CsvUploader({ memberId, onImportComplete }: CsvUploaderProps) {
  const [file, setFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [progress, setProgress] = useState<CsvImportProgress | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Ref to store EventSource cleanup function
  const eventSourceCleanupRef = useRef<(() => void) | null>(null);
  // Ref for the file input element to allow value reset
  const fileInputRef = useRef<HTMLInputElement>(null);
  // Use React's useId for unique IDs (safe for SSR and multiple instances)
  const inputId = useId();

  // Cleanup EventSource on unmount
  useEffect(() => {
    return () => {
      if (eventSourceCleanupRef.current) {
        eventSourceCleanupRef.current();
        eventSourceCleanupRef.current = null;
      }
    };
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);

    const droppedFile = e.dataTransfer.files[0];
    if (droppedFile && isCsvFile(droppedFile)) {
      setFile(droppedFile);
      setError(null);
    } else {
      setError("Please drop a CSV file");
    }
  }, []);

  const handleFileSelect = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      setFile(selectedFile);
      setError(null);
    }
  }, []);

  const handleImport = useCallback(async () => {
    if (!file) return;

    setIsImporting(true);
    setError(null);
    setProgress(null);

    try {
      const result = await importCsv(file, memberId);

      // Subscribe to progress updates
      const cleanup = subscribeToImportProgress(
        result.importId,
        (progressData) => {
          setProgress(progressData);
        },
        (errorMsg) => {
          setError(errorMsg);
          setIsImporting(false);
        },
        () => {
          setIsImporting(false);
          // Use functional update to get latest progress state (avoids stale closure)
          setProgress((currentProgress) => {
            if (currentProgress?.status === "completed") {
              onImportComplete?.();
            }
            return currentProgress;
          });
        },
      );

      // Store cleanup function for unmount
      eventSourceCleanupRef.current = cleanup;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import failed");
      setIsImporting(false);
    }
  }, [file, memberId, onImportComplete]);

  const handleReset = useCallback(() => {
    setFile(null);
    setProgress(null);
    setError(null);
    setIsImporting(false);
    // Clear the file input value so selecting the same file triggers onChange
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }, []);

  const getProgressPercentage = () => {
    if (!progress || progress.totalRows === 0) return 0;
    return Math.round(((progress.validRows + progress.errorRows) / progress.totalRows) * 100);
  };

  return (
    <div className="space-y-4">
      {/* File Upload Area */}
      {!file && !isImporting && (
        <button
          type="button"
          className={`
						w-full border-2 border-dashed rounded-lg p-8 text-center cursor-pointer
						transition-colors duration-200
						${isDragging ? "border-blue-500 bg-blue-50" : "border-gray-300 hover:border-gray-400"}
					`}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
        >
          <input
            ref={fileInputRef}
            id={inputId}
            type="file"
            accept=".csv"
            onChange={handleFileSelect}
            className="hidden"
            aria-label="Select CSV file to import"
          />
          <div className="text-gray-600">
            <p className="text-lg font-medium mb-2">Drop CSV file here or click to browse</p>
            <p className="text-sm text-gray-500">Supported format: CSV (Date, Project Code, Hours, Notes)</p>
          </div>
        </button>
      )}

      {/* File Selected */}
      {file && !isImporting && !progress && (
        <div className="space-y-4">
          <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
            <div>
              <p className="font-medium">{file.name}</p>
              <p className="text-sm text-gray-500">{(file.size / 1024).toFixed(2)} KB</p>
            </div>
            <button
              type="button"
              onClick={handleReset}
              aria-label="Remove selected file"
              className="text-red-600 hover:text-red-800"
            >
              Remove
            </button>
          </div>
          <button
            type="button"
            onClick={handleImport}
            className="w-full bg-blue-600 text-white py-2 px-4 rounded-lg hover:bg-blue-700"
          >
            Import CSV
          </button>
        </div>
      )}

      {/* Import Progress */}
      {isImporting && progress && (
        <div className="space-y-4">
          <div className="p-4 bg-gray-50 rounded-lg">
            <div className="flex items-center justify-between mb-2">
              <span className="font-medium">Importing...</span>
              <span className="text-sm text-gray-600">{getProgressPercentage()}%</span>
            </div>
            <div className="w-full bg-gray-200 rounded-full h-2">
              <div
                className="bg-blue-600 h-2 rounded-full transition-all duration-300"
                style={{ width: `${getProgressPercentage()}%` }}
              />
            </div>
            <div className="mt-2 text-sm text-gray-600">
              <p>Total rows: {progress.totalRows}</p>
              <p className="text-green-600">Valid: {progress.validRows}</p>
              {progress.errorRows > 0 && <p className="text-red-600">Errors: {progress.errorRows}</p>}
            </div>
          </div>
        </div>
      )}

      {/* Completion Status */}
      {progress?.status === "completed" && !isImporting && (
        <div className="p-4 bg-green-50 border border-green-200 rounded-lg">
          <p className="font-medium text-green-800">Import completed!</p>
          <p className="text-sm text-green-700 mt-1">
            Successfully imported {progress.validRows} of {progress.totalRows} rows
          </p>
          {progress.errorRows > 0 && (
            <p className="text-sm text-yellow-700 mt-1">{progress.errorRows} rows had errors and were skipped</p>
          )}
          <button type="button" onClick={handleReset} className="mt-3 text-sm text-blue-600 hover:text-blue-800">
            Import another file
          </button>
        </div>
      )}

      {/* Error Display */}
      {error && (
        <div className="p-4 bg-red-50 border border-red-200 rounded-lg">
          <p className="font-medium text-red-800">Error</p>
          <p className="text-sm text-red-700 mt-1">{error}</p>
        </div>
      )}

      {/* Validation Errors */}
      {progress?.errors && progress.errors.length > 0 && (
        <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg max-h-64 overflow-y-auto">
          <p className="font-medium text-yellow-800 mb-2">Validation Errors</p>
          <div className="space-y-2">
            {progress.errors.slice(0, 100).map((error) => (
              <div key={error.row} className="text-sm">
                <span className="font-medium text-yellow-900">Row {error.row}:</span>{" "}
                <span className="text-yellow-700">{error.errors.join(", ")}</span>
              </div>
            ))}
            {progress.errors.length > 100 && (
              <p className="text-sm text-yellow-700 italic">... and {progress.errors.length - 100} more errors</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
