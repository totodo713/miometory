"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { formatDateJapanese } from "@/lib/date-format";
import { ApiError, api } from "@/services/api";

interface EntryRow {
  entryId: string;
  projectCode: string;
  projectName: string;
  hours: number;
  comment: string | null;
  approvalId: string | null;
  approvalStatus: string | null;
  approvalComment: string | null;
}

interface MemberEntryGroup {
  memberId: string;
  memberName: string;
  entries: EntryRow[];
}

interface DailyGroup {
  date: string;
  members: MemberEntryGroup[];
}

interface DailyApprovalDashboardProps {
  refreshKey: number;
  onRefresh: () => void;
}

export function DailyApprovalDashboard({ refreshKey, onRefresh }: DailyApprovalDashboardProps) {
  const [groups, setGroups] = useState<DailyGroup[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedEntries, setSelectedEntries] = useState<Set<string>>(new Set());
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  // Reject modal state
  const [rejectingEntryId, setRejectingEntryId] = useState<string | null>(null);
  const [rejectComment, setRejectComment] = useState("");

  const loadEntries = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await api.dailyApproval.getEntries({
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
      });
      setGroups(result);
    } catch {
      setGroups([]);
    } finally {
      setIsLoading(false);
    }
  }, [dateFrom, dateTo]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey triggers re-fetch from parent
  useEffect(() => {
    loadEntries();
  }, [loadEntries, refreshKey]);

  // Summary statistics computed from loaded data
  const summary = useMemo(() => {
    let pending = 0;
    let approved = 0;
    let rejected = 0;
    for (const group of groups) {
      for (const member of group.members) {
        for (const entry of member.entries) {
          if (!entry.approvalStatus) pending++;
          else if (entry.approvalStatus === "APPROVED") approved++;
          else if (entry.approvalStatus === "REJECTED") rejected++;
        }
      }
    }
    return { pending, approved, rejected };
  }, [groups]);

  const toggleEntry = (entryId: string) => {
    setSelectedEntries((prev) => {
      const next = new Set(prev);
      if (next.has(entryId)) {
        next.delete(entryId);
      } else {
        next.add(entryId);
      }
      return next;
    });
  };

  const handleBulkApprove = async () => {
    if (selectedEntries.size === 0) return;
    setError(null);
    try {
      await api.dailyApproval.approve({ entryIds: Array.from(selectedEntries) });
      setSelectedEntries(new Set());
      onRefresh();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("エラーが発生しました");
      }
    }
  };

  const handleReject = async () => {
    if (!rejectingEntryId || !rejectComment.trim()) return;
    setError(null);
    try {
      await api.dailyApproval.reject({ entryId: rejectingEntryId, comment: rejectComment });
      setRejectingEntryId(null);
      setRejectComment("");
      onRefresh();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("エラーが発生しました");
      }
    }
  };

  const handleRecall = async (approvalId: string) => {
    setError(null);
    try {
      await api.dailyApproval.recall(approvalId);
      onRefresh();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("エラーが発生しました");
      }
    }
  };

  const isUnapproved = (entry: EntryRow) => !entry.approvalStatus;

  return (
    <div className="space-y-5">
      {/* Filter bar */}
      <div className="flex flex-wrap items-end gap-4">
        <div>
          <label htmlFor="date-from" className="block text-sm font-medium text-gray-700 mb-1">
            開始日
          </label>
          <input
            id="date-from"
            type="date"
            value={dateFrom}
            onChange={(e) => setDateFrom(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div>
          <label htmlFor="date-to" className="block text-sm font-medium text-gray-700 mb-1">
            終了日
          </label>
          <input
            id="date-to"
            type="date"
            value={dateTo}
            onChange={(e) => setDateTo(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>

      {/* Summary statistics cards */}
      {!isLoading && groups.length > 0 && (
        <div className="grid grid-cols-3 gap-4">
          <div
            data-testid="summary-pending"
            className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 text-center"
          >
            <p className="text-2xl font-bold text-amber-700">{summary.pending}</p>
            <p className="text-xs font-medium text-amber-600 mt-0.5">未承認</p>
          </div>
          <div
            data-testid="summary-approved"
            className="bg-green-50 border border-green-200 rounded-lg px-4 py-3 text-center"
          >
            <p className="text-2xl font-bold text-green-700">{summary.approved}</p>
            <p className="text-xs font-medium text-green-600 mt-0.5">承認済</p>
          </div>
          <div
            data-testid="summary-rejected"
            className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-center"
          >
            <p className="text-2xl font-bold text-red-700">{summary.rejected}</p>
            <p className="text-xs font-medium text-red-600 mt-0.5">差戻</p>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="text-center py-12 text-gray-500">読み込み中...</div>
      ) : groups.length === 0 ? (
        <div className="text-center py-12 text-gray-400">
          <svg
            className="mx-auto h-10 w-10 mb-3"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            role="img"
            aria-label="データなし"
          >
            <title>データなし</title>
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1.5}
              d="M9 12h6m-3-3v6m-7 4h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"
            />
          </svg>
          <p className="text-sm">承認待ちの記録はありません</p>
        </div>
      ) : (
        <div className="space-y-6">
          {groups.map((group) => (
            <div key={group.date} className="border border-gray-200 rounded-lg overflow-hidden">
              {/* Date header */}
              <div className="bg-gray-100 border-l-4 border-l-blue-500 px-4 py-2.5">
                <h3 className="text-base font-semibold text-gray-800">{formatDateJapanese(group.date)}</h3>
              </div>

              {/* Member sections */}
              {group.members.map((member, memberIdx) => (
                <div key={member.memberId} className={`px-4 py-3 ${memberIdx > 0 ? "border-t border-gray-200" : ""}`}>
                  <h4 className="text-sm font-semibold text-gray-700 mb-2 flex items-center gap-2">
                    <span className="inline-block w-1.5 h-1.5 rounded-full bg-blue-400" />
                    {member.memberName}
                  </h4>

                  <div className="overflow-x-auto">
                    <table className="w-full text-sm min-w-[640px]">
                      <thead>
                        <tr className="border-b border-gray-200">
                          <th className="w-8 py-2 px-1" />
                          <th className="text-left py-2 px-3 font-medium text-gray-500 w-24">コード</th>
                          <th className="text-left py-2 px-3 font-medium text-gray-500">プロジェクト</th>
                          <th className="text-right py-2 px-3 font-medium text-gray-500 w-20">時間</th>
                          <th className="text-left py-2 px-3 font-medium text-gray-500 w-48">コメント</th>
                          <th className="text-center py-2 px-3 font-medium text-gray-500 w-24">状態</th>
                          <th className="text-right py-2 px-3 font-medium text-gray-500 w-24">操作</th>
                        </tr>
                      </thead>
                      <tbody>
                        {member.entries.map((entry) => (
                          <tr key={entry.entryId} className="border-b border-gray-50 hover:bg-gray-50/50">
                            <td className="py-2 px-1">
                              {isUnapproved(entry) && (
                                <input
                                  type="checkbox"
                                  checked={selectedEntries.has(entry.entryId)}
                                  onChange={() => toggleEntry(entry.entryId)}
                                  aria-label={`${entry.projectName}の承認を選択`}
                                  className="rounded border-gray-300"
                                />
                              )}
                            </td>
                            <td className="py-2 px-3">
                              <span className="font-mono text-xs text-gray-500">{entry.projectCode}</span>
                            </td>
                            <td className="py-2 px-3 text-gray-800">{entry.projectName}</td>
                            <td className="py-2 px-3 text-right font-medium tabular-nums">{entry.hours}h</td>
                            <td className="py-2 px-3">
                              {entry.comment ? (
                                <span className="block truncate max-w-[12rem] text-gray-600" title={entry.comment}>
                                  {entry.comment}
                                </span>
                              ) : (
                                <span className="text-gray-300">—</span>
                              )}
                            </td>
                            <td className="py-2 px-3 text-center">
                              {entry.approvalStatus ? (
                                <span
                                  className={`inline-block px-2.5 py-1 rounded-full text-xs font-medium ${
                                    entry.approvalStatus === "APPROVED"
                                      ? "bg-green-100 text-green-800"
                                      : entry.approvalStatus === "REJECTED"
                                        ? "bg-red-100 text-red-800"
                                        : "bg-gray-100 text-gray-600"
                                  }`}
                                  title={
                                    entry.approvalStatus === "REJECTED" && entry.approvalComment
                                      ? entry.approvalComment
                                      : undefined
                                  }
                                >
                                  {entry.approvalStatus === "APPROVED"
                                    ? "承認済"
                                    : entry.approvalStatus === "REJECTED"
                                      ? "差戻"
                                      : entry.approvalStatus}
                                </span>
                              ) : (
                                <span className="inline-block px-2.5 py-1 rounded-full text-xs font-medium bg-amber-100 text-amber-800">
                                  未承認
                                </span>
                              )}
                            </td>
                            <td className="py-2 px-3 text-right">
                              <div className="flex justify-end gap-1.5">
                                {isUnapproved(entry) && (
                                  <button
                                    type="button"
                                    onClick={() => setRejectingEntryId(entry.entryId)}
                                    className="px-3 py-1.5 text-xs font-medium text-red-700 border border-red-200 rounded-md hover:bg-red-50 focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-1"
                                  >
                                    差戻
                                  </button>
                                )}
                                {entry.approvalStatus === "APPROVED" && entry.approvalId && (
                                  <button
                                    type="button"
                                    onClick={() => handleRecall(entry.approvalId as string)}
                                    className="px-3 py-1.5 text-xs font-medium text-orange-700 border border-orange-200 rounded-md hover:bg-orange-50 focus:outline-none focus:ring-2 focus:ring-orange-500 focus:ring-offset-1"
                                  >
                                    取消
                                  </button>
                                )}
                              </div>
                            </td>
                          </tr>
                        ))}

                        {/* Subtotal row */}
                        <tr className="bg-gray-50/50">
                          <td colSpan={3} />
                          <td className="py-2 px-3 text-right font-semibold text-gray-700 tabular-nums">
                            合計: {parseFloat(member.entries.reduce((sum, e) => sum + e.hours, 0).toFixed(2))}h
                          </td>
                          <td colSpan={3} />
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>
      )}

      {/* Floating action bar */}
      {(selectedEntries.size > 0 || error) && (
        <div className="fixed bottom-0 left-0 right-0 z-40 border-t border-gray-200 bg-white/95 backdrop-blur-sm shadow-[0_-2px_8px_rgba(0,0,0,0.08)]">
          <div className="mx-auto px-6 py-3 space-y-2">
            {error && (
              <div
                role="alert"
                className="flex items-center justify-between gap-3 rounded-md bg-red-50 border border-red-200 px-3 py-2"
              >
                <p className="text-sm text-red-700">{error}</p>
                <button
                  type="button"
                  onClick={() => setError(null)}
                  className="shrink-0 text-red-400 hover:text-red-600"
                  aria-label="エラーを閉じる"
                >
                  <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor" role="img" aria-label="閉じる">
                    <title>閉じる</title>
                    <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
                  </svg>
                </button>
              </div>
            )}
            {selectedEntries.size > 0 && (
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-600">{selectedEntries.size}件選択中</span>
                <button
                  type="button"
                  onClick={handleBulkApprove}
                  className="px-5 py-2.5 text-sm font-medium text-white bg-green-600 rounded-md hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-green-500 focus:ring-offset-2"
                >
                  選択した{selectedEntries.size}件を承認
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Reject modal */}
      {rejectingEntryId && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">差戻コメント</h2>
            <textarea
              value={rejectComment}
              onChange={(e) => setRejectComment(e.target.value)}
              placeholder="差戻理由を入力してください（必須）"
              rows={3}
              aria-label="差戻コメント"
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <div className="flex justify-end gap-3 mt-4">
              <button
                type="button"
                onClick={() => {
                  setRejectingEntryId(null);
                  setRejectComment("");
                }}
                className="px-4 py-2 text-sm font-medium text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-gray-400"
              >
                キャンセル
              </button>
              <button
                type="button"
                onClick={handleReject}
                disabled={!rejectComment.trim()}
                className="px-4 py-2 text-sm font-medium text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-red-500"
              >
                差戻
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
