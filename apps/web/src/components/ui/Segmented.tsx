"use client";

import type { ReactNode } from "react";

/** Contrôle segmenté (onglets compacts). */
export function Segmented<T extends string>({
  value,
  onChange,
  options,
}: {
  value: T;
  onChange: (v: T) => void;
  options: { value: T; label: ReactNode; icon?: ReactNode }[];
}) {
  return (
    <div role="tablist" style={{
      display: "inline-flex", padding: 3, gap: 2,
      background: "var(--surface-sunken)",
      border: "1px solid var(--border-subtle)",
      borderRadius: "var(--radius-md)",
    }}>
      {options.map((o) => {
        const active = o.value === value;
        return (
          <button key={o.value} type="button" role="tab" aria-selected={active}
            onClick={() => onChange(o.value)}
            style={{
              display: "inline-flex", alignItems: "center", gap: 6,
              padding: "6px 12px", fontSize: 13, fontWeight: active ? 600 : 500,
              border: "none", borderRadius: "var(--radius-sm)",
              background: active ? "var(--surface-card)" : "transparent",
              color: active ? "var(--text-primary)" : "var(--text-secondary)",
              boxShadow: active ? "var(--shadow-sm)" : "none",
            }}>
            {o.icon}
            {o.label}
          </button>
        );
      })}
    </div>
  );
}
