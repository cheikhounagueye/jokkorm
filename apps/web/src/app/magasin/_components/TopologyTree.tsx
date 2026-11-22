"use client";

import { useState } from "react";
import { Alert, Card, EmptyState, IconTile } from "@/components/ui";
import type { StorageLocationTreeResponse } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { CreateChildForm } from "./CreateChildForm";
import { KIND_META } from "./kinds";

export function TopologyTree({
  roots,
  loading,
  error,
  selectedId,
  ancestorIds,
  onSelect,
  onCreated,
}: {
  roots: StorageLocationTreeResponse[] | undefined;
  loading: boolean;
  error: Error | null;
  selectedId: string | null;
  ancestorIds: Set<string>;
  onSelect: (id: string) => void;
  onCreated: () => void;
}) {
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  const toggle = (id: string) => {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  return (
    <Card padding={0} style={{ position: "sticky", top: 72, maxHeight: "calc(100vh - 96px)", display: "flex", flexDirection: "column", overflow: "hidden" }}>
      <div style={{ padding: "14px 16px", borderBottom: "1px solid var(--border-subtle)", display: "flex", alignItems: "center", gap: 10 }}>
        <IconTile name="topology" tone="kraft" size={30} variant="soft" />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13, fontWeight: 600 }}>Topographie</div>
          <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>5 niveaux, du site à la position</div>
        </div>
      </div>

      <div style={{ flex: 1, overflow: "auto", padding: "8px 8px 4px" }}>
        {loading && <div style={{ padding: 16, fontSize: 13, color: "var(--text-tertiary)" }}>Chargement…</div>}
        {error && <div style={{ padding: 8 }}><Alert tone="danger" title="Topographie indisponible">{error.message}</Alert></div>}
        {roots && roots.length === 0 && (
          <EmptyState icon={<Icons.site size={28} />} title="Aucun site"
            description="Crée un premier site ci-dessous, ou importe un fichier Excel." />
        )}
        {roots?.map((n) => (
          <TreeNode key={n.node.id} node={n} depth={0}
            selectedId={selectedId} ancestorIds={ancestorIds}
            collapsed={collapsed} onToggle={toggle} onSelect={onSelect} />
        ))}
      </div>

      <div style={{ borderTop: "1px solid var(--border-subtle)", padding: "12px 14px", background: "var(--surface-sunken)" }}>
        <CreateChildForm parent={null} onCreated={onCreated} compact />
      </div>
    </Card>
  );
}

function TreeNode({
  node, depth, selectedId, ancestorIds, collapsed, onToggle, onSelect,
}: {
  node: StorageLocationTreeResponse;
  depth: number;
  selectedId: string | null;
  ancestorIds: Set<string>;
  collapsed: Set<string>;
  onToggle: (id: string) => void;
  onSelect: (id: string) => void;
}) {
  const meta = KIND_META[node.node.kind];
  const hasChildren = node.children.length > 0;
  // un ancêtre du nœud sélectionné reste toujours déplié
  const open = hasChildren && (!collapsed.has(node.node.id) || ancestorIds.has(node.node.id));
  const selected = selectedId === node.node.id;
  const Chevron = open ? Icons.chevronDown : Icons.chevronRight;

  return (
    <div>
      <div
        role="treeitem"
        aria-selected={selected}
        aria-expanded={hasChildren ? open : undefined}
        style={{
          display: "flex", alignItems: "center", gap: 6,
          padding: "5px 8px 5px 4px",
          paddingLeft: 4 + depth * 16,
          marginBottom: 1,
          borderRadius: "var(--radius-md)",
          background: selected ? "var(--brand-50)" : "transparent",
          boxShadow: selected ? "inset 3px 0 0 var(--brand-500)" : "none",
          cursor: "pointer",
        }}
        onClick={() => onSelect(node.node.id)}
        onMouseEnter={(e) => { if (!selected) e.currentTarget.style.background = "var(--surface-hover)"; }}
        onMouseLeave={(e) => { if (!selected) e.currentTarget.style.background = "transparent"; }}
      >
        <button
          type="button"
          aria-label={open ? "Replier" : "Déplier"}
          onClick={(e) => { e.stopPropagation(); if (hasChildren) onToggle(node.node.id); }}
          style={{
            width: 18, height: 18, padding: 0, border: "none", background: "transparent",
            display: "inline-flex", alignItems: "center", justifyContent: "center",
            color: "var(--text-tertiary)", visibility: hasChildren ? "visible" : "hidden",
          }}
        >
          <Chevron size={12} weight="bold" />
        </button>
        <IconTile name={meta.icon} tone={meta.tone} size={26} variant="soft" />
        <div style={{ flex: 1, minWidth: 0, lineHeight: 1.25 }}>
          <div style={{
            fontSize: 13, fontWeight: selected ? 600 : 500,
            color: selected ? "var(--brand-500)" : "var(--text-primary)",
            overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
          }}>
            {node.node.label || `${meta.label} ${node.node.code}`}
          </div>
          <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>
            {meta.label} · <span style={{ fontFamily: "var(--font-mono)" }}>{node.node.code}</span>
          </div>
        </div>
        {hasChildren && (
          <span style={{
            fontSize: 11, fontWeight: 600, color: "var(--text-secondary)",
            background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)",
            borderRadius: "var(--radius-full)", padding: "0 7px", lineHeight: "18px",
          }}>
            {node.children.length}
          </span>
        )}
      </div>
      {open && node.children.map((c) => (
        <TreeNode key={c.node.id} node={c} depth={depth + 1}
          selectedId={selectedId} ancestorIds={ancestorIds}
          collapsed={collapsed} onToggle={onToggle} onSelect={onSelect} />
      ))}
    </div>
  );
}
