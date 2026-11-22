"use client";

import type { ReactNode } from "react";

type Tone = "neutral" | "brand" | "success" | "warning" | "danger" | "info";

const TONE_STYLES: Record<Tone, { bg: string; fg: string }> = {
  neutral: { bg: "var(--surface-sunken)", fg: "var(--text-secondary)" },
  brand:   { bg: "var(--brand-50)",       fg: "var(--brand-500)"   },
  success: { bg: "var(--success-50)",     fg: "var(--success-700)" },
  warning: { bg: "var(--warning-50)",     fg: "var(--warning-700)" },
  danger:  { bg: "var(--danger-50)",      fg: "var(--danger-700)"  },
  info:    { bg: "var(--info-50)",        fg: "var(--info-700)"    },
};

export function Badge({
  children,
  tone = "neutral",
  icon,
  size = "md",
}: {
  children: ReactNode;
  tone?: Tone;
  icon?: ReactNode;
  size?: "sm" | "md";
}) {
  const s = TONE_STYLES[tone];
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 4,
        padding: size === "sm" ? "1px 6px" : "2px 8px",
        background: s.bg,
        color: s.fg,
        borderRadius: "var(--radius-full)",
        fontSize: size === "sm" ? 10 : 11,
        fontWeight: 600,
        textTransform: "uppercase",
        letterSpacing: "0.04em",
        lineHeight: 1.4,
      }}
    >
      {icon}
      {children}
    </span>
  );
}
