"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, CardHeader, Field, IconTile, Input, Segmented } from "@/components/ui";
import { generateShelving, type FurnitureType, type ShelvingTemplate } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { FURNITURE, FurnitureTypePicker } from "./FurniturePanel";

const int = (v: string) => (/^\d+$/.test(v.trim()) ? Number(v.trim()) : NaN);
const cmToMm = (v: string): number | null => {
  const t = v.trim().replace(",", ".");
  if (t === "") return null;
  const n = Number(t);
  return Number.isFinite(n) ? Math.round(n * 10) : NaN;
};

/**
 * Générateur de topographie à gabarit (pattern 10 § 4.4) : « 2 épis × 5 rangs × 2 faces,
 * 6 tablettes de 100 cm, rayonnage fixe ». Plan vu de dessus, aperçu calculé par le serveur
 * (codes exacts), puis création en une fois.
 */
export function ShelvingGenerator({ storehouseId, onDone, onCancel }: {
  storehouseId: string; onDone: () => void; onCancel: () => void;
}) {
  const qc = useQueryClient();
  const [type, setType] = useState<FurnitureType | null>("FIXED_SHELVING");
  const [aisles, setAisles] = useState("2");
  const [ranks, setRanks] = useState("5");
  const [faces, setFaces] = useState<"1" | "2">("2");
  const [tablettes, setTablettes] = useState("6");
  const [positions, setPositions] = useState("1");
  const [length, setLength] = useState("100");
  const [depth, setDepth] = useState("");
  const [clearance, setClearance] = useState("");
  const [height, setHeight] = useState("");

  const template: ShelvingTemplate = {
    aisles: int(aisles), ranksPerFace: int(ranks), faces: Number(faces) as 1 | 2,
    tablettesPerTravee: int(tablettes), positionsPerTablette: int(positions),
    lengthMm: cmToMm(length), furnitureType: type,
    depthMm: cmToMm(depth), clearanceMm: cmToMm(clearance), heightMm: cmToMm(height),
  };
  const valid = Object.values(template).every((v) => typeof v !== "number" || Number.isFinite(v));

  // Aperçu serveur, recalculé 300 ms après la dernière frappe.
  const key = JSON.stringify(template);
  const [debounced, setDebounced] = useState(key);
  useEffect(() => { const t = setTimeout(() => setDebounced(key), 300); return () => clearTimeout(t); }, [key]);
  const plan = useQuery({
    queryKey: ["shelving-plan", storehouseId, debounced],
    queryFn: () => generateShelving(storehouseId, JSON.parse(debounced) as ShelvingTemplate, true),
    enabled: valid && debounced === key,
    retry: false,
  });

  const create = useMutation({
    mutationFn: () => generateShelving(storehouseId, template, false),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["storage-tree"] });
      qc.invalidateQueries({ queryKey: ["room"] });
      qc.invalidateQueries({ queryKey: ["room-spec"] });
      onDone();
    },
  });

  const p = plan.data;
  return (
    <Card>
      <CardHeader title="Générer des travées" subtitle="Décrivez un gabarit : JokkoRM crée épis, travées, tablettes et emplacements d'un coup." />

      <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>Mobilier</div>
      <FurnitureTypePicker value={type} onChange={setType} />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(5, minmax(0, 1fr))", gap: 12, alignItems: "end" }}>
        <Field label="Épis"><Input value={aisles} onChange={(e) => setAisles(e.target.value)} inputMode="numeric" /></Field>
        <Field label="Travées par face"><Input value={ranks} onChange={(e) => setRanks(e.target.value)} inputMode="numeric" /></Field>
        <Field label="Faces">
          <Segmented value={faces} onChange={setFaces} options={[{ value: "1", label: "Simple" }, { value: "2", label: "Double" }]} />
        </Field>
        <Field label="Tablettes par travée"><Input value={tablettes} onChange={(e) => setTablettes(e.target.value)} inputMode="numeric" /></Field>
        <Field label="Emplacements / tablette"><Input value={positions} onChange={(e) => setPositions(e.target.value)} inputMode="numeric" /></Field>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", gap: 12 }}>
        <Field label="Longueur d'une travée (cm)"><Input value={length} onChange={(e) => setLength(e.target.value)} inputMode="decimal" placeholder="vide : 100 cm" /></Field>
        <Field label="Profondeur d'une face (cm)"><Input value={depth} onChange={(e) => setDepth(e.target.value)} inputMode="decimal" placeholder="non mesurée" /></Field>
        <Field label="Hauteur libre (cm)"><Input value={clearance} onChange={(e) => setClearance(e.target.value)} inputMode="decimal" placeholder="non mesurée" /></Field>
        <Field label="Hauteur du meuble (cm)"><Input value={height} onChange={(e) => setHeight(e.target.value)} inputMode="decimal" placeholder="non mesurée" /></Field>
      </div>

      {valid && Number.isFinite(template.aisles) && Number.isFinite(template.ranksPerFace) && (
        <TopView aisles={template.aisles} ranks={template.ranksPerFace} faces={template.faces}
          codes={p?.aisleCodes} type={type} />
      )}

      {!valid && <div style={{ marginBottom: 12 }}><Alert tone="warning">Une valeur n&apos;est pas un nombre.</Alert></div>}
      {plan.isError && <div style={{ marginBottom: 12 }}><Alert tone="warning">{(plan.error as Error).message}</Alert></div>}
      {p && (
        <div style={{
          display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", padding: "12px 14px", marginBottom: 14,
          borderRadius: "var(--radius-md)", background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)",
        }}>
          <IconTile name="shelf" tone="info" size={34} />
          <div style={{ flex: 1, minWidth: 240 }}>
            <div style={{ fontWeight: 700 }}>
              {template.aisles} épi{template.aisles > 1 ? "s" : ""} × {template.ranksPerFace} rang{template.ranksPerFace > 1 ? "s" : ""} × {template.faces} face{template.faces > 1 ? "s" : ""} = {p.travees} travées
            </div>
            <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
              {p.tablettes} tablettes · {p.positions} emplacements · {(p.installedMm / 1000).toLocaleString("fr-FR")} ml installés ·
              codes {p.firstTravee} → {p.lastTravee} · épi{p.aisleCodes.length > 1 ? "s" : ""} {p.aisleCodes.join(", ")}
            </div>
          </div>
        </div>
      )}

      {create.isError && <div style={{ marginBottom: 12 }}><Alert tone="danger" title="Création refusée">{(create.error as Error).message}</Alert></div>}
      <div style={{ display: "flex", gap: 8 }}>
        <Button icon={<Icons.add size={15} weight="bold" />} loading={create.isPending} disabled={!p || plan.isError || !valid}
          onClick={() => create.mutate()}>
          {p ? `Créer les ${p.travees} travées` : "Créer"}
        </Button>
        <Button variant="ghost" onClick={onCancel}>Annuler</Button>
      </div>
    </Card>
  );
}

/** Plan vu de dessus : chaque épi, sa face A au-dessus, sa face B en dessous, une case par travée. */
function TopView({ aisles, ranks, faces, codes, type }: {
  aisles: number; ranks: number; faces: 1 | 2; codes?: string[]; type: FurnitureType | null;
}) {
  const shown = Math.min(aisles, 6);
  const cell = Math.max(10, Math.min(42, 560 / Math.max(1, ranks)));
  const w = 64 + ranks * cell + 8;
  const faceH = 22;
  const rowH = faces === 2 ? faceH * 2 + 26 : faceH + 26;
  const h = shown * rowH + 8;
  const colors = useMemo(() => ({ a: "#c99b62", b: "#e9cb9d", edge: "#a77942" }), []);
  return (
    <div style={{ margin: "4px 0 14px", padding: 12, borderRadius: "var(--radius-md)", border: "1px solid var(--border-subtle)", background: "var(--surface-card)", overflowX: "auto" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12, color: "var(--text-secondary)", marginBottom: 8 }}>
        <Icons.compass size={16} weight="duotone" /> Plan vu de dessus{type ? ` · ${FURNITURE[type].label.toLowerCase()}` : ""}
      </div>
      <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} role="img" aria-label="Plan des épis">
        {Array.from({ length: shown }, (_, a) => {
          const y0 = 4 + a * rowH;
          const label = codes?.[a] ?? `É${a + 1}`;
          return (
            <g key={a}>
              <text x={4} y={y0 + (faces === 2 ? faceH + 4 : faceH / 2 + 4)} fontSize={12} fontWeight={700} fill="currentColor">{label}</text>
              {Array.from({ length: faces }, (_, f) => (
                <g key={f}>
                  <text x={44} y={y0 + f * faceH + faceH / 2 + 4} fontSize={10} fill="currentColor" opacity={0.6}>{faces === 2 ? (f === 0 ? "A" : "B") : ""}</text>
                  {Array.from({ length: ranks }, (_, r) => (
                    <rect key={r} x={60 + r * cell} y={y0 + f * faceH} width={cell - 2} height={faceH - 2} rx={2}
                      fill={f === 0 ? colors.a : colors.b} stroke={colors.edge} strokeWidth={0.6} />
                  ))}
                </g>
              ))}
            </g>
          );
        })}
      </svg>
      {aisles > shown && <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>… et {aisles - shown} autres épis</div>}
      <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 4 }}>
        Une case = une travée. En double face, chaque rang porte deux travées, une par face.
      </div>
    </div>
  );
}
