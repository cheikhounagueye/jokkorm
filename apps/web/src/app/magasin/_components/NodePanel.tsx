"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Alert, Button, Card, CardHeader, IconTile, Input } from "@/components/ui";
import {
  cm,
  deleteStorageLocation,
  getStorageSpace,
  setStorageLength,
  updateStorageLocation,
  type StorageLocationKind,
  type StorageLocationTreeResponse,
} from "@/lib/api";
import { Icons } from "@/lib/icons";
import { CreateChildForm } from "./CreateChildForm";
import { aisleLabel, FurnitureCard, RoomSpecCard, TabletteDimsCard } from "./FurniturePanel";
import { KIND_META } from "./kinds";
import { LinkButton } from "./LinkButton";
import { PositionShelf } from "./PositionShelf";
import { SpaceGauge } from "./SpaceGauge";

/** Niveaux qui ont une longueur de rangement (pattern 10). */
const HAS_LENGTH: StorageLocationKind[] = ["SHELF", "RACK", "POSITION"];

export function NodePanel({
  path,
  onSelect,
  onRenamed,
  onDeleted,
  onCreated,
}: {
  /** Chemin racine → nœud sélectionné (dernier élément = sélection). */
  path: StorageLocationTreeResponse[];
  onSelect: (id: string) => void;
  onRenamed: () => void;
  onDeleted: () => void;
  onCreated: () => void;
}) {
  const current = path[path.length - 1];
  const loc = current.node;
  const meta = KIND_META[loc.kind];
  const childMeta = meta.child ? KIND_META[meta.child] : null;
  const [mode, setMode] = useState<"view" | "rename" | "delete" | "create" | "length">("view");
  const hasLength = HAS_LENGTH.includes(loc.kind);
  // Adresse de terrain (pattern 10) : « SALLE-A · Épi E1 · rang 3 · face A · Travée T01 · Tablette 2 »
  const travee = path.find((p) => p.node.kind === "SHELF")?.node ?? null;
  const roomNode = path.find((p) => p.node.kind === "STOREHOUSE")?.node ?? null;
  const aisle = travee ? aisleLabel(travee) : null;
  const address = aisle && roomNode
    ? [roomNode.code, aisle, ...path.filter((p) => p.node.kind === "SHELF" || p.node.kind === "RACK" || p.node.kind === "POSITION")
        .map((p) => `${KIND_META[p.node.kind].label} ${p.node.code}`)].join(" · ")
    : null;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Card padding={0}>
        <div style={{ padding: "12px 20px", borderBottom: "1px solid var(--border-subtle)", display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap", fontSize: 12 }}>
          {path.map((p, i) => {
            const m = KIND_META[p.node.kind];
            const last = i === path.length - 1;
            const CrumbIcon = Icons[m.icon];
            return (
              <span key={p.node.id} style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                {i > 0 && <Icons.chevronRight size={11} weight="bold" style={{ color: "var(--text-tertiary)" }} />}
                <button type="button" onClick={() => onSelect(p.node.id)} disabled={last}
                  style={{
                    display: "inline-flex", alignItems: "center", gap: 4,
                    border: "none", background: "transparent", padding: "2px 4px",
                    borderRadius: "var(--radius-sm)", fontSize: 12,
                    color: last ? "var(--text-primary)" : "var(--text-secondary)",
                    fontWeight: last ? 600 : 500, cursor: last ? "default" : "pointer", opacity: 1,
                  }}>
                  <CrumbIcon size={13} />
                  {p.node.code}
                </button>
              </span>
            );
          })}
        </div>

        <div style={{ padding: 20, display: "flex", alignItems: "center", gap: 16, flexWrap: "wrap" }}>
          <IconTile name={meta.icon} tone={meta.tone} size={52} />
          <div style={{ flex: 1, minWidth: 200 }}>
            <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)" }}>
              {meta.label}
            </div>
            <h2 style={{ fontSize: 20, marginTop: 2 }}>{loc.label || `${meta.label} ${loc.code}`}</h2>
            <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 2 }}>
              Code <code>{loc.code}</code> · Chemin <code>{loc.path}</code>
              {hasLength && <> · Longueur {loc.lengthMm != null ? <strong>{cm(loc.lengthMm)}</strong> : "déduite"}</>}
            </div>
            {address && (
              <div style={{ display: "inline-flex", alignItems: "center", gap: 6, marginTop: 6, fontSize: 12, fontWeight: 600,
                padding: "3px 10px", borderRadius: 99, background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)" }}>
                <Icons.position size={13} weight="duotone" /> {address}
              </div>
            )}
          </div>
          {mode === "view" && (
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {loc.kind === "STOREHOUSE" && (
                <LinkButton href={`/magasin/visite?room=${loc.id}`} variant="primary" icon={<Icons.virtualTour size={14} />}>
                  Visite 3D
                </LinkButton>
              )}
              {childMeta && (
                <Button icon={<Icons.add size={14} weight="bold" />} onClick={() => setMode("create")}>
                  Ajouter {childMeta.label.toLowerCase()}
                </Button>
              )}
              {hasLength && (
                <Button variant="secondary" icon={<Icons.gauge size={14} />} onClick={() => setMode("length")}>
                  Longueur
                </Button>
              )}
              <Button variant="secondary" icon={<Icons.edit size={14} />} onClick={() => setMode("rename")}>
                Renommer
              </Button>
              <Button variant="secondary" icon={<Icons.remove size={14} style={{ color: "var(--danger-500)" }} />}
                onClick={() => setMode("delete")}>
                Supprimer
              </Button>
            </div>
          )}
        </div>

        {mode === "rename" && (
          <div style={{ padding: "0 20px 20px" }}>
            <RenameForm id={loc.id} initial={loc.label ?? ""}
              onDone={() => { setMode("view"); onRenamed(); }} onCancel={() => setMode("view")} />
          </div>
        )}
        {mode === "length" && (
          <div style={{ padding: "0 20px 20px" }}>
            <LengthForm id={loc.id} kind={loc.kind} initialMm={loc.lengthMm}
              onDone={() => { setMode("view"); onRenamed(); }} onCancel={() => setMode("view")} />
          </div>
        )}
        {mode === "delete" && (
          <div style={{ padding: "0 20px 20px" }}>
            <DeleteConfirm id={loc.id} code={loc.code} path={loc.path}
              onDone={() => { setMode("view"); onDeleted(); }} onCancel={() => setMode("view")} />
          </div>
        )}
        {mode === "create" && childMeta && (
          <div style={{ padding: "16px 20px 20px", borderTop: "1px solid var(--border-subtle)", background: "var(--surface-sunken)" }}>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 12 }}>
              Nouveau/nouvelle {childMeta.label.toLowerCase()} dans <code>{loc.path}</code>
            </div>
            <CreateChildForm parent={loc} onCreated={() => { setMode("view"); onCreated(); }} onCancel={() => setMode("view")} />
          </div>
        )}
      </Card>

      {loc.kind === "STOREHOUSE" && <RoomSpecCard storehouseId={loc.id} />}
      {loc.kind === "SHELF" && <FurnitureCard node={current} />}
      {loc.kind === "RACK" && <TabletteDimsCard node={loc} travee={travee} />}
      {loc.kind === "RACK" && <TabletteSpace id={loc.id} />}
      {loc.kind === "POSITION"
        ? <PositionShelf position={loc} />
        : <ChildrenGrid node={current} onSelect={onSelect} onAdd={() => setMode("create")} />}
    </div>
  );
}

function ChildrenGrid({
  node, onSelect, onAdd,
}: {
  node: StorageLocationTreeResponse;
  onSelect: (id: string) => void;
  onAdd: () => void;
}) {
  const childMeta = KIND_META[KIND_META[node.node.kind].child!];
  return (
    <Card>
      <CardHeader
        title={`${node.children.length} ${node.children.length > 1 ? childMeta.plural : childMeta.label.toLowerCase()}`}
        subtitle={`Contenu de ${node.node.path}`}
      />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(190px, 1fr))", gap: 12 }}>
        {node.children.map((c) => (
          <button key={c.node.id} type="button" onClick={() => onSelect(c.node.id)}
            style={{
              display: "flex", alignItems: "center", gap: 12, textAlign: "left",
              padding: "14px 14px", background: "var(--surface-card)",
              border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)",
              transition: "transform 150ms ease, box-shadow 150ms ease",
            }}
            onMouseEnter={(e) => { e.currentTarget.style.transform = "translateY(-2px)"; e.currentTarget.style.boxShadow = "var(--shadow-md)"; }}
            onMouseLeave={(e) => { e.currentTarget.style.transform = "translateY(0)"; e.currentTarget.style.boxShadow = "none"; }}>
            <IconTile name={childMeta.icon} tone={childMeta.tone} size={40} />
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 14, fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                {c.node.label || `${childMeta.label} ${c.node.code}`}
              </div>
              <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>
                <span style={{ fontFamily: "var(--font-mono)" }}>{c.node.code}</span>
                {childMeta.child && ` · ${c.children.length} ${KIND_META[childMeta.child].plural}`}
              </div>
            </div>
          </button>
        ))}
        <button type="button" onClick={onAdd}
          style={{
            display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
            minHeight: 70, padding: 14, background: "transparent",
            border: "1.5px dashed var(--border-strong)", borderRadius: "var(--radius-md)",
            color: "var(--text-secondary)", fontSize: 13, fontWeight: 500,
          }}>
          <Icons.add size={16} weight="bold" />
          Ajouter {childMeta.label.toLowerCase()}
        </button>
      </div>
    </Card>
  );
}

function RenameForm({ id, initial, onDone, onCancel }: {
  id: string; initial: string; onDone: () => void; onCancel: () => void;
}) {
  const [label, setLabel] = useState(initial);
  const mutation = useMutation({
    mutationFn: () => updateStorageLocation(id, label || null),
    onSuccess: onDone,
  });
  return (
    <form onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}>
      <div style={{ display: "flex", gap: 8 }}>
        <Input value={label} onChange={(e) => setLabel(e.target.value)} autoFocus
          placeholder="Nouveau libellé (vide pour effacer)" aria-label="Nouveau libellé" style={{ flex: 1 }} />
        <Button type="submit" icon={<Icons.success size={14} />} loading={mutation.isPending}>Enregistrer</Button>
        <Button type="button" variant="ghost" onClick={onCancel}>Annuler</Button>
      </div>
      {mutation.isError && <div style={{ marginTop: 10 }}><Alert tone="danger">{(mutation.error as Error).message}</Alert></div>}
    </form>
  );
}

function DeleteConfirm({ id, code, path, onDone, onCancel }: {
  id: string; code: string; path: string; onDone: () => void; onCancel: () => void;
}) {
  const [typed, setTyped] = useState("");
  const mutation = useMutation({
    mutationFn: () => deleteStorageLocation(id),
    onSuccess: onDone,
  });
  const ok = typed.toUpperCase() === code;
  return (
    <Alert tone="danger" title="Suppression définitive">
      <p style={{ marginBottom: 10 }}>
        Retape le code <code>{code}</code> pour supprimer <code>{path}</code>.
        La suppression est refusée si le nœud contient des éléments ou des boîtes.
      </p>
      <div style={{ display: "flex", gap: 8 }}>
        <Input value={typed} onChange={(e) => setTyped(e.target.value)} autoFocus
          placeholder={`Retape « ${code} »`} aria-label="Code de confirmation" style={{ flex: 1 }} />
        <Button variant="danger" icon={<Icons.remove size={14} />}
          disabled={!ok} loading={mutation.isPending} onClick={() => mutation.mutate()}>
          Supprimer
        </Button>
        <Button variant="ghost" onClick={onCancel}>Annuler</Button>
      </div>
      {mutation.isError && (
        <div style={{ marginTop: 8, color: "var(--danger-700)", fontWeight: 500 }}>{(mutation.error as Error).message}</div>
      )}
    </Alert>
  );
}

const LENGTH_HINT: Partial<Record<StorageLocationKind, string>> = {
  SHELF: "Largeur de la travée entre montants. Les tablettes sans longueur propre en héritent.",
  RACK: "Longueur utile de la tablette. Vide : celle de la travée, sinon 100 cm.",
  POSITION: "Vide : part égale de la tablette. La somme des emplacements ne dépasse pas la tablette.",
};

function LengthForm({ id, kind, initialMm, onDone, onCancel }: {
  id: string; kind: StorageLocationKind; initialMm: number | null; onDone: () => void; onCancel: () => void;
}) {
  const qc = useQueryClient();
  const [value, setValue] = useState(initialMm != null ? String(initialMm / 10) : "");
  const mutation = useMutation({
    mutationFn: () => {
      const v = value.trim().replace(",", ".");
      return setStorageLength(id, v === "" ? null : Math.round(parseFloat(v) * 10));
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["space"] });
      qc.invalidateQueries({ queryKey: ["room"] });
      onDone();
    },
  });
  return (
    <form onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <Input value={value} onChange={(e) => setValue(e.target.value)} autoFocus inputMode="decimal"
          placeholder="ex. 100" aria-label="Longueur en centimètres" style={{ width: 140 }} />
        <span style={{ fontSize: 13, color: "var(--text-secondary)" }}>cm</span>
        <Button type="submit" loading={mutation.isPending}>Enregistrer</Button>
        <Button type="button" variant="ghost" onClick={onCancel}>Annuler</Button>
      </div>
      <p style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 6 }}>{LENGTH_HINT[kind]}</p>
      {mutation.isError && <Alert tone="danger">{(mutation.error as Error).message}</Alert>}
    </form>
  );
}

function TabletteSpace({ id }: { id: string }) {
  const space = useQuery({ queryKey: ["space", id], queryFn: () => getStorageSpace(id) });
  if (!space.data) return null;
  const sp = space.data;
  return (
    <Card>
      <CardHeader title="Place sur la tablette" subtitle={`${sp.boxes} boîte${sp.boxes > 1 ? "s" : ""} · marge de manipulation ${sp.marginPercent} %`} />
      <SpaceGauge lengthMm={sp.lengthMm} usedMm={sp.usedMm} placeableMm={sp.placeableMm}
        marginPercent={sp.marginPercent} fits={sp.fits} segments={[]} />
    </Card>
  );
}
