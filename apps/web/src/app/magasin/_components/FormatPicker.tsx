"use client";

import type { BoxFormatResponse } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { spineLabel } from "./useBoxFormats";

/** Px par mm : une boîte dos 10 × 33 cm mesure 22 × 73 px. */
const SCALE = 0.22;

/**
 * Boîte vue de face, côté dos, à l'échelle : on compare les formats d'un coup d'œil
 * (même échelle pour tous, donc un dos 15 est visiblement 1,5 fois plus large qu'un dos 10).
 */
export function BoxSpine({ spineMm, heightMm, retired = false, scale = SCALE }: {
  spineMm: number; heightMm: number; retired?: boolean; scale?: number;
}) {
  const w = Math.max(8, spineMm * scale);
  const h = Math.max(20, heightMm * scale);
  const side = Math.min(10, w * 0.35);
  const id = `spine-${spineMm}-${heightMm}-${retired ? "r" : "a"}`;
  const [light, mid, dark] = retired ? ["#e7e3da", "#cfc8b8", "#b3ab99"] : ["#e9cb9d", "#c99b62", "#a77942"];
  return (
    <svg width={w + side} height={h + side} viewBox={`0 0 ${w + side} ${h + side}`} aria-hidden>
      <defs>
        <linearGradient id={id} x1="0" x2="1" y1="0" y2="0">
          <stop offset="0" stopColor={light} />
          <stop offset="1" stopColor={mid} />
        </linearGradient>
      </defs>
      {/* dessus et flanc pour donner du volume */}
      <polygon points={`0,${side} ${side},0 ${w + side},0 ${w},${side}`} fill={light} />
      <polygon points={`${w},${side} ${w + side},0 ${w + side},${h} ${w},${h + side}`} fill={dark} />
      {/* face dos */}
      <rect x="0" y={side} width={w} height={h} fill={`url(#${id})`} />
      {/* étiquette */}
      {w >= 12 && (
        <rect x={w * 0.18} y={side + h * 0.12} width={w * 0.64} height={h * 0.3} rx="1.5" fill="#fbf6ec" opacity="0.95" />
      )}
      {/* trou de préhension */}
      <ellipse cx={w / 2} cy={side + h * 0.78} rx={Math.min(5, w * 0.22)} ry="3" fill="rgba(0,0,0,0.28)" />
    </svg>
  );
}

/** Choix d'un format par ses boîtes dessinées à l'échelle, sans liste déroulante. */
export function FormatPicker({ formats, value, onChange }: {
  formats: BoxFormatResponse[];
  value: string | null;
  onChange: (id: string) => void;
}) {
  return (
    <div role="radiogroup" style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "stretch" }}>
      {formats.map((f) => {
        const selected = f.id === value;
        return (
          <button key={f.id} type="button" role="radio" aria-checked={selected} onClick={() => onChange(f.id)}
            title={f.label}
            style={{
              position: "relative", minWidth: 92, padding: "12px 12px 10px",
              display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "flex-end", gap: 8,
              borderRadius: "var(--radius-md)", cursor: "pointer",
              border: selected ? "2px solid var(--brand-500)" : "1px solid var(--border-subtle)",
              background: selected
                ? "color-mix(in srgb, var(--brand-500) 8%, var(--surface-card))"
                : "var(--surface-card)",
              boxShadow: selected ? "var(--shadow-sm)" : "none",
              color: "var(--text-primary)",
            }}>
            {selected && (
              <span style={{ position: "absolute", top: 6, right: 6, color: "var(--brand-500)" }}>
                <Icons.success size={16} weight="fill" />
              </span>
            )}
            {f.isDefault && (
              <span title="Format par défaut" style={{ position: "absolute", top: 6, left: 6, color: "var(--kraft-500)" }}>
                <Icons.makeDefault size={14} weight="fill" />
              </span>
            )}
            <BoxSpine spineMm={f.spineMm} heightMm={f.heightMm} />
            <div style={{ fontWeight: 700, fontSize: 13 }}>{spineLabel(f.spineMm)}</div>
            <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>
              {f.depthMm / 10} × {f.heightMm / 10} cm
            </div>
          </button>
        );
      })}
    </div>
  );
}
