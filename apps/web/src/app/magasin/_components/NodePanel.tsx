"use client";

import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Alert, Button, Card, CardHeader, IconTile, Input } from "@/components/ui";
import {
  deleteStorageLocation,
  updateStorageLocation,
  type StorageLocationTreeResponse,
} from "@/lib/api";
import { Icons } from "@/lib/icons";
import { CreateChildForm } from "./CreateChildForm";
import { KIND_META } from "./kinds";
import { LinkButton } from "./LinkButton";
import { PositionShelf } from "./PositionShelf";

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
  const [mode, setMode] = useState<"view" | "rename" | "delete" | "create">("view");

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
            </div>
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
