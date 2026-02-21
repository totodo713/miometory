"use client";

import { useCallback, useEffect, useState } from "react";
import type { OrganizationTreeNode } from "@/services/api";
import { api } from "@/services/api";

interface OrganizationTreeProps {
  refreshKey: number;
  onSelectOrg?: (org: OrganizationTreeNode) => void;
}

interface TreeNodeProps {
  node: OrganizationTreeNode;
  onSelectOrg?: (org: OrganizationTreeNode) => void;
  selectedOrgId?: string;
}

function TreeNode({ node, onSelectOrg, selectedOrgId }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = node.children.length > 0;
  const isSelected = selectedOrgId === node.id;

  return (
    <div>
      <div className="flex items-center gap-1" style={{ paddingLeft: `${(node.level - 1) * 1.5 + 0.5}rem` }}>
        {hasChildren ? (
          <button
            type="button"
            onClick={() => setExpanded((prev) => !prev)}
            className="w-5 h-5 flex items-center justify-center text-gray-500 hover:text-gray-700 text-xs flex-shrink-0"
            aria-label={expanded ? "折りたたむ" : "展開する"}
          >
            {expanded ? "\u25BC" : "\u25B6"}
          </button>
        ) : (
          <span className="w-5 h-5 flex-shrink-0" />
        )}

        <button
          type="button"
          onClick={() => onSelectOrg?.(node)}
          className={`flex items-center gap-2 flex-1 py-1.5 px-2 rounded-md cursor-pointer text-left hover:bg-gray-50 ${
            isSelected ? "bg-blue-50 border border-blue-200" : ""
          }`}
        >
          <span className="font-mono text-xs text-gray-500 flex-shrink-0">{node.code}</span>
          <span className="text-sm text-gray-900 truncate">{node.name}</span>

          <span
            className={`inline-block px-1.5 py-0.5 rounded-full text-xs font-medium flex-shrink-0 ${
              node.status === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-600"
            }`}
          >
            {node.status === "ACTIVE" ? "有効" : "無効"}
          </span>

          <span className="text-xs text-gray-500 flex-shrink-0" title="メンバー数">
            {node.memberCount}人
          </span>
        </button>
      </div>

      {expanded && hasChildren && (
        <div>
          {node.children.map((child) => (
            <TreeNode key={child.id} node={child} onSelectOrg={onSelectOrg} selectedOrgId={selectedOrgId} />
          ))}
        </div>
      )}
    </div>
  );
}

export function OrganizationTree({ refreshKey, onSelectOrg }: OrganizationTreeProps) {
  const [tree, setTree] = useState<OrganizationTreeNode[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [showInactive, setShowInactive] = useState(false);
  const [selectedOrgId, setSelectedOrgId] = useState<string | undefined>(undefined);

  const loadTree = useCallback(async () => {
    setIsLoading(true);
    try {
      const result = await api.admin.organizations.getOrganizationTree(showInactive);
      setTree(result);
    } catch {
      // Error handled by API client
    } finally {
      setIsLoading(false);
    }
  }, [showInactive]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: refreshKey triggers reload
  useEffect(() => {
    loadTree();
  }, [loadTree, refreshKey]);

  const handleSelectOrg = useCallback(
    (org: OrganizationTreeNode) => {
      setSelectedOrgId(org.id);
      onSelectOrg?.(org);
    },
    [onSelectOrg],
  );

  return (
    <div>
      <div className="flex items-center gap-4 mb-4">
        <label className="flex items-center gap-2 text-sm text-gray-600 whitespace-nowrap">
          <input type="checkbox" checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />
          無効も表示
        </label>
      </div>

      {isLoading ? (
        <div className="text-center py-8 text-gray-500">読み込み中...</div>
      ) : tree.length === 0 ? (
        <div className="text-center py-8 text-gray-500">組織が見つかりません</div>
      ) : (
        <div className="py-1">
          {tree.map((node) => (
            <TreeNode key={node.id} node={node} onSelectOrg={handleSelectOrg} selectedOrgId={selectedOrgId} />
          ))}
        </div>
      )}
    </div>
  );
}
