"use client";

import type { ReactNode } from "react";
import { Icons } from "@/lib/icons";

type Tone = "info" | "success" | "warning" | "danger";

const MAP = {
  info:    { icon: Icons.info,    bg: "var(--info-50)",    fg: "var(--info-700)",    bd: "var(--info-500)" },
  success: { icon: Icons.success, bg: "var(--success-50)", fg: "var(--success-700)", bd: "var(--success-500)" },
  warning: { icon: Icons.warning, bg: "var(--warning-50)", fg: "var(--warning-700)", bd: "var(--warning-500)" },
  danger:  { icon: Icons.error,   bg: "var(--danger-50)",  fg: "var(--danger-700)",  bd: "var(--danger-500)" },
} as const;

export function Alert({
  tone = "info",
  title,
  children,
  action,
}: {
  tone?: Tone;
  title?: ReactNode;
  children?: ReactNode;
  action?: ReactNode;
}) {
  const s = MAP[tone];
  const IconCmp = s.icon;
  return (
    <div role={tone === "danger" ? "alert" : "status"} style={{
      display: "flex", gap: 12, alignItems: "flex-start",
      padding: "12px 14px",
      background: s.bg,
      border: `1px solid color-mix(in srgb, ${s.bd} 30%, transparent)`,
      borderRadius: "var(--radius-md)",
      color: s.fg,
      fontSize: 13,
    }}>
      <IconCmp size={20} weight="duotone" style={{ flexShrink: 0, marginTop: 1 }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        {title && <div style={{ fontWeight: 600, marginBottom: children ? 2 : 0 }}>{title}</div>}
        {children && <div style={{ color: "var(--text-secondary)" }}>{children}</div>}
      </div>
      {action}
    </div>
  );
}
