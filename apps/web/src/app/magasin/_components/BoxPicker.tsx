"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import { Alert, Badge, Button, Field, Input } from "@/components/ui";
import { createContainer, searchContainers, type ContainerHit } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { BOX_FINISH } from "./kinds";

const DEFAULT_CAPACITY = 50;

/** Où est la boîte, en mots : adresse si rangée, sinon zone tampon ou prêt. */
export function boxWhere(hit: ContainerHit): string {
  if (hit.onLoan) return "En prêt";
  return hit.address ?? "Zone tampon";
}

/** Pourquoi une boîte ne peut pas recevoir de dossier (null = elle peut). */
export function cannotReceive(hit: ContainerHit): string | null {
  if (hit.onLoan) return "en prêt";
  if (hit.container.state === "SEALED") return "fermée";
  if (hit.container.state === "DESTROYED") return "détruite";
  if (hit.container.capacity !== null && hit.recordCount >= hit.container.capacity) return "pleine";
  return null;
}

/** Jauge de remplissage : dossiers dedans (et ceux qu'on s'apprête à ajouter) sur la capacité. */
export function Gauge({ count, adding = 0, capacity }: { count: number; adding?: number; capacity: number | null }) {
  if (capacity === null) {
    const n = count + adding;
    return <span style={{ fontSize: 12, color: "var(--text-secondary)" }}>{n} dossier{n > 1 ? "s" : ""} · sans limite</span>;
  }
  const pct = (n: number) => `${Math.min(100, (n / capacity) * 100)}%`;
  const over = count + adding > capacity;
  return (
    <div>
      <div style={{ position: "relative", height: 6, borderRadius: 3, background: "var(--surface-sunken)", overflow: "hidden" }}>
        <div style={{ position: "absolute", inset: 0, width: pct(count + adding), background: over ? "var(--danger-500)" : "var(--brand-200)" }} />
        <div style={{ position: "absolute", inset: 0, width: pct(count), background: over ? "var(--danger-500)" : "var(--brand-500)" }} />
      </div>
      <div style={{ fontSize: 12, marginTop: 4, color: over ? "var(--danger-500)" : "var(--text-secondary)", fontVariantNumeric: "tabular-nums" }}>
        {adding > 0 ? `${count} + ${adding} = ${count + adding}` : count} / {capacity} dossiers
      </div>
    </div>
  );
}

/**
 * Choisir une boîte sans l'avoir en main : recherche par libellé ou code-barres, jauge de
 * remplissage, et (si {@code allowCreate}) création d'une nouvelle boîte. {@code forContent} :
 * les boîtes qui ne peuvent pas recevoir de dossier (prêt, fermée, pleine) sont grisées.
 */
export function BoxPicker({ onPick, allowCreate = true, forContent = true }: {
  onPick: (h: ContainerHit) => void;
  allowCreate?: boolean;
  forContent?: boolean;
}) {
  const [q, setQ] = useState("");
  const [debounced, setDebounced] = useState("");
  const [creating, setCreating] = useState(false);
  useEffect(() => {
    const t = window.setTimeout(() => setDebounced(q.trim()), 250);
    return () => window.clearTimeout(t);
  }, [q]);
  const hits = useQuery({ queryKey: ["container-search", debounced], queryFn: () => searchContainers(debounced, 8) });

  if (creating) return <NewBox onCreated={onPick} onCancel={() => setCreating(false)} />;

  return (
    <div>
      <div style={{ position: "relative" }}>
        <Icons.search size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "var(--text-tertiary)" }} />
        <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Libellé ou code-barres de la boîte"
          aria-label="Chercher une boîte" style={{ paddingLeft: 36 }} />
      </div>
      <ul style={{ listStyle: "none", margin: "10px 0 0", padding: 0, display: "flex", flexDirection: "column", gap: 6 }}>
        {hits.isLoading && <li style={{ fontSize: 13, color: "var(--text-tertiary)" }}>Recherche…</li>}
        {hits.isError && <li><Alert tone="danger">{(hits.error as Error).message}</Alert></li>}
        {hits.data?.length === 0 && <li style={{ fontSize: 13, color: "var(--text-tertiary)" }}>Aucune boîte trouvée.</li>}
        {hits.data?.map((h) => {
          const why = forContent ? cannotReceive(h) : null;
          return (
            <li key={h.container.id}>
              <button type="button" disabled={!!why} onClick={() => onPick(h)} style={{
                width: "100%", textAlign: "left", display: "flex", gap: 10, alignItems: "center", padding: "8px 10px",
                borderRadius: "var(--radius-md)", border: "1px solid var(--border-subtle)", background: "var(--surface-card)",
                color: "var(--text-primary)", opacity: why ? 0.55 : 1, cursor: why ? "not-allowed" : "pointer",
              }}>
                <ArchiveBoxIso size={34} finish={BOX_FINISH[h.container.state]} />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                    {h.container.label || h.container.barcode}
                  </div>
                  <div style={{ fontSize: 11, color: "var(--text-tertiary)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                    <code>{h.container.barcode}</code> · {boxWhere(h)}
                  </div>
                </div>
                {why
                  ? <Badge tone="neutral">{why}</Badge>
                  : <span style={{ fontSize: 12, color: "var(--text-secondary)", fontVariantNumeric: "tabular-nums", whiteSpace: "nowrap" }}>
                      {h.recordCount}{h.container.capacity !== null ? ` / ${h.container.capacity}` : ""}
                    </span>}
              </button>
            </li>
          );
        })}
      </ul>
      {allowCreate && (
        <Button variant="secondary" fullWidth icon={<Icons.add size={14} />} style={{ marginTop: 10 }}
          onClick={() => setCreating(true)}>
          Nouvelle boîte
        </Button>
      )}
    </div>
  );
}

function NewBox({ onCreated, onCancel }: { onCreated: (h: ContainerHit) => void; onCancel: () => void }) {
  const [label, setLabel] = useState("");
  const [capacity, setCapacity] = useState(String(DEFAULT_CAPACITY));
  const create = useMutation({
    mutationFn: () => createContainer({ label: label.trim() || undefined, capacity: capacity ? parseInt(capacity, 10) : undefined }),
    onSuccess: (c) => onCreated({ container: c, address: null, onLoan: false, recordCount: 0 }),
  });
  return (
    <form onSubmit={(e) => { e.preventDefault(); create.mutate(); }}>
      <Field label="Libellé" hint="Court et neutre : service, versement, n° de boîte (D9). Unique.">
        <Input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="ex. DRH · versement 2026-04 · 001" autoFocus />
      </Field>
      <Field label="Capacité" hint="Nombre maximum de dossiers">
        <Input type="number" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} />
      </Field>
      {create.isError && <div style={{ marginBottom: 10 }}><Alert tone="danger">{(create.error as Error).message}</Alert></div>}
      <div style={{ display: "flex", gap: 8 }}>
        <Button type="submit" icon={<Icons.box size={14} />} loading={create.isPending}>Créer et choisir</Button>
        <Button type="button" variant="secondary" onClick={onCancel}>Annuler</Button>
      </div>
    </form>
  );
}
