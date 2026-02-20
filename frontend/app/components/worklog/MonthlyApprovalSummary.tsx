"use client";

interface ProjectBreakdown {
  projectCode: string;
  projectName: string;
  hours: number;
}

interface DailyApprovalSummary {
  approvedCount: number;
  rejectedCount: number;
  unapprovedCount: number;
}

interface UnresolvedEntry {
  entryId: string;
  date: string;
  projectCode: string;
  rejectionComment: string;
}

interface MonthlyApprovalSummaryProps {
  memberName: string;
  fiscalMonthStart: string;
  fiscalMonthEnd: string;
  totalWorkHours: number;
  totalAbsenceHours: number;
  projectBreakdown: ProjectBreakdown[];
  dailyApprovalSummary: DailyApprovalSummary;
  unresolvedEntries: UnresolvedEntry[];
  onClose: () => void;
}

export function MonthlyApprovalSummaryView({
  memberName,
  fiscalMonthStart,
  fiscalMonthEnd,
  totalWorkHours,
  totalAbsenceHours,
  projectBreakdown,
  dailyApprovalSummary,
  unresolvedEntries,
  onClose,
}: MonthlyApprovalSummaryProps) {
  const totalDailyEntries =
    dailyApprovalSummary.approvedCount + dailyApprovalSummary.rejectedCount + dailyApprovalSummary.unapprovedCount;
  const totalHours = totalWorkHours + totalAbsenceHours;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">{memberName}</h2>
          <p className="text-sm text-gray-600">
            {fiscalMonthStart} ~ {fiscalMonthEnd}
          </p>
        </div>
        <button type="button" onClick={onClose} className="text-sm text-gray-500 hover:text-gray-700">
          閉じる
        </button>
      </div>

      {/* Hours summary */}
      <div className="grid grid-cols-3 gap-4">
        <div className="bg-blue-50 rounded-lg p-4 text-center">
          <p className="text-sm text-blue-600">勤務時間</p>
          <p className="text-2xl font-bold text-blue-800">{totalWorkHours.toFixed(2)}h</p>
        </div>
        <div className="bg-orange-50 rounded-lg p-4 text-center">
          <p className="text-sm text-orange-600">休暇時間</p>
          <p className="text-2xl font-bold text-orange-800">{totalAbsenceHours.toFixed(2)}h</p>
        </div>
        <div className="bg-gray-50 rounded-lg p-4 text-center">
          <p className="text-sm text-gray-600">合計</p>
          <p className="text-2xl font-bold text-gray-800">{totalHours.toFixed(2)}h</p>
        </div>
      </div>

      {/* Daily approval status bar */}
      {totalDailyEntries > 0 && (
        <div>
          <h3 className="text-sm font-medium text-gray-700 mb-2">日次承認状況</h3>
          <div className="flex h-4 rounded-full overflow-hidden">
            {dailyApprovalSummary.approvedCount > 0 && (
              <div
                className="bg-green-500"
                style={{ width: `${(dailyApprovalSummary.approvedCount / totalDailyEntries) * 100}%` }}
              />
            )}
            {dailyApprovalSummary.rejectedCount > 0 && (
              <div
                className="bg-red-500"
                style={{ width: `${(dailyApprovalSummary.rejectedCount / totalDailyEntries) * 100}%` }}
              />
            )}
            {dailyApprovalSummary.unapprovedCount > 0 && (
              <div
                className="bg-gray-300"
                style={{ width: `${(dailyApprovalSummary.unapprovedCount / totalDailyEntries) * 100}%` }}
              />
            )}
          </div>
          <div className="flex gap-4 mt-1 text-xs text-gray-600">
            <span className="flex items-center gap-1">
              <span className="w-3 h-3 rounded-full bg-green-500 inline-block" />
              承認済 {dailyApprovalSummary.approvedCount}件
            </span>
            <span className="flex items-center gap-1">
              <span className="w-3 h-3 rounded-full bg-red-500 inline-block" />
              差戻 {dailyApprovalSummary.rejectedCount}件
            </span>
            <span className="flex items-center gap-1">
              <span className="w-3 h-3 rounded-full bg-gray-300 inline-block" />
              未承認 {dailyApprovalSummary.unapprovedCount}件
            </span>
          </div>
        </div>
      )}

      {/* Unresolved rejections warning */}
      {unresolvedEntries.length > 0 && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4">
          <h3 className="text-sm font-medium text-red-800 mb-2">未解決の日次差戻 ({unresolvedEntries.length}件)</h3>
          <ul className="space-y-1">
            {unresolvedEntries.map((entry) => (
              <li key={entry.entryId} className="text-sm text-red-700">
                {entry.date} - {entry.projectCode}: {entry.rejectionComment}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Project breakdown */}
      {projectBreakdown.length > 0 && (
        <div>
          <h3 className="text-sm font-medium text-gray-700 mb-2">プロジェクト内訳</h3>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="text-left py-2 font-medium text-gray-600">コード</th>
                <th className="text-left py-2 font-medium text-gray-600">プロジェクト名</th>
                <th className="text-right py-2 font-medium text-gray-600">時間</th>
                <th className="text-right py-2 font-medium text-gray-600">割合</th>
              </tr>
            </thead>
            <tbody>
              {projectBreakdown.map((project) => (
                <tr key={project.projectCode} className="border-b border-gray-50">
                  <td className="py-2 font-mono text-xs">{project.projectCode}</td>
                  <td className="py-2">{project.projectName}</td>
                  <td className="py-2 text-right">{project.hours.toFixed(2)}h</td>
                  <td className="py-2 text-right">
                    {totalWorkHours > 0 ? ((project.hours / totalWorkHours) * 100).toFixed(1) : 0}%
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
