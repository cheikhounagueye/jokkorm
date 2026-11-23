"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  Field,
  Input,
  PageHeader,
  Select,
} from "@/components/ui";
import {
  createBoxFormat,
  updateBoxFormat,
  type BoxFormatResponse,
  type BoxQuality,
} from "@/lib/api";
import { Icons } from "@/lib/icons";
import { useRole } from "../../demandes/_components/useRole";
import { BoxSpine } from "../_components/FormatPicker";
import { spineLabel, useBoxFormats } from "../_components/useBoxFormats";

const QUALITY: Record<BoxQuality, string> = {
  CONSERVATION: "Conservation",
  STANDARD: "Standard",
  POLYPROPYLENE: "Polypropylène",
};

/** Bornes du serveur (BoxFormat), saisies ici en centimètres. */
const LIMITS = { spine: [1, 100], depth: [5, 150], height: [5, 150] } as const;

const toMm = (cmValue: string) => Math.round(parseFloat(cmValue.replace(",", ".")) * 10);
const fmtCm = (mm: number) => String(mm / 10).replace(".", ",");
/** Boîtes au mètre linéaire (mesure pure, sans la marge de manipulation des tablettes). */
const perMl = (spineMm: number) => {
  const v = 1000 / spineMm;
  return (Number.isInteger(v) ? String(v) : v.toFixed(1)).replace(".", ",");
};

/** BOITE-25X33-D10, comme le catalogue standard. */
function suggestCode(spine: string, depth: string, height: string): string {
  const part = (v: string) => v.trim().replace(",", ".").replace(".", "_");
  const s = part(spine);
  return `BOITE-${part(depth)}X${part(height)}-D${s.length === 1 ? `0${s}` : s}`.toUpperCase();
}

export default function BoxFormatsPage() {
  const role = useRole();
  const canEdit = role === "ARCHIVIST" || role === "ARCHIVES_HEAD";
  const { query, formats } = useBoxFormats();
  const qc = useQueryClient();
  const [creating, setCreating] = useState(false);

  // Retirer un format change ce qui « tient encore » sur chaque tablette.
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["box-formats"] });
    qc.invalidateQueries({ queryKey: ["space"] });
    qc.invalidateQueries({ queryKey: ["room"] });
  };

  const active = formats.filter((f) => f.active);
  const retired = formats.filter((f) => !f.active);

  return (
    <section className="fade-up">
      <PageHeader
        icon="boxFormats"
        tone="kraft"
        title="Formats de boîtes"
        subtitle="Le dos de chaque boîte fixe la place qu'elle prend sur la tablette."
        crumbs={[{ label: "Salle d'archives", href: "/magasin" }, { label: "Formats de boîtes" }]}
        actions={
          <Button icon={<Icons.add size={16} weight="bold" />} disabled={!canEdit || creating}
            title={canEdit ? undefined : "Réservé à l'archiviste et au responsable des archives"}
            onClick={() => setCreating(true)}>
            Nouveau format
          </Button>
        }
      />

      {role !== null && !canEdit && (
        <div style={{ marginBottom: 20 }}>
          <Alert tone="info" title="Lecture seule">
            Seuls l&apos;archiviste et le responsable des archives modifient le catalogue.
          </Alert>
        </div>
      )}

      {query.isError && (
        <div style={{ marginBottom: 20 }}>
          <Alert tone="danger" title="Catalogue indisponible">{(query.error as Error).message}</Alert>
        </div>
      )}

      {creating && canEdit && (
        <CreateFormatCard
          onCancel={() => setCreating(false)}
          onCreated={() => { setCreating(false); refresh(); }}
        />
      )}

      {formats.length > 0 && <CatalogueShelf formats={active} />}

      {query.isLoading && (
        <Card><p style={{ color: "var(--text-tertiary)", fontSize: 13 }}>Chargement du catalogue…</p></Card>
      )}

      {query.data && formats.length === 0 && (
        <Card>
          <EmptyState icon={<Icons.boxFormats size={30} />} title="Catalogue vide"
            description="Ajoute un premier format : son dos sert à calculer la place sur les tablettes." />
        </Card>
      )}

      {active.length > 0 && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(250px, 1fr))", gap: 16, marginBottom: 24 }}>
          {active.map((f) => <FormatCard key={f.id} format={f} canEdit={canEdit} onChanged={refresh} />)}
        </div>
      )}

      {retired.length > 0 && (
        <>
          <h3 style={{ fontSize: 13, fontWeight: 600, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.06em", margin: "8px 0 12px" }}>
            Retirés du catalogue
          </h3>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginBottom: 12 }}>
            Les boîtes déjà rangées gardent leur format ; il n&apos;est simplement plus proposé.
          </p>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(250px, 1fr))", gap: 16 }}>
            {retired.map((f) => <FormatCard key={f.id} format={f} canEdit={canEdit} onChanged={refresh} />)}
          </div>
        </>
      )}
    </section>
  );
}

/** Tous les formats actifs posés côte à côte sur une planche, à la même échelle. */
function CatalogueShelf({ formats }: { formats: BoxFormatResponse[] }) {
  if (formats.length === 0) return null;
  return (
    <Card style={{ marginBottom: 20 }}>
      <div style={{
        display: "grid", gridTemplateColumns: `repeat(${formats.length}, auto) 1fr`,
        columnGap: 28, alignItems: "end", overflowX: "auto",
      }}>
        {formats.map((f, i) => (
          <div key={f.id} title={f.label} style={{ gridRow: 1, gridColumn: i + 1, justifySelf: "center", paddingLeft: i === 0 ? 18 : 0 }}>
            <BoxSpine spineMm={f.spineMm} heightMm={f.heightMm} scale={0.3} />
          </div>
        ))}
        <div aria-hidden style={{
          gridRow: 2, gridColumn: "1 / -1", height: 14, borderRadius: 3, marginBottom: 10,
          background: "linear-gradient(180deg, var(--kraft-300) 0%, var(--kraft-500) 70%, var(--kraft-700) 100%)",
          boxShadow: "0 6px 10px -4px rgba(15, 23, 42, 0.35)",
        }} />
        {formats.map((f, i) => (
          <div key={f.id} style={{ gridRow: 3, gridColumn: i + 1, textAlign: "center", paddingLeft: i === 0 ? 18 : 0 }}>
            <div style={{ fontWeight: 700, fontSize: 13 }}>{spineLabel(f.spineMm)}</div>
            <div style={{ fontSize: 11, color: "var(--text-tertiary)", whiteSpace: "nowrap" }}>{perMl(f.spineMm)} / ml</div>
          </div>
        ))}
      </div>
    </Card>
  );
}

function FormatCard({ format: f, canEdit, onChanged }: {
  format: BoxFormatResponse; canEdit: boolean; onChanged: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [label, setLabel] = useState(f.label);
  const [quality, setQuality] = useState<BoxQuality>(f.quality);
  const [weight, setWeight] = useState(f.maxWeightKg != null ? String(f.maxWeightKg) : "");

  const mutation = useMutation({
    mutationFn: (change: Parameters<typeof updateBoxFormat>[1]) => updateBoxFormat(f.id, change),
    onSuccess: () => { setEditing(false); onChanged(); },
  });

  const save = () => mutation.mutate({
    label: label.trim(),
    quality,
    maxWeightKg: weight ? parseInt(weight, 10) : undefined,
  });

  return (
    <Card style={{ opacity: f.active ? 1 : 0.75, position: "relative" }}>
      {canEdit && !editing && (
        <button type="button" onClick={() => setEditing(true)} title="Modifier le libellé, la qualité ou le poids"
          aria-label={`Modifier ${f.label}`}
          style={{
            position: "absolute", top: 12, right: 12, padding: 6, lineHeight: 0,
            border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-sm)",
            background: "var(--surface-card)", color: "var(--text-secondary)", cursor: "pointer",
          }}>
          <Icons.edit size={14} />
        </button>
      )}
      <div style={{ display: "flex", gap: 14, alignItems: "flex-end", marginBottom: 12 }}>
        <div style={{ width: 56, display: "flex", justifyContent: "center", alignItems: "flex-end", minHeight: 84 }}>
          <BoxSpine spineMm={f.spineMm} heightMm={f.heightMm} retired={!f.active} />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 6 }}>
            {f.isDefault && <Badge tone="brand" size="sm" icon={<Icons.makeDefault size={11} weight="fill" />}>Par défaut</Badge>}
            {!f.active && <Badge tone="neutral" size="sm">Retiré</Badge>}
            {f.quality !== "STANDARD" && (
              <Badge tone={f.quality === "CONSERVATION" ? "success" : "info"} size="sm">{QUALITY[f.quality]}</Badge>
            )}
          </div>
          <div style={{ fontSize: 20, fontWeight: 700, lineHeight: 1.1 }}>{spineLabel(f.spineMm)}</div>
          <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
            {fmtCm(f.depthMm)} × {fmtCm(f.heightMm)} cm{f.maxWeightKg != null ? ` · ${f.maxWeightKg} kg max` : ""}
          </div>
        </div>
      </div>

      {editing ? (
        <form onSubmit={(e) => { e.preventDefault(); save(); }}>
          <Field label="Libellé" required>
            <Input value={label} onChange={(e) => setLabel(e.target.value)} required maxLength={120} />
          </Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <Field label="Qualité">
              <Select value={quality} onChange={(e) => setQuality(e.target.value as BoxQuality)}>
                {(Object.keys(QUALITY) as BoxQuality[]).map((q) => <option key={q} value={q}>{QUALITY[q]}</option>)}
              </Select>
            </Field>
            <Field label="Poids max (kg)" hint={f.maxWeightKg != null ? "Modifiable, pas effaçable" : undefined}>
              {/* Le serveur garde l'ancien poids si on n'en envoie pas : un poids posé ne peut que changer. */}
              <Input type="number" min={1} max={1000} value={weight} onChange={(e) => setWeight(e.target.value)}
                required={f.maxWeightKg != null} />
            </Field>
          </div>
          <p style={{ fontSize: 11, color: "var(--text-tertiary)", margin: "0 0 10px" }}>
            Dos et dimensions ne se modifient pas : ils mesurent des boîtes déjà rangées. Crée un autre format.
          </p>
          <div style={{ display: "flex", gap: 8 }}>
            <Button type="submit" size="sm" loading={mutation.isPending} disabled={!label.trim()}>Enregistrer</Button>
            <Button type="button" size="sm" variant="ghost" onClick={() => {
              setEditing(false); setLabel(f.label); setQuality(f.quality);
              setWeight(f.maxWeightKg != null ? String(f.maxWeightKg) : "");
            }}>Annuler</Button>
          </div>
        </form>
      ) : (
        <>
          <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 2 }}>{f.label}</div>
          <div style={{ fontSize: 11, fontFamily: "var(--font-mono)", color: "var(--text-tertiary)", marginBottom: 12 }}>{f.code}</div>
          {canEdit && (
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap", borderTop: "1px solid var(--border-subtle)", paddingTop: 10 }}>
              {f.active && !f.isDefault && (
                <Button size="sm" variant="ghost" icon={<Icons.makeDefault size={13} />} loading={mutation.isPending}
                  onClick={() => mutation.mutate({ isDefault: true })}>
                  Mettre par défaut
                </Button>
              )}
              {f.active ? (
                <Button size="sm" variant="ghost" icon={<Icons.blocked size={13} />} disabled={f.isDefault || mutation.isPending}
                  title={f.isDefault ? "Choisis d'abord un autre format par défaut" : undefined}
                  onClick={() => mutation.mutate({ active: false })}>
                  Retirer
                </Button>
              ) : (
                <Button size="sm" variant="ghost" icon={<Icons.returned size={13} />} loading={mutation.isPending}
                  onClick={() => mutation.mutate({ active: true })}>
                  Réactiver
                </Button>
              )}
            </div>
          )}
        </>
      )}
      {mutation.isError && (
        <div style={{ marginTop: 10 }}>
          <Alert tone="danger">{(mutation.error as Error).message}</Alert>
        </div>
      )}
    </Card>
  );
}

function CreateFormatCard({ onCancel, onCreated }: { onCancel: () => void; onCreated: () => void }) {
  const [spine, setSpine] = useState("10");
  const [depth, setDepth] = useState("25");
  const [height, setHeight] = useState("33");
  const [quality, setQuality] = useState<BoxQuality>("STANDARD");
  const [weight, setWeight] = useState("");
  // Code et libellé suivent les dimensions tant qu'on ne les a pas saisis soi-même.
  const [code, setCode] = useState<string | null>(null);
  const [label, setLabel] = useState<string | null>(null);

  const spineMm = toMm(spine);
  const depthMm = toMm(depth);
  const heightMm = toMm(height);
  const inRange = (mm: number, [min, max]: readonly [number, number]) =>
    Number.isFinite(mm) && mm >= min * 10 && mm <= max * 10;
  const valid = inRange(spineMm, LIMITS.spine) && inRange(depthMm, LIMITS.depth) && inRange(heightMm, LIMITS.height);

  const shownCode = code ?? suggestCode(spine, depth, height);
  const fr = (v: string) => v.trim().replace(".", ",");
  const shownLabel = label ?? `Boîte ${fr(depth)} × ${fr(height)} dos ${fr(spine)} cm`;

  const mutation = useMutation({
    mutationFn: () => createBoxFormat({
      code: shownCode.trim().toUpperCase(),
      label: shownLabel.trim(),
      spineMm, depthMm, heightMm, quality,
      maxWeightKg: weight ? parseInt(weight, 10) : undefined,
    }),
    onSuccess: onCreated,
  });

  const cmInput = (value: string, set: (v: string) => void, [min, max]: readonly [number, number]) => (
    <Input type="number" step={0.5} min={min} max={max} value={value} onChange={(e) => set(e.target.value)} required />
  );

  return (
    <Card style={{ marginBottom: 20 }}>
      <CardHeader title="Nouveau format" subtitle="Boîtes rangées debout. Les dimensions ne pourront plus changer." />
      <form onSubmit={(e) => { e.preventDefault(); if (valid) mutation.mutate(); }}>
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 160px", gap: 24, alignItems: "start" }}>
          <div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 12 }}>
              <Field label="Dos (cm)" required hint="1 à 100 cm">{cmInput(spine, setSpine, LIMITS.spine)}</Field>
              <Field label="Profondeur (cm)" required hint="5 à 150 cm">{cmInput(depth, setDepth, LIMITS.depth)}</Field>
              <Field label="Hauteur (cm)" required hint="5 à 150 cm">{cmInput(height, setHeight, LIMITS.height)}</Field>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <Field label="Qualité">
                <Select value={quality} onChange={(e) => setQuality(e.target.value as BoxQuality)}>
                  {(Object.keys(QUALITY) as BoxQuality[]).map((q) => <option key={q} value={q}>{QUALITY[q]}</option>)}
                </Select>
              </Field>
              <Field label="Poids max (kg)" hint="Facultatif">
                <Input type="number" min={1} max={1000} value={weight} onChange={(e) => setWeight(e.target.value)} />
              </Field>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1.4fr", gap: 12 }}>
              <Field label="Code" required hint="Lettres, chiffres, - et _">
                <Input value={shownCode} onChange={(e) => setCode(e.target.value.toUpperCase())} required
                  pattern="[A-Za-z0-9][A-Za-z0-9_\-]{0,31}" maxLength={32}
                  style={{ fontFamily: "var(--font-mono)" }} />
              </Field>
              <Field label="Libellé" required>
                <Input value={shownLabel} onChange={(e) => setLabel(e.target.value)} required maxLength={120} />
              </Field>
            </div>
          </div>

          <div style={{
            display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "flex-end", gap: 8,
            minHeight: 180, padding: 12, borderRadius: "var(--radius-md)",
            background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)",
          }}>
            {valid ? (
              <>
                <BoxSpine spineMm={spineMm} heightMm={heightMm} />
                <div style={{ fontWeight: 700 }}>{spineLabel(spineMm)}</div>
                <div style={{ fontSize: 11, color: "var(--text-tertiary)", textAlign: "center" }}>
                  {perMl(spineMm)} boîtes au mètre linéaire
                </div>
              </>
            ) : (
              <div style={{ fontSize: 12, color: "var(--text-tertiary)", textAlign: "center" }}>Dimensions hors limites</div>
            )}
          </div>
        </div>

        {mutation.isError && (
          <div style={{ marginBottom: 12 }}>
            <Alert tone="danger" title="Création impossible">{(mutation.error as Error).message}</Alert>
          </div>
        )}

        <div style={{ display: "flex", gap: 10 }}>
          <Button type="submit" loading={mutation.isPending} disabled={!valid} icon={<Icons.add size={15} weight="bold" />}>
            Ajouter au catalogue
          </Button>
          <Button type="button" variant="secondary" onClick={onCancel}>Annuler</Button>
        </div>
      </form>
    </Card>
  );
}
