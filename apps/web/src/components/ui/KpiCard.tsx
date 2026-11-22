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
  const toneKey = tone === "neutral" ? null : tone === "brand" ? "brand" : tone;
  const toneColor = toneKey ? `var(--${toneKey}-500)` : "var(--text-primary)";
  const toneBg = toneKey ? `var(--${toneKey}-50)` : "var(--surface-sunken)";
  const Component: any = href ? "a" : "div";
  return (
    <Component
      href={href}
      style={{
        display: "block",
        background: "var(--surface-card)",
        border: "1px solid var(--border-strong)",
        borderRadius: "var(--radius-lg)",
        padding: "18px 20px",
        boxShadow: "var(--shadow-md)",
        textDecoration: "none",
        color: "inherit",
        transition: "box-shadow 150ms ease, transform 150ms ease, border-color 150ms ease",
        cursor: href ? "pointer" : "default",
        position: "relative",
        overflow: "hidden",
      }}
      onMouseEnter={href ? (e: any) => {
        e.currentTarget.style.boxShadow = "var(--shadow-md)";
        e.currentTarget.style.transform = "translateY(-2px)";
        e.currentTarget.style.borderColor = toneColor;
      } : undefined}
      onMouseLeave={href ? (e: any) => {
        e.currentTarget.style.boxShadow = "var(--shadow-sm)";
        e.currentTarget.style.transform = "translateY(0)";
        e.currentTarget.style.borderColor = "var(--border-subtle)";
      } : undefined}
    >
      {/* Décor arrière-plan subtil */}
      {toneKey && (
        <div style={{
          position: "absolute", top: -30, right: -30,
          width: 100, height: 100, borderRadius: "50%",
          background: toneBg, opacity: 0.6,
          pointerEvents: "none",
        }} />
      )}
      <div style={{ position: "relative" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
          {icon && (
            <div style={{
              display: "inline-flex", padding: 8,
              background: toneBg, color: toneColor,
              borderRadius: "var(--radius-md)",
              border: `1px solid color-mix(in srgb, ${toneColor} 20%, transparent)`,
            }}>{icon}</div>
          )}
          {delta && (
            <div style={{
              fontSize: 11, fontWeight: 600,
              color: delta.positive ? "var(--success-500)" : "var(--danger-500)",
              background: delta.positive ? "var(--success-50)" : "var(--danger-50)",
              padding: "2px 8px", borderRadius: "var(--radius-full)",
            }}>
              {delta.positive ? "↑" : "↓"} {delta.value} {delta.label}
            </div>
          )}
        </div>
        <div style={{ fontSize: 32, fontWeight: 700, color: toneColor, lineHeight: 1.1, letterSpacing: "-0.03em", marginBottom: 4 }}>
          {value}
        </div>
        <div style={{ fontSize: 12, fontWeight: 500, color: "var(--text-secondary)", textTransform: "uppercase", letterSpacing: "0.05em" }}>
          {label}
        </div>
      </div>
    </Component>
  );
}
