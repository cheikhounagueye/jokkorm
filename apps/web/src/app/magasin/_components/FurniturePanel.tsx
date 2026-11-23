"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { Alert, Button, Card, CardHeader, Field, IconTile, Input, Segmented, type Tone } from "@/components/ui";
import {
  cm,
  getRoomSpec,
  setRoomSpec,
  setStorageSpec,
  type FurnitureType,
  type RoomSpecInput,
  type StorageLocationResponse,
  type StorageLocationTreeResponse,
} from "@/lib/api";
import { Icons, type IconName } from "@/lib/icons";

// ─── Vocabulaire ───────────────────────────────────────────────────────────────

export const FURNITURE: Record<FurnitureType, { label: string; icon: IconName; tone: Tone; hint: string }> = {
  FIXED_SHELVING:  { label: "Rayonnage fixe",   icon: "furnitureFixed",   tone: "kraft",  hint: "simple ou double face" },
  MOBILE_SHELVING: { label: "Rayonnage mobile", icon: "furnitureMobile",  tone: "info",   hint: "compactus, une allée mobile" },
  CABINET:         { label: "Armoire",          icon: "furnitureCabinet", tone: "slate",  hint: "portes, souvent verrouillée" },
  FIRE_SAFE:       { label: "Armoire forte",    icon: "furnitureSafe",    tone: "danger", hint: "ignifuge, pièces sensibles" },
  PLAN_CHEST:      { label: "Meuble à plans",   icon: "furniturePlans",   tone: "violet", hint: "tiroirs (à plat en v2)" },
  PALLET_RACK:     { label: "Rack à palettes",  icon: "furniturePallet",  tone: "warning", hint: "conteneurs (v2)" },
};
const FURNITURE_ORDER = Object.keys(FURNITURE) as FurnitureType[];

/** « Épi E1 · rang 3 · face A » */
export function aisleLabel(n: Pick<StorageLocationResponse, "aisleCode" | "aisleOrder" | "aisleSide">): string | null {
  if (!n.aisleCode) return null;
  return [`Épi ${n.aisleCode}`, n.aisleOrder != null ? `rang ${n.aisleOrder}` : null, n.aisleSide ? `face ${n.aisleSide}` : null]
    .filter(Boolean).join(" · ");
}

const toCm = (mm: number | null | undefined) => (mm == null ? "" : String(mm / 10).replace(".", ","));
const toM = (mm: number | null | undefined) => (mm == null ? "" : String(mm / 1000).replace(".", ","));
const num = (v: string) => {
  const t = v.trim().replace(",", ".");
  if (t === "") return null;
  const n = Number(t);
  return Number.isFinite(n) ? n : NaN;
};
const mmFromCm = (v: string) => { const n = num(v); return n == null || Number.isNaN(n) ? n : Math.round(n * 10); };
const mmFromM = (v: string) => { const n = num(v); return n == null || Number.isNaN(n) ? n : Math.round(n * 1000); };
const meters = (mm: number) => `${(mm / 1000).toLocaleString("fr-FR", { maximumFractionDigits: 2 })} m`;

function useRefresh() {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ["storage-tree"] });
    qc.invalidateQueries({ queryKey: ["space"] });
    qc.invalidateQueries({ queryKey: ["room"] });
    qc.invalidateQueries({ queryKey: ["room-spec"] });
    qc.invalidateQueries({ queryKey: ["room-indicators"] });
  };
}

/** Une mesure en tuile : icône, valeur, libellé ; « non mesuré » en clair. */
function Stat({ icon, label, value, origin, empty = "non mesurée" }: {
  icon: IconName; label: string; value: ReactNode | null; origin?: string; empty?: string;
}) {
  const I = Icons[icon];
  return (
    <div style={{
      display: "flex", gap: 10, alignItems: "center", padding: "10px 12px",
      border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)",
      background: value == null ? "transparent" : "var(--surface-sunken)",
    }}>
      <I size={20} weight="duotone" style={{ color: value == null ? "var(--text-tertiary)" : "var(--text-secondary)", flexShrink: 0 }} />
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 11, color: "var(--text-tertiary)", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.04em" }}>{label}</div>
        <div style={{ fontSize: 14, fontWeight: value == null ? 500 : 700, color: value == null ? "var(--text-tertiary)" : "var(--text-primary)" }}>
          {value ?? empty}
        </div>
        {origin && value != null && <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>{origin}</div>}
      </div>
    </div>
  );
}

const statGrid = { display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(170px, 1fr))", gap: 10 } as const;

// ─── Travée ────────────────────────────────────────────────────────────────────

export function FurnitureCard({ node }: { node: StorageLocationTreeResponse }) {
  const n = node.node;
  const [editing, setEditing] = useState(false);
  const type = n.furnitureType ? FURNITURE[n.furnitureType] : null;
  const tablettes = node.children.length;

  return (
    <Card>
      <div style={{ display: "flex", gap: 14, alignItems: "center", marginBottom: 14 }}>
        <IconTile name={type?.icon ?? "shelf"} tone={type?.tone ?? "slate"} size={48} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)" }}>Mobilier</div>
          <div style={{ fontSize: 18, fontWeight: 700, lineHeight: 1.25 }}>{type?.label ?? "Type non renseigné"}</div>
          <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
            {[aisleLabel(n), n.faces ? (n.faces === 2 ? "double face" : "simple face") : null, `${tablettes} tablette${tablettes > 1 ? "s" : ""}`]
              .filter(Boolean).join(" · ")}
          </div>
        </div>
        {!editing && (
          <Button variant="secondary" icon={<Icons.edit size={14} />} onClick={() => setEditing(true)}>Mesurer</Button>
        )}
      </div>

      {editing ? (
        <FurnitureForm n={n} onDone={() => setEditing(false)} />
      ) : (
        <>
          <div style={statGrid}>
            <Stat icon="width" label="Longueur" value={n.lengthMm != null ? cm(n.lengthMm) : null} empty="déduite" />
            <Stat icon="boxFormats" label="Profondeur d'une face" value={n.depthMm != null ? cm(n.depthMm) : null} />
            <Stat icon="height" label="Hauteur libre / tablette" value={n.clearanceMm != null ? cm(n.clearanceMm) : null} />
            <Stat icon="height" label="Hauteur du meuble" value={n.heightMm != null ? meters(n.heightMm) : null} />
          </div>
          <p style={{ fontSize: 12, color: "var(--text-tertiary)", margin: "10px 0 0" }}>
            Les tablettes héritent de la profondeur et de la hauteur libre, sauf réglage propre.
            Une mesure absente n&apos;est pas contrôlée au rangement.
          </p>
        </>
      )}
    </Card>
  );
}

function FurnitureForm({ n, onDone }: { n: StorageLocationResponse; onDone: () => void }) {
  const refresh = useRefresh();
  const [type, setType] = useState<FurnitureType | null>(n.furnitureType);
  const [depth, setDepth] = useState(toCm(n.depthMm));
  const [clearance, setClearance] = useState(toCm(n.clearanceMm));
  const [height, setHeight] = useState(toCm(n.heightMm));
  const [faces, setFaces] = useState<"0" | "1" | "2">(n.faces ? (String(n.faces) as "1" | "2") : "0");
  const [aisle, setAisle] = useState(n.aisleCode ?? "");
  const [order, setOrder] = useState(n.aisleOrder != null ? String(n.aisleOrder) : "");
  const [side, setSide] = useState<"-" | "A" | "B">(n.aisleSide ?? "-");

  const values = { depthMm: mmFromCm(depth), clearanceMm: mmFromCm(clearance), heightMm: mmFromCm(height), aisleOrder: num(order) };
  const invalid = Object.values(values).some((v) => typeof v === "number" && Number.isNaN(v));

  const mutation = useMutation({
    mutationFn: () => setStorageSpec(n.id, {
      furnitureType: type, ...values,
      faces: faces === "0" ? null : (Number(faces) as 1 | 2),
      aisleCode: aisle.trim() || null,
      aisleSide: side === "-" ? null : side,
    }),
    onSuccess: () => { refresh(); onDone(); },
  });

  return (
    <form onSubmit={(e) => { e.preventDefault(); if (!invalid) mutation.mutate(); }}>
      <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>Type de mobilier</div>
      <FurnitureTypePicker value={type} onChange={setType} />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: 12 }}>
        <Field label="Profondeur d'une face (cm)" hint="Une boîte plus profonde est refusée">
          <Input value={depth} onChange={(e) => setDepth(e.target.value)} inputMode="decimal" placeholder="non mesurée" />
        </Field>
        <Field label="Hauteur libre par tablette (cm)" hint="Une boîte plus haute est refusée">
          <Input value={clearance} onChange={(e) => setClearance(e.target.value)} inputMode="decimal" placeholder="non mesurée" />
        </Field>
        <Field label="Hauteur du meuble (cm)" hint="Comparée au plafond de la salle">
          <Input value={height} onChange={(e) => setHeight(e.target.value)} inputMode="decimal" placeholder="non mesurée" />
        </Field>
      </div>

      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "flex-end", marginBottom: 14 }}>
        <Field label="Faces">
          <Segmented value={faces} onChange={setFaces} options={[
            { value: "0", label: "—" }, { value: "1", label: "Simple" }, { value: "2", label: "Double" },
          ]} />
        </Field>
        <Field label="Épi">
          <Input value={aisle} onChange={(e) => setAisle(e.target.value.toUpperCase())} placeholder="ex. E1" style={{ width: 100 }} maxLength={16} />
        </Field>
        <Field label="Rang dans l'épi">
          <Input value={order} onChange={(e) => setOrder(e.target.value)} inputMode="numeric" placeholder="ex. 3" style={{ width: 90 }} />
        </Field>
        <Field label="Face de l'épi">
          <Segmented value={side} onChange={setSide} options={[
            { value: "-", label: "—" }, { value: "A", label: "A" }, { value: "B", label: "B" },
          ]} />
        </Field>
      </div>

      {invalid && <div style={{ marginBottom: 10 }}><Alert tone="warning">Une valeur n&apos;est pas un nombre.</Alert></div>}
      {mutation.isError && <div style={{ marginBottom: 10 }}><Alert tone="danger" title="Réglage refusé">{(mutation.error as Error).message}</Alert></div>}
      <div style={{ display: "flex", gap: 8 }}>
        <Button type="submit" loading={mutation.isPending} disabled={invalid} icon={<Icons.success size={14} />}>Enregistrer</Button>
        <Button type="button" variant="ghost" onClick={onDone}>Annuler</Button>
      </div>
    </form>
  );
}

/** Choix du type de mobilier en tuiles (un second clic désélectionne). */
export function FurnitureTypePicker({ value, onChange }: { value: FurnitureType | null; onChange: (t: FurnitureType | null) => void }) {
  return (
    <div role="radiogroup" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(118px, 1fr))", gap: 8, marginBottom: 16 }}>
      {FURNITURE_ORDER.map((t) => {
        const m = FURNITURE[t];
        const on = value === t;
        return (
          <button key={t} type="button" role="radio" aria-checked={on} onClick={() => onChange(on ? null : t)} title={m.hint}
            style={{
              display: "flex", flexDirection: "column", alignItems: "center", gap: 6, padding: "10px 6px",
              borderRadius: "var(--radius-md)", cursor: "pointer", color: "var(--text-primary)",
              border: on ? "2px solid var(--brand-500)" : "1px solid var(--border-subtle)",
              background: on ? "color-mix(in srgb, var(--brand-500) 8%, var(--surface-card))" : "var(--surface-card)",
            }}>
            <IconTile name={m.icon} tone={m.tone} size={36} variant={on ? "solid" : "soft"} />
            <span style={{ fontSize: 12, fontWeight: on ? 700 : 500, textAlign: "center" }}>{m.label}</span>
          </button>
        );
      })}
    </div>
  );
}

// ─── Tablette ──────────────────────────────────────────────────────────────────

export function TabletteDimsCard({ node, travee }: { node: StorageLocationResponse; travee: StorageLocationResponse | null }) {
  const refresh = useRefresh();
  const [editing, setEditing] = useState(false);
  const [depth, setDepth] = useState(toCm(node.depthMm));
  const [clearance, setClearance] = useState(toCm(node.clearanceMm));
  const origin = (own: number | null, inherited: number | null | undefined) =>
    own != null ? "propre à la tablette" : inherited != null ? `hérité de la travée ${travee?.code}` : undefined;
  const effClear = node.clearanceMm ?? travee?.clearanceMm ?? null;
  const effDepth = node.depthMm ?? travee?.depthMm ?? null;

  const values = { depthMm: mmFromCm(depth), clearanceMm: mmFromCm(clearance) };
  const invalid = Object.values(values).some((v) => typeof v === "number" && Number.isNaN(v));
  const mutation = useMutation({
    mutationFn: () => setStorageSpec(node.id, values),
    onSuccess: () => { refresh(); setEditing(false); },
  });

  return (
    <Card>
      <CardHeader title="Dimensions de la tablette"
        subtitle="Une boîte plus haute ou plus profonde que la tablette est refusée au rangement." />
      {editing ? (
        <form onSubmit={(e) => { e.preventDefault(); if (!invalid) mutation.mutate(); }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12 }}>
            <Field label="Hauteur libre (cm)" hint="Vide : celle de la travée">
              <Input value={clearance} onChange={(e) => setClearance(e.target.value)} inputMode="decimal"
                placeholder={travee?.clearanceMm != null ? `hérité : ${cm(travee.clearanceMm)}` : "non mesurée"} />
            </Field>
            <Field label="Profondeur (cm)" hint="Vide : celle de la travée">
              <Input value={depth} onChange={(e) => setDepth(e.target.value)} inputMode="decimal"
                placeholder={travee?.depthMm != null ? `hérité : ${cm(travee.depthMm)}` : "non mesurée"} />
            </Field>
          </div>
          {mutation.isError && <div style={{ marginBottom: 10 }}><Alert tone="danger" title="Réglage refusé">{(mutation.error as Error).message}</Alert></div>}
          <div style={{ display: "flex", gap: 8 }}>
            <Button type="submit" loading={mutation.isPending} disabled={invalid}>Enregistrer</Button>
            <Button type="button" variant="ghost" onClick={() => setEditing(false)}>Annuler</Button>
          </div>
        </form>
      ) : (
        <div style={{ display: "flex", gap: 10, alignItems: "stretch", flexWrap: "wrap" }}>
          <div style={{ ...statGrid, flex: 1 }}>
            <Stat icon="height" label="Hauteur libre" value={effClear != null ? cm(effClear) : null} origin={origin(node.clearanceMm, travee?.clearanceMm)} />
            <Stat icon="boxFormats" label="Profondeur" value={effDepth != null ? cm(effDepth) : null} origin={origin(node.depthMm, travee?.depthMm)} />
          </div>
          <div style={{ alignSelf: "center" }}>
            <Button variant="secondary" icon={<Icons.edit size={14} />} onClick={() => setEditing(true)}>Régler</Button>
          </div>
        </div>
      )}
    </Card>
  );
}

// ─── Salle ─────────────────────────────────────────────────────────────────────

export function RoomSpecCard({ storehouseId }: { storehouseId: string }) {
  const query = useQuery({ queryKey: ["room-spec", storehouseId], queryFn: () => getRoomSpec(storehouseId) });
  const [editing, setEditing] = useState(false);
  if (!query.data) return null;
  const { spec, limits } = query.data;
  const area = limits.areaM2;
  const footprintPct = area ? Math.min(100, Math.round((limits.footprintM2 / area) * 100)) : null;
  const m2 = (v: number) => `${v.toLocaleString("fr-FR", { maximumFractionDigits: 1 })} m²`;

  return (
    <Card>
      <div style={{ display: "flex", gap: 14, alignItems: "center", marginBottom: 14 }}>
        <IconTile name="storehouse" tone="kraft" size={48} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)" }}>Salle</div>
          <div style={{ fontSize: 18, fontWeight: 700, lineHeight: 1.25 }}>
            {spec?.lengthMm != null && spec.widthMm != null ? `${meters(spec.lengthMm)} × ${meters(spec.widthMm)}` : "Dimensions non renseignées"}
          </div>
          <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
            {limits.shelves} travée{limits.shelves > 1 ? "s" : ""}, dont {limits.measuredShelves} avec profondeur
          </div>
        </div>
        {!editing && <Button variant="secondary" icon={<Icons.edit size={14} />} onClick={() => setEditing(true)}>Mesurer</Button>}
      </div>

      {editing ? (
        <RoomSpecForm storehouseId={storehouseId} spec={spec} onDone={() => setEditing(false)} />
      ) : (
        <>
          <div style={statGrid}>
            <Stat icon="height" label="Sous plafond" value={spec?.ceilingHeightMm != null ? meters(spec.ceilingHeightMm) : null} />
            <Stat icon="boxFormats" label="Surface utile" value={area != null ? m2(area) : null}
              origin={spec?.usableAreaM2 == null && area != null ? "longueur × largeur" : undefined} />
            <Stat icon="floorLoad" label="Charge au sol" value={spec?.floorLoadKgM2 != null ? `${spec.floorLoadKgM2} kg/m²` : null} />
            <Stat icon="hygrometer" label="Température" value={spec?.tempMinC != null || spec?.tempMaxC != null
              ? `${spec?.tempMinC ?? "?"} à ${spec?.tempMaxC ?? "?"} °C` : null} />
            <Stat icon="humidity" label="Humidité relative" value={spec?.rhMinPct != null || spec?.rhMaxPct != null
              ? `${spec?.rhMinPct ?? "?"} à ${spec?.rhMaxPct ?? "?"} %` : null} />
            <Stat icon="floorLoad" label="Poids moyen au ml" value={spec?.avgWeightKgPerMl != null ? `${String(spec.avgWeightKgPerMl).replace(".", ",")} kg/ml` : null} empty="non mesuré" />
          </div>

          {footprintPct != null && (
            <div style={{ marginTop: 14 }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 4 }}>
                <span style={{ color: "var(--text-secondary)" }}>Emprise estimée du mobilier (sans les allées)</span>
                <strong>{m2(limits.footprintM2)} · {footprintPct} %</strong>
              </div>
              <div style={{ height: 8, borderRadius: 99, background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)", overflow: "hidden" }}>
                <div style={{ width: `${Math.max(footprintPct, limits.footprintM2 > 0 ? 2 : 0)}%`, height: "100%", background: footprintPct >= 100 ? "var(--danger-500)" : "var(--kraft-500)" }} />
              </div>
            </div>
          )}
        </>
      )}

      {limits.warnings.length > 0 && (
        <div style={{ marginTop: 14 }}>
          <Alert tone="warning" title={`${limits.warnings.length} point${limits.warnings.length > 1 ? "s" : ""} à vérifier dans la salle`}>
            <ul style={{ margin: "4px 0 0", paddingLeft: 18 }}>
              {limits.warnings.map((w) => <li key={w}>{w}</li>)}
            </ul>
          </Alert>
        </div>
      )}
    </Card>
  );
}

function RoomSpecForm({ storehouseId, spec, onDone }: { storehouseId: string; spec: RoomSpecInput | null; onDone: () => void }) {
  const refresh = useRefresh();
  const [f, setF] = useState({
    length: toM(spec?.lengthMm), width: toM(spec?.widthMm), ceiling: toM(spec?.ceilingHeightMm),
    area: spec?.usableAreaM2 != null ? String(spec.usableAreaM2).replace(".", ",") : "",
    load: spec?.floorLoadKgM2 != null ? String(spec.floorLoadKgM2) : "",
    tmin: spec?.tempMinC != null ? String(spec.tempMinC).replace(".", ",") : "",
    tmax: spec?.tempMaxC != null ? String(spec.tempMaxC).replace(".", ",") : "",
    rhmin: spec?.rhMinPct != null ? String(spec.rhMinPct) : "",
    rhmax: spec?.rhMaxPct != null ? String(spec.rhMaxPct) : "",
    weight: spec?.avgWeightKgPerMl != null ? String(spec.avgWeightKgPerMl).replace(".", ",") : "",
  });
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });
  const input: RoomSpecInput = {
    lengthMm: mmFromM(f.length), widthMm: mmFromM(f.width), ceilingHeightMm: mmFromM(f.ceiling),
    usableAreaM2: num(f.area), floorLoadKgM2: num(f.load), tempMinC: num(f.tmin), tempMaxC: num(f.tmax),
    rhMinPct: num(f.rhmin), rhMaxPct: num(f.rhmax), avgWeightKgPerMl: num(f.weight),
  };
  const invalid = Object.values(input).some((v) => typeof v === "number" && Number.isNaN(v));
  const mutation = useMutation({
    mutationFn: () => setRoomSpec(storehouseId, input),
    onSuccess: () => { refresh(); onDone(); },
  });
  const field = (label: string, k: keyof typeof f, placeholder: string, hint?: string) => (
    <Field label={label} hint={hint}>
      <Input value={f[k]} onChange={set(k)} inputMode="decimal" placeholder={placeholder} />
    </Field>
  );

  return (
    <form onSubmit={(e) => { e.preventDefault(); if (!invalid) mutation.mutate(); }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: 12 }}>
        {field("Longueur (m)", "length", "ex. 12,5")}
        {field("Largeur (m)", "width", "ex. 8")}
        {field("Hauteur sous plafond (m)", "ceiling", "ex. 2,8")}
        {field("Surface utile (m²)", "area", "vide : longueur × largeur")}
        {field("Charge au sol (kg/m²)", "load", "selon le bâtiment")}
        {field("Poids moyen au ml (kg)", "weight", "à mesurer", "Pesez 1 mètre de boîtes pleines")}
      </div>
      <div style={{ fontSize: 13, fontWeight: 600, margin: "4px 0 8px" }}>Consignes climatiques</div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", gap: 12 }}>
        {field("Température min (°C)", "tmin", "—")}
        {field("Température max (°C)", "tmax", "—")}
        {field("Humidité min (%)", "rhmin", "—")}
        {field("Humidité max (%)", "rhmax", "—")}
      </div>
      <p style={{ fontSize: 12, color: "var(--text-tertiary)", margin: "0 0 12px" }}>
        Aucune valeur n&apos;est proposée : les consignes se fixent d&apos;après l&apos;ISO 11799 et les
        recommandations nationales applicables à la salle.
      </p>
      {invalid && <div style={{ marginBottom: 10 }}><Alert tone="warning">Une valeur n&apos;est pas un nombre.</Alert></div>}
      {mutation.isError && <div style={{ marginBottom: 10 }}><Alert tone="danger" title="Enregistrement refusé">{(mutation.error as Error).message}</Alert></div>}
      <div style={{ display: "flex", gap: 8 }}>
        <Button type="submit" loading={mutation.isPending} disabled={invalid} icon={<Icons.success size={14} />}>Enregistrer</Button>
        <Button type="button" variant="ghost" onClick={onDone}>Annuler</Button>
      </div>
    </form>
  );
}
