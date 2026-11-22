"use client";

import type { CSSProperties, ReactNode } from "react";

export function Card({
  children,
  padding = 20,
  accent,
  interactive,
  style,
  onClick,
}: {
  children: ReactNode;
  padding?: number;
  accent?: "brand" | "success" | "warning" | "danger" | "info";
  interactive?: boolean;
  style?: CSSProperties;
  onClick?: () => void;
}) {
  const accentColor = accent ? `var(--${accent === "brand" ? "brand" : accent}-500)` : undefined;
  return (
    <div
      onClick={onClick}
      style={{
        background: "var(--surface-card)",
        border: "1px solid var(--border-subtle)",
        borderLeft: accent ? `3px solid ${accentColor}` : undefined,
        borderRadius: "var(--radius-lg)",
        padding,
        boxShadow: "var(--shadow-sm)",
        cursor: interactive ? "pointer" : undefined,
        transition: interactive ? "transform 150ms ease, box-shadow 150ms ease, border-color 150ms ease" : undefined,
        ...style,
      }}
      onMouseEnter={interactive ? (e) => {
        e.currentTarget.style.boxShadow = "var(--shadow-md)";
        e.currentTarget.style.borderColor = "var(--border-strong)";
      } : undefined}
      onMouseLeave={interactive ? (e) => {
        e.currentTarget.style.boxShadow = "var(--shadow-sm)";
        e.currentTarget.style.borderColor = "var(--border-subtle)";
      } : undefined}
    >
      {children}
    </div>
  );
}

export function CardHeader({
  title,
  subtitle,
  action,
}: {
  title: ReactNode;
  subtitle?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 16 }}>
      <div>
        <h3 style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)", fontWeight: 600, marginBottom: subtitle ? 4 : 0 }}>
          {title}
        </h3>
        {subtitle && <div style={{ fontSize: 13, color: "var(--text-secondary)" }}>{subtitle}</div>}
      </div>
      {action}
    </div>
  );
}
