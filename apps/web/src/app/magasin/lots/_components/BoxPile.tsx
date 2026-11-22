"use client";

import { ArchiveBoxIso, type BoxFinish } from "@/components/illustrations/ArchiveBoxIso";

/**
 * Pile de boîtes isométriques : rangées décalées qui se chevauchent, comme une
 * palette de cartons. Le nombre affiché est plafonné ; l'excédent s'affiche en « +N ».
 */
export function BoxPile({
  count,
  max = 24,
  perRow = 6,
  size = 44,
  finishAt,
}: {
  count: number;
  max?: number;
  perRow?: number;
  size?: number;
  finishAt?: (index: number) => BoxFinish;
}) {
  const shown = Math.max(0, Math.min(count, max));
  const rows: number[][] = [];
  for (let i = 0; i < shown; i += perRow) {
    rows.push(Array.from({ length: Math.min(perRow, shown - i) }, (_, k) => i + k));
  }
  const overflow = count - shown;

  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 10 }}>
      <div aria-hidden style={{ display: "inline-flex", flexDirection: "column" }}>
        {rows.map((row, r) => (
          <div key={r} style={{
            display: "flex",
            marginTop: r === 0 ? 0 : -Math.round(size * 0.4),
            marginLeft: r % 2 === 1 ? Math.round(size * 0.45) : 0,
          }}>
            {row.map((i, k) => (
              <div key={i} style={{ marginLeft: k === 0 ? 0 : -Math.round(size * 0.14) }}>
                <ArchiveBoxIso size={size} finish={finishAt ? finishAt(i) : "kraft"} />
              </div>
            ))}
          </div>
        ))}
      </div>
      {overflow > 0 && (
        <span style={{
          fontSize: 12, fontWeight: 700, color: "var(--kraft-700)",
          background: "color-mix(in srgb, var(--kraft-500) 14%, transparent)",
          border: "1px solid color-mix(in srgb, var(--kraft-500) 30%, transparent)",
          padding: "3px 9px", borderRadius: "var(--radius-full)", marginBottom: 6,
        }}>
          +{overflow}
        </span>
      )}
    </div>
  );
}
