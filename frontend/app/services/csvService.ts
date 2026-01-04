/**
 * CSV Import/Export Service
 * 
 * Provides methods for:
 * - Downloading CSV template
 * - Importing CSV files with progress tracking
 * - Exporting work log data to CSV
 */

export interface CsvImportProgress {
	totalRows: number
	validRows: number
	errorRows: number
	status: "processing" | "completed" | "failed"
	errors?: Array<{
		row: number
		errors: string[]
	}>
}

export interface CsvImportResult {
	importId: string
}

/**
 * Download the CSV template file.
 */
export async function downloadTemplate(): Promise<void> {
	const response = await fetch("/api/v1/worklog/csv/template")
	if (!response.ok) {
		throw new Error("Failed to download template")
	}

	const blob = await response.blob()
	const url = window.URL.createObjectURL(blob)
	const a = document.createElement("a")
	a.href = url
	a.download = "worklog-template.csv"
	document.body.appendChild(a)
	a.click()
	document.body.removeChild(a)
	window.URL.revokeObjectURL(url)
}

/**
 * Import a CSV file.
 * Returns an import ID that can be used to track progress.
 */
export async function importCsv(
	file: File,
	memberId: string,
): Promise<CsvImportResult> {
	const formData = new FormData()
	formData.append("file", file)
	formData.append("memberId", memberId)

	const response = await fetch("/api/v1/worklog/csv/import", {
		method: "POST",
		body: formData,
	})

	if (!response.ok) {
		throw new Error("Failed to start CSV import")
	}

	return response.json()
}

/**
 * Subscribe to import progress updates via Server-Sent Events.
 * 
 * @param importId Import ID from importCsv()
 * @param onProgress Callback for progress updates
 * @param onError Callback for errors
 * @param onComplete Callback when import completes
 */
export function subscribeToImportProgress(
	importId: string,
	onProgress: (progress: CsvImportProgress) => void,
	onError: (error: string) => void,
	onComplete: () => void,
): () => void {
	const eventSource = new EventSource(
		`/api/v1/worklog/csv/import/${importId}/progress`,
	)

	eventSource.addEventListener("progress", (event) => {
		const progress = JSON.parse(event.data) as CsvImportProgress
		onProgress(progress)

		if (progress.status === "completed" || progress.status === "failed") {
			eventSource.close()
			onComplete()
		}
	})

	eventSource.addEventListener("error", (event) => {
		const errorData = JSON.parse((event as MessageEvent).data)
		onError(errorData.error || "Unknown error")
		eventSource.close()
	})

	eventSource.onerror = () => {
		onError("Connection to server lost")
		eventSource.close()
		onComplete()
	}

	// Return cleanup function
	return () => {
		eventSource.close()
	}
}

/**
 * Export work log entries for a specific month to CSV.
 * 
 * @param year Year (e.g., 2026)
 * @param month Month (1-12)
 * @param memberId Member ID to export entries for
 */
export async function exportCsv(
	year: number,
	month: number,
	memberId: string,
): Promise<void> {
	const response = await fetch(
		`/api/v1/worklog/csv/export/${year}/${month}?memberId=${memberId}`,
	)

	if (!response.ok) {
		throw new Error("Failed to export CSV")
	}

	const blob = await response.blob()
	const url = window.URL.createObjectURL(blob)
	const a = document.createElement("a")
	a.href = url
	a.download = `worklog-${year}-${String(month).padStart(2, "0")}.csv`
	document.body.appendChild(a)
	a.click()
	document.body.removeChild(a)
	window.URL.revokeObjectURL(url)
}
