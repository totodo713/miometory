"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { CsvUploader } from "@/components/worklog/CsvUploader";
import { downloadTemplate } from "@/services/csvService";

export default function CsvImportPage() {
  const t = useTranslations("worklog.csvUploader");
  const tc = useTranslations("common");
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  // TODO: Get actual member ID from session/auth
  const memberId = "00000000-0000-0000-0000-000000000001";

  const handleDownloadTemplate = async () => {
    setIsDownloading(true);
    setDownloadError(null);
    try {
      await downloadTemplate();
    } catch (error) {
      setDownloadError(error instanceof Error ? error.message : "Failed to download template");
    } finally {
      setIsDownloading(false);
    }
  };

  const handleImportComplete = () => {
    // Import completed successfully - could redirect or show a toast notification here
  };

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-4xl mx-auto px-4">
        {/* Header */}
        <div className="mb-8">
          <Link href="/worklog" className="text-blue-600 hover:text-blue-800 mb-4 inline-block">
            ← Back to Miometry
          </Link>
          <h1 className="text-3xl font-bold text-gray-900">{t("title")}</h1>
          <p className="text-gray-600 mt-2">{t("dropzone")}</p>
        </div>

        {/* Instructions */}
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <h2 className="text-xl font-semibold mb-4">How to Import</h2>
          <ol className="list-decimal list-inside space-y-2 text-gray-700">
            <li>Download the CSV template or prepare your own CSV file</li>
            <li>Fill in your work log entries (Date, Project Code, Hours, Notes)</li>
            <li>Upload the file using the form below</li>
            <li>Wait for the import to complete and review any errors</li>
          </ol>

          <div className="mt-6">
            <button
              type="button"
              onClick={handleDownloadTemplate}
              disabled={isDownloading}
              className="bg-gray-600 text-white py-2 px-4 rounded-lg hover:bg-gray-700 disabled:opacity-50"
            >
              {isDownloading ? tc("loading") : t("template")}
            </button>
            {downloadError && <p className="text-sm text-red-600 mt-2">{downloadError}</p>}
          </div>
        </div>

        {/* CSV Format Info */}
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6">
          <h3 className="font-semibold text-blue-900 mb-2">CSV Format</h3>
          <div className="text-sm text-blue-800 space-y-1">
            <p>
              <strong>Date:</strong> YYYY-MM-DD format (e.g., 2026-01-21)
            </p>
            <p>
              <strong>Project Code:</strong> Valid project code (e.g., PRJ-001)
            </p>
            <p>
              <strong>Hours:</strong> 0.25 increments, 0-24 range (e.g., 8.00, 4.50)
            </p>
            <p>
              <strong>Notes:</strong> Optional text, max 500 characters
            </p>
          </div>
        </div>

        {/* Upload Form */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold mb-4">{t("title")}</h2>
          <CsvUploader memberId={memberId} onImportComplete={handleImportComplete} />
        </div>
      </div>
    </div>
  );
}
