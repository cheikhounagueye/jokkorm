"use client";

import { cm } from "@/lib/api";
import { spineLabel } from "./useBoxFormats";

export interface GaugeSegment {
  key: string;
  spineMm: number;
  title: string;
  onLoan?: boolean;
  active?: boolean;
}

export interface GaugeFit { code: string; spineMm: number; count: number }

/** Teinte par épaisseur de dos : plus la boîte est épaisse, plus le kraft est foncé. */
function spineTone(spineMm: number): string {
  if (spineMm <= 80) return "var(--kraft-300)";
  if (spineMm <= 100) return "var(--kraft-500)";
  if (spineMm <= 150) return "color-mix(in srgb, var(--kraft-500) 60%, var(--kraft-700))";
  return "var(--kraft-700)";
}

/**
 * Tablette à l'échelle (pattern 10) : chaque boîte prend la largeur de son dos, la marge
 * de manipulation est hachurée en bout de tablette, et l'on voit d'un coup d'œil combien
 * de boîtes de chaque format tiennent encore.
 */
export function SpaceGauge({
  lengthMm, usedMm, placeableMm, marginPercent, segments, fits, onSegmentClick,
}: {
  lengthMm: number;
  usedMm: number;
  placeableMm: number;
  marginPercent: number;
  segments: GaugeSegment[];
  fits: GaugeFit[];
  onSegmentClick?: (key: string) => void;
}) {
  const total = Math.max(lengthMm, usedMm, 1);
  const pct = (mm: number) => `${(mm / total) * 100}%`;
  const marginMm = Math.round((lengthMm * marginPercent) / 100);
  const rate = lengthMm > 0 ? Math.round((usedMm / lengthMm) * 100) : 0;
  const overloaded = usedMm > lengthMm;
  const canStore = fits.filter((f) => f.count > 0);

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 12, marginBottom: 8, flexWrap: "wrap" }}>
        <div style={{ fontSize: 13 }}>
          <strong>{cm(usedMm)}</strong>
          <span style={{ color: "var(--text-secondary)" }}> occupés sur {cm(lengthMm)}</span>
          <span style={{ color: "var(--text-tertiary)" }}> · {rate} %</span>
        </div>
        <div style={{ fontSize: 13, fontWeight: 600, color: placeableMm > 0 ? "var(--success-700)" : "var(--danger-700)" }}>
          {placeableMm > 0 ? `Encore ${cm(placeableMm)} rangeables` : overloaded ? "Surchargée" : "Pleine"}
        </div>
      </div>

      <div role="img" aria-label={`Tablette : ${cm(usedMm)} occupés sur ${cm(lengthMm)}`}
        style={{
          position: "relative", height: 34, borderRadius: 6, overflow: "hidden",
          background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)",
          boxShadow: "inset 0 -5px 0 0 color-mix(in srgb, var(--kraft-500) 45%, transparent)",
        }}>
        {marginMm > 0 && (
          <div aria-hidden title={`Marge de manipulation ${marginPercent} % (${cm(marginMm)})`}
            style={{
              position: "absolute", top: 0, bottom: 0, right: lengthMm >= total ? 0 : pct(total - lengthMm),
              width: pct(marginMm),
              background: "repeating-linear-gradient(135deg, transparent 0 5px, color-mix(in srgb, var(--text-tertiary) 22%, transparent) 5px 7px)",
              borderLeft: "1px dashed var(--border-strong)",
            }} />
        )}
        <div style={{ position: "absolute", inset: 0, display: "flex" }}>
          {segments.length === 0 && usedMm > 0 && (
            <div aria-hidden style={{ width: pct(usedMm), height: "100%", background: "var(--kraft-500)" }} />
          )}
          {segments.map((s) => (
            <button key={s.key} type="button" title={`${s.title} · ${spineLabel(s.spineMm)}`}
              onClick={onSegmentClick ? () => onSegmentClick(s.key) : undefined}
              style={{
                width: pct(s.spineMm), flex: "0 0 auto", height: "100%", padding: 0,
                border: "none", borderRight: "2px solid var(--surface-card)",
                background: s.onLoan
                  ? "repeating-linear-gradient(90deg, transparent 0 3px, color-mix(in srgb, var(--info-500) 35%, transparent) 3px 5px)"
                  : spineTone(s.spineMm),
                outline: s.active ? "2px solid var(--brand-500)" : "none", outlineOffset: -2,
                cursor: onSegmentClick ? "pointer" : "default",
                color: "rgba(255,255,255,0.92)", fontSize: 10, fontWeight: 600, overflow: "hidden",
              }}>
              {s.spineMm >= 100 ? s.spineMm / 10 : ""}
            </button>
          ))}
        </div>
      </div>

      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 10, fontSize: 12 }}>
        <span style={{ color: "var(--text-tertiary)" }}>Tient encore :</span>
        {canStore.length === 0 ? (
          <span style={{ fontWeight: 600 }}>aucune boîte du catalogue</span>
        ) : canStore.map((f) => (
          <span key={f.code} style={{
            padding: "2px 8px", borderRadius: 99, background: "var(--surface-sunken)",
            border: "1px solid var(--border-subtle)", fontWeight: 500,
          }}>
            {f.count} × {spineLabel(f.spineMm)}
          </span>
        ))}
      </div>
    </div>
  );
}
