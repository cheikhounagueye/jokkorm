"use client";

import type { ReactNode } from "react";

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div style={{
      textAlign: "center",
      padding: "48px 24px",
      background: "var(--surface-sunken)",
      border: "1px dashed var(--border-subtle)",
      borderRadius: "var(--radius-lg)",
    }}>
      {icon && (
        <div style={{ display: "inline-flex", padding: 12, background: "var(--surface-card)", borderRadius: "var(--radius-full)", marginBottom: 12, color: "var(--text-tertiary)" }}>
          {icon}
        </div>
      )}
      <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text-primary)", marginBottom: description ? 4 : 0 }}>{title}</div>
      {description && (
        <div style={{ fontSize: 13, color: "var(--text-secondary)", maxWidth: 360, margin: "0 auto" }}>{description}</div>
      )}
      {action && <div style={{ marginTop: 16 }}>{action}</div>}
    </div>
  );
}
