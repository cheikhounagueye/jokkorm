"use client";

export type AveryTemplate = "AVERY_L7160" | "AVERY_L7159" | "AVERY_L7162";

/** Formats des planches d'étiquettes Avery A4 gérés par le backend. */
export const AVERY_TEMPLATES: Record<AveryTemplate, { code: string; cols: number; rows: number; perPage: number; dims: string }> = {
  AVERY_L7160: { code: "L7160", cols: 3, rows: 7, perPage: 21, dims: "63,5 × 38,1 mm" },
  AVERY_L7159: { code: "L7159", cols: 3, rows: 8, perPage: 24, dims: "63,5 × 33,9 mm" },
  AVERY_L7162: { code: "L7162", cols: 2, rows: 8, perPage: 16, dims: "99,1 × 33,9 mm" },
};

/** Barres de code-barres stylisées (aperçu, pas un code lisible). */
const FAKE_BARS =
  "repeating-linear-gradient(90deg, #111 0 2px, transparent 2px 3px, #111 3px 4px, transparent 4px 7px, #111 7px 10px, transparent 10px 12px)";

/** Planche A4 miniature : la grille d'étiquettes du template, cases remplies jusqu'à `filled`. */
export function SheetPreview({ template, filled, width = 150 }: { template: AveryTemplate; filled: number; width?: number }) {
  const t = AVERY_TEMPLATES[template];
  const used = Math.min(filled, t.perPage);
  return (
    <div aria-hidden style={{
      width, aspectRatio: "210 / 297",
      background: "#ffffff",
      border: "1px solid var(--border-strong)",
      borderRadius: 4,
      boxShadow: "var(--shadow-md)",
      padding: "7% 5%",
      display: "grid",
      gridTemplateColumns: `repeat(${t.cols}, 1fr)`,
      gridTemplateRows: `repeat(${t.rows}, 1fr)`,
      gap: 3,
    }}>
      {Array.from({ length: t.perPage }, (_, i) => (
        <div key={i} style={{
          borderRadius: 2,
          border: i < used ? "1px solid #d9cdb6" : "1px dashed #ddd6c8",
          background: i < used ? "#fffdf8" : "transparent",
          display: "flex", alignItems: "center", justifyContent: "center",
        }}>
          {i < used && <span style={{ width: "62%", height: "38%", backgroundImage: FAKE_BARS }} />}
        </div>
      ))}
    </div>
  );
}

/** Étiquette autocollante d'aperçu, au rendu proche de l'impression PDF. */
export function LabelSticker({ title, seq, code }: { title: string; seq: string; code: string }) {
  return (
    <div style={{
      background: "#ffffff",
      color: "#111111",
      border: "1px solid #d9cdb6",
      borderRadius: 10,
      boxShadow: "0 1px 0 rgba(255,255,255,0.8) inset, 0 6px 16px -6px rgba(60, 40, 10, 0.28)",
      padding: "12px 14px 10px",
      position: "relative",
    }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 11, marginBottom: 8 }}>
        <strong style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{title}</strong>
        <span style={{ color: "#555555", whiteSpace: "nowrap" }}>N° {seq}</span>
      </div>
      <div style={{ height: 46, backgroundImage: FAKE_BARS, borderRadius: 1 }} />
      <div style={{ textAlign: "center", fontFamily: "var(--font-mono)", fontSize: 12, letterSpacing: "0.08em", marginTop: 6 }}>
        {code}
      </div>
    </div>
  );
}
