"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useId, useReducer, useRef, useState } from "react";
import { useToast } from "@/hooks/useToast";
import { api, type OrganizationTreeNode } from "@/services/api";
import type { MemberCsvDryRunResult } from "@/services/memberCsvService";
import { downloadMemberCsvTemplate, dryRunMemberCsv, executeMemberCsvImport } from "@/services/memberCsvService";

// --- CSV file validation (same logic as CsvUploader.tsx) ---

const CSV_MIME_TYPES = ["text/csv", "application/csv", "application/vnd.ms-excel", "text/plain", "text/x-csv", ""];

function isCsvFile(file: File): boolean {
  if (file.name.toLowerCase().endsWith(".csv")) {
    return true;
  }
  return CSV_MIME_TYPES.includes(file.type);
}

// --- Organization tree flattening ---

interface FlatOrgOption {
  id: string;
  label: string;
  level: number;
}

function flattenOrgTree(nodes: OrganizationTreeNode[], depth = 0): FlatOrgOption[] {
  const result: FlatOrgOption[] = [];
  for (const node of nodes) {
    if (node.status !== "ACTIVE") continue;
    const indent = "\u00A0\u00A0".repeat(depth * 2);
    result.push({ id: node.id, label: `${indent}${node.code} - ${node.name}`, level: depth });
    result.push(...flattenOrgTree(node.children, depth + 1));
  }
  return result;
}

// --- Wizard state machine ---

const STEP_NAMES = ["upload", "dry-run-results", "importing", "complete"] as const;

type WizardState =
  | { step: "upload" }
  | { step: "dry-run-results"; result: MemberCsvDryRunResult }
  | { step: "importing"; sessionId: string; validRows: number }
  | { step: "complete"; importedCount: number };

type WizardAction =
  | { type: "DRY_RUN_SUCCESS"; result: MemberCsvDryRunResult }
  | { type: "EXECUTE_START" }
  | { type: "EXECUTE_COMPLETE"; importedCount: number }
  | { type: "RESET" };

function wizardReducer(state: WizardState, action: WizardAction): WizardState {
  switch (action.type) {
    case "DRY_RUN_SUCCESS":
      return { step: "dry-run-results", result: action.result };
    case "EXECUTE_START":
      if (state.step !== "dry-run-results") return state;
      return { step: "importing", sessionId: state.result.sessionId, validRows: state.result.validRows };
    case "EXECUTE_COMPLETE":
      return { step: "complete", importedCount: action.importedCount };
    case "RESET":
      return { step: "upload" };
    default:
      return state;
  }
}

// --- Step indicator ---

function WizardSteps({ currentStep }: { currentStep: WizardState["step"] }) {
  const t = useTranslations("admin.members.csvImport.steps");
  const stepKeys = ["upload", "dryRun", "importing", "complete"] as const;
  const currentIndex = STEP_NAMES.indexOf(currentStep);

  return (
    <div className="flex items-center mb-8">
      {stepKeys.map((key, index) => (
        <div key={key} className="flex items-center flex-1">
          <div className="flex items-center">
            <div
              className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium ${
                index < currentIndex
                  ? "bg-green-600 text-white"
                  : index === currentIndex
                    ? "bg-blue-600 text-white"
                    : "bg-gray-200 text-gray-500"
              }`}
            >
              {index < currentIndex ? "✓" : index + 1}
            </div>
            <span
              className={`ml-2 text-sm hidden sm:inline ${
                index === currentIndex ? "font-medium text-gray-900" : "text-gray-500"
              }`}
            >
              {t(key)}
            </span>
          </div>
          {index < stepKeys.length - 1 && <div className="flex-1 h-px bg-gray-300 mx-3" />}
        </div>
      ))}
    </div>
  );
}

// --- Step 1: Upload ---

function StepUpload({ onDryRunSuccess }: { onDryRunSuccess: (result: MemberCsvDryRunResult) => void }) {
  const t = useTranslations("admin.members.csvImport.uploadStep");
  const toast = useToast();

  const [file, setFile] = useState<File | null>(null);
  const [organizationId, setOrganizationId] = useState("");
  const [isDragging, setIsDragging] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [templateError, setTemplateError] = useState<string | null>(null);
  const [orgOptions, setOrgOptions] = useState<FlatOrgOption[]>([]);
  const [isLoadingOrgs, setIsLoadingOrgs] = useState(true);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const inputId = useId();

  useEffect(() => {
    let cancelled = false;
    api.admin.organizations
      .getOrganizationTree(false)
      .then((tree) => {
        if (!cancelled) {
          setOrgOptions(flattenOrgTree(tree));
          setIsLoadingOrgs(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setIsLoadingOrgs(false);
        }
      });
    return () => {
      cancelled = true;
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

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setIsDragging(false);
      const droppedFile = e.dataTransfer.files[0];
      if (droppedFile && isCsvFile(droppedFile)) {
        setFile(droppedFile);
        setError(null);
      } else {
        setError(t("formatError"));
      }
    },
    [t],
  );

  const handleFileSelect = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const selectedFile = e.target.files?.[0];
      if (selectedFile) {
        if (!isCsvFile(selectedFile)) {
          setError(t("formatError"));
          return;
        }
        setFile(selectedFile);
        setError(null);
      }
    },
    [t],
  );

  const handleRemoveFile = useCallback(() => {
    setFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  }, []);

  const handleDownloadTemplate = useCallback(async () => {
    setTemplateError(null);
    try {
      await downloadMemberCsvTemplate();
    } catch {
      setTemplateError(t("downloadTemplateError"));
    }
  }, [t]);

  const handleSubmit = useCallback(async () => {
    if (!file) {
      setError(t("fileRequired"));
      return;
    }
    if (!organizationId) {
      setError(t("orgRequired"));
      return;
    }
    setIsSubmitting(true);
    setError(null);
    try {
      const result = await dryRunMemberCsv(file, organizationId);
      onDryRunSuccess(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Dry run failed");
      toast.error(err instanceof Error ? err.message : "Dry run failed");
    } finally {
      setIsSubmitting(false);
    }
  }, [file, organizationId, onDryRunSuccess, t, toast]);

  return (
    <div className="space-y-6">
      {/* Template download */}
      <div>
        <button
          type="button"
          onClick={handleDownloadTemplate}
          className="text-sm text-blue-600 hover:text-blue-800 underline"
        >
          {t("downloadTemplate")}
        </button>
        {templateError && <p className="mt-1 text-sm text-red-600">{templateError}</p>}
      </div>

      {/* Organization selector */}
      <div>
        <label htmlFor={`${inputId}-org`} className="block text-sm font-medium text-gray-700 mb-1">
          {t("organization")}
        </label>
        <select
          id={`${inputId}-org`}
          value={organizationId}
          onChange={(e) => setOrganizationId(e.target.value)}
          disabled={isLoadingOrgs}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">{t("selectOrganization")}</option>
          {orgOptions.map((opt) => (
            <option key={opt.id} value={opt.id}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      {/* File upload area */}
      {!file ? (
        <button
          type="button"
          className={`w-full border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors duration-200 ${
            isDragging ? "border-blue-500 bg-blue-50" : "border-gray-300 hover:border-gray-400"
          }`}
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
            aria-label={t("selectFile")}
          />
          <div className="text-gray-600">
            <p className="text-lg font-medium mb-2">{t("dropzone")}</p>
            <p className="text-sm text-gray-500">{t("selectFile")}</p>
          </div>
        </button>
      ) : (
        <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
          <div>
            <p className="font-medium">{file.name}</p>
            <p className="text-sm text-gray-500">{(file.size / 1024).toFixed(2)} KB</p>
          </div>
          <button
            type="button"
            onClick={handleRemoveFile}
            aria-label="Remove selected file"
            className="text-red-600 hover:text-red-800 text-sm"
          >
            ✕
          </button>
        </div>
      )}

      {/* Error display */}
      {error && (
        <div className="p-3 bg-red-50 border border-red-200 rounded-lg">
          <p className="text-sm text-red-700">{error}</p>
        </div>
      )}

      {/* Submit button */}
      <button
        type="button"
        onClick={handleSubmit}
        disabled={isSubmitting || !file || !organizationId}
        className="w-full bg-blue-600 text-white py-2 px-4 rounded-lg hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
      >
        {isSubmitting ? "..." : t("runDryRun")}
      </button>
    </div>
  );
}

// --- Step 2: Dry-run Results ---

function StepDryRunResults({
  result,
  onProceed,
  onCancel,
}: {
  result: MemberCsvDryRunResult;
  onProceed: () => void;
  onCancel: () => void;
}) {
  const t = useTranslations("admin.members.csvImport.dryRunStep");
  const errorRows = result.rows.filter((r) => r.status === "ERROR");

  return (
    <div className="space-y-6">
      {/* Summary */}
      <div className="p-4 bg-gray-50 rounded-lg">
        <p className="font-medium text-gray-900">
          {t("summary", { total: result.totalRows, valid: result.validRows, errors: result.errorRows })}
        </p>
      </div>

      {/* Error table */}
      {errorRows.length > 0 ? (
        <div className="overflow-x-auto">
          <table className="w-full text-sm border-collapse">
            <caption className="sr-only">{t("errorTableCaption")}</caption>
            <thead>
              <tr className="bg-gray-100 border-b border-gray-200">
                <th className="px-3 py-2 text-left font-medium text-gray-700">{t("colRow")}</th>
                <th className="px-3 py-2 text-left font-medium text-gray-700">{t("colEmail")}</th>
                <th className="px-3 py-2 text-left font-medium text-gray-700">{t("colName")}</th>
                <th className="px-3 py-2 text-left font-medium text-gray-700">{t("colErrors")}</th>
              </tr>
            </thead>
            <tbody>
              {errorRows.map((row) => (
                <tr key={row.rowNumber} className="border-b border-gray-100">
                  <td className="px-3 py-2 text-gray-600">{row.rowNumber}</td>
                  <td className="px-3 py-2 text-gray-600">{row.email}</td>
                  <td className="px-3 py-2 text-gray-600">{row.displayName}</td>
                  <td className="px-3 py-2 text-red-600">{row.errors.join(" / ")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="p-4 bg-green-50 border border-green-200 rounded-lg">
          <p className="text-sm text-green-700">{t("noErrors")}</p>
        </div>
      )}

      {/* No valid rows warning */}
      {result.validRows === 0 && (
        <div className="p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
          <p className="text-sm text-yellow-700">{t("noValidRows")}</p>
        </div>
      )}

      {/* Action buttons */}
      <div className="flex gap-3">
        <button
          type="button"
          onClick={onCancel}
          className="flex-1 px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
        >
          {t("cancelButton")}
        </button>
        <button
          type="button"
          onClick={onProceed}
          disabled={result.validRows === 0}
          className="flex-1 px-4 py-2 text-sm text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
        >
          {t("proceedButton", { count: result.validRows })}
        </button>
      </div>
    </div>
  );
}

// --- Step 3: Importing ---

function StepImporting({
  sessionId,
  validRows,
  onComplete,
  onError,
}: {
  sessionId: string;
  validRows: number;
  onComplete: (importedCount: number) => void;
  onError: (message: string) => void;
}) {
  const t = useTranslations("admin.members.csvImport.importingStep");

  useEffect(() => {
    const controller = new AbortController();
    executeMemberCsvImport(sessionId, controller.signal)
      .then(() => {
        if (!controller.signal.aborted) {
          onComplete(validRows);
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted) {
          onError(err instanceof Error ? err.message : "Import failed");
        }
      });
    return () => {
      controller.abort();
    };
  }, [sessionId, validRows, onComplete, onError]);

  return (
    <div className="flex flex-col items-center justify-center py-12">
      <div className="animate-spin rounded-full h-12 w-12 border-4 border-gray-200 border-t-blue-600 mb-4" />
      <p className="text-gray-700 font-medium">{t("message")}</p>
    </div>
  );
}

// --- Step 4: Complete ---

function StepComplete({ importedCount, onReset }: { importedCount: number; onReset: () => void }) {
  const t = useTranslations("admin.members.csvImport.completeStep");

  return (
    <div className="space-y-6">
      <div className="p-6 bg-green-50 border border-green-200 rounded-lg text-center">
        <p className="text-lg font-medium text-green-800">{t("success", { count: importedCount })}</p>
      </div>
      <div className="flex justify-center">
        <button
          type="button"
          onClick={onReset}
          className="px-4 py-2 text-sm text-blue-600 border border-blue-300 rounded-md hover:bg-blue-50"
        >
          {t("importAnother")}
        </button>
      </div>
    </div>
  );
}

// --- Main component ---

export function MemberCsvImport() {
  const t = useTranslations("admin.members.csvImport.error");
  const toast = useToast();
  const [state, dispatch] = useReducer(wizardReducer, { step: "upload" });
  const [executeError, setExecuteError] = useState<string | null>(null);

  const handleDryRunSuccess = useCallback((result: MemberCsvDryRunResult) => {
    dispatch({ type: "DRY_RUN_SUCCESS", result });
  }, []);

  const handleProceed = useCallback(() => {
    setExecuteError(null);
    dispatch({ type: "EXECUTE_START" });
  }, []);

  const handleReset = useCallback(() => {
    setExecuteError(null);
    dispatch({ type: "RESET" });
  }, []);

  const handleExecuteComplete = useCallback((importedCount: number) => {
    dispatch({ type: "EXECUTE_COMPLETE", importedCount });
  }, []);

  const handleExecuteError = useCallback(
    (message: string) => {
      setExecuteError(message);
      toast.error(t("importFailed", { message }));
    },
    [t, toast],
  );

  return (
    <div className="max-w-3xl">
      <WizardSteps currentStep={state.step} />

      {state.step === "upload" && <StepUpload onDryRunSuccess={handleDryRunSuccess} />}

      {state.step === "dry-run-results" && (
        <StepDryRunResults result={state.result} onProceed={handleProceed} onCancel={handleReset} />
      )}

      {state.step === "importing" && !executeError && (
        <StepImporting
          sessionId={state.sessionId}
          validRows={state.validRows}
          onComplete={handleExecuteComplete}
          onError={handleExecuteError}
        />
      )}

      {state.step === "complete" && <StepComplete importedCount={state.importedCount} onReset={handleReset} />}

      {/* Execute error with retry/reset options */}
      {executeError && state.step === "importing" && (
        <div className="space-y-4">
          <div className="p-4 bg-red-50 border border-red-200 rounded-lg">
            <p className="font-medium text-red-800">{t("importFailed", { message: executeError })}</p>
          </div>
          <div className="flex gap-3">
            <button
              type="button"
              onClick={handleReset}
              className="flex-1 px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
            >
              {t("resetButton")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
