"use client";

import type { ReactNode } from "react";

export function KpiCard({
  label,
  value,
  icon,
  tone = "neutral",
  delta,
  href,
}: {
  label: string;
  value: number | string;
  icon?: ReactNode;
  tone?: "neutral" | "brand" | "success" | "warning" | "danger" | "info";
  delta?: { value: number; label: string; positive?: boolean };
  href?: string;
}) {
  const toneColor = tone === "neutral" ? "var(--text-primary)" : `var(--${tone === "brand" ? "brand" : tone}-500)`;
  const bg = tone === "neutral" ? "var(--surface-card)" : `var(--${tone === "brand" ? "brand" : tone}-50)`;
  const Component: any = href ? "a" : "div";
  return (
    <Component
      href={href}
      style={{
        display: "block",
        background: "var(--surface-card)",
        border: "1px solid var(--border-subtle)",
        borderRadius: "var(--radius-lg)",
        padding: 16,
        boxShadow: "var(--shadow-sm)",
        textDecoration: "none",
        color: "inherit",
        transition: "box-shadow 150ms ease, transform 150ms ease",
        cursor: href ? "pointer" : "default",
      }}
      onMouseEnter={href ? (e: any) => {
        e.currentTarget.style.boxShadow = "var(--shadow-md)";
        e.currentTarget.style.transform = "translateY(-1px)";
      } : undefined}
      onMouseLeave={href ? (e: any) => {
        e.currentTarget.style.boxShadow = "var(--shadow-sm)";
        e.currentTarget.style.transform = "translateY(0)";
      } : undefined}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 10 }}>
        <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)" }}>
          {label}
        </div>
        {icon && (
          <div style={{
            display: "inline-flex", padding: 6,
            background: bg, color: toneColor,
            borderRadius: "var(--radius-md)",
          }}>{icon}</div>
        )}
      </div>
      <div style={{ fontSize: 28, fontWeight: 700, color: toneColor, lineHeight: 1.1, letterSpacing: "-0.02em" }}>
        {value}
      </div>
      {delta && (
        <div style={{ marginTop: 6, fontSize: 12, color: delta.positive ? "var(--success-500)" : "var(--danger-500)" }}>
          {delta.positive ? "↑" : "↓"} {delta.value} {delta.label}
        </div>
      )}
    </Component>
  );
}
