"use client";

import { useEffect, useRef } from "react";

interface ConfirmDialogProps {
	open: boolean;
	title: string;
	message: string;
	confirmLabel: string;
	variant: "danger" | "warning";
	onConfirm: () => void;
	onCancel: () => void;
}

const confirmButtonStyles = {
	danger: "bg-red-600 hover:bg-red-700 text-white",
	warning: "bg-yellow-600 hover:bg-yellow-700 text-white",
};

export function ConfirmDialog({ open, title, message, confirmLabel, variant, onConfirm, onCancel }: ConfirmDialogProps) {
	const cancelRef = useRef<HTMLButtonElement>(null);

	useEffect(() => {
		if (open) cancelRef.current?.focus();
	}, [open]);

	useEffect(() => {
		if (!open) return;
		const handler = (e: KeyboardEvent) => {
			if (e.key === "Escape") onCancel();
		};
		document.addEventListener("keydown", handler);
		return () => document.removeEventListener("keydown", handler);
	}, [open, onCancel]);

	if (!open) return null;

	return (
		<div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onCancel}>
			<div
				className="bg-white rounded-lg shadow-xl p-6 max-w-sm w-full mx-4"
				onClick={(e) => e.stopPropagation()}
				role="alertdialog"
				aria-labelledby="confirm-title"
				aria-describedby="confirm-message"
			>
				<h3 id="confirm-title" className="text-lg font-semibold text-gray-900">
					{title}
				</h3>
				<p id="confirm-message" className="mt-2 text-sm text-gray-600">
					{message}
				</p>
				<div className="mt-4 flex justify-end gap-3">
					<button
						type="button"
						ref={cancelRef}
						onClick={onCancel}
						className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
					>
						キャンセル
					</button>
					<button
						type="button"
						onClick={onConfirm}
						className={`px-4 py-2 text-sm font-medium rounded-md ${confirmButtonStyles[variant]}`}
					>
						{confirmLabel}
					</button>
				</div>
			</div>
		</div>
	);
}
