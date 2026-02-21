"use client";

import { useCallback, useEffect, useState } from "react";
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

  useEffect(() => {
    loadEntries();
  }, [loadEntries, refreshKey]);

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
    <div className="space-y-4">
      {/* Date filter */}
      <div className="flex items-end gap-4">
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
        {selectedEntries.size > 0 && (
          <button
            type="button"
            onClick={handleBulkApprove}
            className="px-4 py-2 text-sm text-white bg-green-600 rounded-md hover:bg-green-700"
          >
            選択した{selectedEntries.size}件を承認
          </button>
        )}
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {isLoading ? (
        <div className="text-center py-8 text-gray-500">読み込み中...</div>
      ) : groups.length === 0 ? (
        <div className="text-center py-8 text-gray-500">承認待ちの記録はありません</div>
      ) : (
        <div className="space-y-6">
          {groups.map((group) => (
            <div key={group.date} className="border border-gray-200 rounded-lg">
              <div className="bg-gray-50 px-4 py-2 border-b border-gray-200">
                <h3 className="text-sm font-semibold text-gray-700">{group.date}</h3>
              </div>
              {group.members.map((member) => (
                <div key={member.memberId} className="p-4 border-b border-gray-100 last:border-b-0">
                  <h4 className="text-sm font-medium text-gray-800 mb-2">{member.memberName}</h4>
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-100">
                        <th className="w-8 py-2" />
                        <th className="text-left py-2 font-medium text-gray-600">プロジェクト</th>
                        <th className="text-right py-2 font-medium text-gray-600 w-20">時間</th>
                        <th className="text-left py-2 font-medium text-gray-600">コメント</th>
                        <th className="text-center py-2 font-medium text-gray-600 w-24">状態</th>
                        <th className="text-right py-2 font-medium text-gray-600 w-32">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {member.entries.map((entry) => (
                        <tr key={entry.entryId} className="border-b border-gray-50">
                          <td className="py-2">
                            {isUnapproved(entry) && (
                              <input
                                type="checkbox"
                                checked={selectedEntries.has(entry.entryId)}
                                onChange={() => toggleEntry(entry.entryId)}
                                aria-label={`${entry.projectName}の承認を選択`}
                              />
                            )}
                          </td>
                          <td className="py-2">
                            <span className="font-mono text-xs">{entry.projectCode}</span> {entry.projectName}
                          </td>
                          <td className="py-2 text-right">{entry.hours}h</td>
                          <td className="py-2 text-gray-600 text-xs">{entry.comment || "—"}</td>
                          <td className="py-2 text-center">
                            {entry.approvalStatus ? (
                              <span
                                className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                                  entry.approvalStatus === "APPROVED"
                                    ? "bg-green-100 text-green-800"
                                    : entry.approvalStatus === "REJECTED"
                                      ? "bg-red-100 text-red-800"
                                      : "bg-gray-100 text-gray-600"
                                }`}
                              >
                                {entry.approvalStatus === "APPROVED"
                                  ? "承認済"
                                  : entry.approvalStatus === "REJECTED"
                                    ? "差戻"
                                    : entry.approvalStatus}
                              </span>
                            ) : (
                              <span className="inline-block px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                                未承認
                              </span>
                            )}
                          </td>
                          <td className="py-2 text-right">
                            <div className="flex justify-end gap-2">
                              {isUnapproved(entry) && (
                                <button
                                  type="button"
                                  onClick={() => setRejectingEntryId(entry.entryId)}
                                  className="text-red-600 hover:text-red-800 text-xs"
                                >
                                  差戻
                                </button>
                              )}
                              {entry.approvalStatus === "APPROVED" && entry.approvalId && (
                                <button
                                  type="button"
                                  onClick={() => handleRecall(entry.approvalId as string)}
                                  className="text-orange-600 hover:text-orange-800 text-xs"
                                >
                                  取消
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ))}
            </div>
          ))}
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
                className="px-4 py-2 text-sm text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50"
              >
                キャンセル
              </button>
              <button
                type="button"
                onClick={handleReject}
                disabled={!rejectComment.trim()}
                className="px-4 py-2 text-sm text-white bg-red-600 rounded-md hover:bg-red-700 disabled:opacity-50"
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
