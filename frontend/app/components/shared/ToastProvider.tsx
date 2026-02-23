"use client";

import { createContext, useCallback, useState, type ReactNode } from "react";
import { Toast } from "./Toast";
import type { ToastItem, ToastVariant } from "@/hooks/useToast";

interface ToastContextValue {
	success: (message: string) => void;
	error: (message: string) => void;
	warning: (message: string) => void;
	info: (message: string) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
	const [toasts, setToasts] = useState<ToastItem[]>([]);

	const addToast = useCallback((message: string, variant: ToastVariant) => {
		const id = crypto.randomUUID();
		setToasts((prev) => [...prev, { id, message, variant }]);
		setTimeout(() => {
			setToasts((prev) => prev.filter((t) => t.id !== id));
		}, 3000);
	}, []);

	const removeToast = useCallback((id: string) => {
		setToasts((prev) => prev.filter((t) => t.id !== id));
	}, []);

	const value: ToastContextValue = {
		success: useCallback((msg: string) => addToast(msg, "success"), [addToast]),
		error: useCallback((msg: string) => addToast(msg, "error"), [addToast]),
		warning: useCallback((msg: string) => addToast(msg, "warning"), [addToast]),
		info: useCallback((msg: string) => addToast(msg, "info"), [addToast]),
	};

	return (
		<ToastContext.Provider value={value}>
			{children}
			<div className="fixed top-4 right-4 z-50 flex flex-col gap-2" aria-live="polite">
				{toasts.map((toast) => (
					<Toast key={toast.id} toast={toast} onClose={() => removeToast(toast.id)} />
				))}
			</div>
		</ToastContext.Provider>
	);
}
