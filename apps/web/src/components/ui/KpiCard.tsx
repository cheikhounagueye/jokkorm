"use client";

import Link from "next/link";
import type { IconName } from "@/lib/icons";
import { IconTile } from "./IconTile";

type KpiTone = "neutral" | "brand" | "success" | "warning" | "danger" | "info";

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
  icon?: IconName;
  tone?: KpiTone;
  delta?: { value: number; label: string; positive?: boolean };
  href?: string;
}) {
  const toneColor = tone === "neutral" ? "var(--text-primary)" : `var(--${tone}-500)`;
  const tileTone = tone === "neutral" ? "slate" : tone;

  const body = (
    <>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 14 }}>
        {icon && <IconTile name={icon} tone={tileTone} size={40} />}
        {delta && (
          <span style={{
            fontSize: 11, fontWeight: 600,
            color: delta.positive ? "var(--success-700)" : "var(--danger-700)",
            background: delta.positive ? "var(--success-50)" : "var(--danger-50)",
            padding: "2px 8px", borderRadius: "var(--radius-full)",
          }}>
            {delta.positive ? "↑" : "↓"} {delta.value} {delta.label}
          </span>
        )}
      </div>
      <div style={{ fontSize: 30, fontWeight: 700, color: toneColor, lineHeight: 1.1, letterSpacing: "-0.03em", marginBottom: 4 }}>
        {value}
      </div>
      <div style={{ fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", textTransform: "uppercase", letterSpacing: "0.05em" }}>
        {label}
      </div>
    </>
  );

  const style: React.CSSProperties = {
    display: "block",
    background: "var(--surface-card)",
    border: "1px solid var(--border-strong)",
    borderRadius: "var(--radius-lg)",
    padding: "18px 20px",
    boxShadow: "var(--shadow-md)",
    color: "inherit",
    transition: "box-shadow 150ms ease, transform 150ms ease, border-color 150ms ease",
  };

  if (!href) return <div style={style}>{body}</div>;
  return (
    <Link href={href} style={style}
      onMouseEnter={(e) => {
        e.currentTarget.style.boxShadow = "var(--shadow-lg)";
        e.currentTarget.style.transform = "translateY(-2px)";
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.boxShadow = "var(--shadow-md)";
        e.currentTarget.style.transform = "translateY(0)";
      }}>
      {body}
    </Link>
  );
}
