"use client";

import { useQuery } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { Alert, Card, IconTile, type Tone } from "@/components/ui";
import { getRoomIndicators, ml } from "@/lib/api";
import { type IconName } from "@/lib/icons";
import { spineLabel } from "./useBoxFormats";

const pct = (r: number) => `${Math.round(r * 100)} %`;
const barColor = (r: number) => (r >= 0.9 ? "var(--danger-500)" : r >= 0.6 ? "var(--warning-500)" : "var(--success-500)");

function Kpi({ icon, tone, label, value, sub }: { icon: IconName; tone: Tone; label: string; value: ReactNode; sub?: ReactNode }) {
  return (
    <div style={{ display: "flex", gap: 12, alignItems: "center", padding: "12px 14px", borderRadius: "var(--radius-md)",
      border: "1px solid var(--border-subtle)", background: "var(--surface-sunken)" }}>
      <IconTile name={icon} tone={tone} size={38} />
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.04em", color: "var(--text-tertiary)" }}>{label}</div>
        <div style={{ fontSize: 20, fontWeight: 800, lineHeight: 1.15 }}>{value}</div>
        {sub && <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>{sub}</div>}
      </div>
    </div>
  );
}

/** Indicateurs en mètres linéaires d'une salle (pattern 10 § 3, 5T.D). */
export function RoomIndicatorsCard({ storehouseId }: { storehouseId: string }) {
  const q = useQuery({ queryKey: ["room-indicators", storehouseId], queryFn: () => getRoomIndicators(storehouseId) });
  if (!q.data) return null;
  const i = q.data;
  const s = i.saturation;
  const fits = i.capacityByFormat.filter((f) => f.boxes > 0);
  const satDate = s.date ? new Date(s.date).toLocaleDateString("fr-FR", { month: "long", year: "numeric" }) : null;

  return (
    <Card>
      <div style={{ display: "flex", gap: 14, alignItems: "center", marginBottom: 14 }}>
        <IconTile name="gauge" tone="brand" size={44} />
        <div>
          <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)" }}>Indicateurs</div>
          <div style={{ fontSize: 18, fontWeight: 700 }}>{i.boxes} boîte{i.boxes > 1 ? "s" : ""} · {pct(i.rate)} de la salle occupée</div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 10, marginBottom: 16 }}>
        <Kpi icon="shelf" tone="info" label="Installés" value={ml(i.installedMm)} sub="longueur de tablettes" />
        <Kpi icon="box" tone="kraft" label="Occupés" value={ml(i.usedMm)} sub={pct(i.rate)} />
        <Kpi icon="success" tone="success" label="Rangeables" value={ml(i.placeableMm)} sub="marge de manipulation déduite" />
        <Kpi icon="storehouse" tone="slate" label="Densité"
          value={i.densityMlPerM2 != null ? `${i.densityMlPerM2.toLocaleString("fr-FR")} ml/m²` : "—"}
          sub={i.areaM2 != null ? `sur ${i.areaM2.toLocaleString("fr-FR")} m²` : "surface non mesurée"} />
      </div>

      {i.byAisle.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>Par épi</div>
          <div style={{ display: "grid", gap: 8 }}>
            {i.byAisle.map((a) => (
              <div key={a.key || "none"}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 3 }}>
                  <span style={{ fontWeight: 600 }}>{a.label} <span style={{ color: "var(--text-tertiary)", fontWeight: 500 }}>· {a.travees} travée{a.travees > 1 ? "s" : ""}</span></span>
                  <span>{ml(a.usedMm)} / {ml(a.installedMm)} · <strong>{pct(a.rate)}</strong></span>
                </div>
                <div style={{ height: 8, borderRadius: 99, background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)", overflow: "hidden" }}>
                  <div style={{ width: `${Math.max(a.rate * 100, a.boxes > 0 ? 2 : 0)}%`, height: "100%", background: barColor(a.rate) }} />
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center", fontSize: 12, marginBottom: 16 }}>
        <span style={{ color: "var(--text-tertiary)" }}>Tient encore :</span>
        {fits.length === 0 ? <strong>aucune boîte du catalogue</strong> : fits.map((f) => (
          <span key={f.code} style={{ padding: "2px 9px", borderRadius: 99, background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)", fontWeight: 600 }}>
            {f.boxes.toLocaleString("fr-FR")} × {spineLabel(f.spineMm)}
          </span>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 10 }}>
        <div style={{ display: "flex", gap: 12, padding: 12, borderRadius: "var(--radius-md)", border: "1px solid var(--border-subtle)" }}>
          <IconTile name="calendar" tone={s.date ? "violet" : "slate"} size={36} variant="soft" />
          <div>
            <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.04em", color: "var(--text-tertiary)" }}>Saturation estimée</div>
            {satDate ? (
              <>
                <div style={{ fontSize: 16, fontWeight: 700, textTransform: "capitalize" }}>{satDate}</div>
                <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                  au rythme de {(s.mmPerMonth! / 10).toLocaleString("fr-FR", { maximumFractionDigits: 1 })} cm de boîtes par mois (12 derniers mois)
                </div>
              </>
            ) : <div style={{ fontSize: 13, color: "var(--text-secondary)" }}>{s.note}</div>}
          </div>
        </div>
        <div style={{ display: "flex", gap: 12, padding: 12, borderRadius: "var(--radius-md)", border: "1px solid var(--border-subtle)" }}>
          <IconTile name="floorLoad" tone={i.load?.overLimit ? "danger" : "slate"} size={36} variant="soft" />
          <div>
            <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.04em", color: "var(--text-tertiary)" }}>Charge au sol estimée</div>
            {i.load && i.load.kgPerM2 != null ? (
              <>
                <div style={{ fontSize: 16, fontWeight: 700, color: i.load.overLimit ? "var(--danger-700)" : undefined }}>
                  {i.load.kgPerM2.toLocaleString("fr-FR")} kg/m²{i.load.limitKgM2 != null ? ` pour ${i.load.limitKgM2} admis` : ""}
                </div>
                <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                  ≈ {Math.round(i.load.estimatedKg).toLocaleString("fr-FR")} kg sur {i.load.footprintM2.toLocaleString("fr-FR")} m² de mobilier ·
                  indicatif, à faire confirmer par un bureau d&apos;études
                </div>
              </>
            ) : <div style={{ fontSize: 13, color: "var(--text-secondary)" }}>{i.loadNote}</div>}
          </div>
        </div>
      </div>
      {i.load?.overLimit && (
        <div style={{ marginTop: 12 }}>
          <Alert tone="danger" title="Charge estimée au-dessus de la charge admissible">
            Estimation indicative : faites vérifier la structure avant d&apos;ajouter des boîtes.
          </Alert>
        </div>
      )}
    </Card>
  );
}
