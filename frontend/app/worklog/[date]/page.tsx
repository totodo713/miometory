"use client";

/**
 * Daily Work Log Entry Page
 *
 * Allows engineers to enter or edit work log entries for a specific date.
 * Handles routing from calendar view (/worklog -> /worklog/2026-01-15)
 */

import { useRouter } from "next/navigation";
import { DailyEntryForm } from "@/components/worklog/DailyEntryForm";

interface PageProps {
	params: {
		date: string; // Format: YYYY-MM-DD
	};
}

export default function DailyEntryPage({ params }: PageProps) {
	const router = useRouter();
	const { date } = params;

	// Parse date string to Date object
	let parsedDate: Date;
	try {
		parsedDate = new Date(date);
		// Validate date
		if (Number.isNaN(parsedDate.getTime())) {
			throw new Error("Invalid date");
		}
	} catch (error) {
		// Invalid date format - redirect back to calendar
		router.push("/worklog");
		return null;
	}

	// TODO: Get actual member ID from auth context
	const memberId = "00000000-0000-0000-0000-000000000001";

	const handleClose = () => {
		router.push("/worklog");
	};

	const handleSave = () => {
		// Navigate back to calendar after successful save
		router.push("/worklog");
	};

	return (
		<div className="min-h-screen bg-gray-50">
			<DailyEntryForm
				date={parsedDate}
				memberId={memberId}
				onClose={handleClose}
				onSave={handleSave}
			/>
		</div>
	);
}
