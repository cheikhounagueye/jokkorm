"use client";

import { BOX_FINISHES } from "@/components/illustrations/ArchiveBoxIso";
import type { ColorMode } from "./sceneShared";

const REAL = [
  { color: BOX_FINISHES.kraft.left, label: "Active" },
  { color: BOX_FINISHES.sealed.left, label: "Scellée" },
  { color: BOX_FINISHES.empty.left, label: "Vide" },
  { color: BOX_FINISHES.loaned.left, label: "En prêt", ghost: true },
];

const OCCUPANCY = [
  { color: "#22c55e", label: "< 60 %" },
  { color: "#f59e0b", label: "60 – 90 %" },
  { color: "#ef4444", label: "≥ 90 %" },
  { color: "#cbd5e1", label: "Vide" },
];

export function Legend({ mode }: { mode: ColorMode }) {
  const items = mode === "real" ? REAL : OCCUPANCY;
  return (
    <div style={{
      position: "absolute", left: 12, bottom: 12, zIndex: 2,
      display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap",
      padding: "7px 12px", borderRadius: "var(--radius-md)",
      background: "color-mix(in srgb, var(--surface-card) 92%, transparent)",
      border: "1px solid var(--border-subtle)", boxShadow: "var(--shadow-sm)",
      fontSize: 11, fontWeight: 500, color: "var(--text-secondary)",
    }}>
      <span style={{ fontWeight: 700, color: "var(--text-primary)" }}>{mode === "real" ? "Boîtes" : "Occupation"}</span>
      {items.map((i) => (
        <span key={i.label} style={{ display: "inline-flex", alignItems: "center", gap: 5 }}>
          <span style={{
            width: 11, height: 11, borderRadius: 3, background: i.color,
            opacity: "ghost" in i && i.ghost ? 0.45 : 1, border: "1px solid rgba(15,23,42,0.15)",
          }} />
          {i.label}
        </span>
      ))}
    </div>
  );
}
