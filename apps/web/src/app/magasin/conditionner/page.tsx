"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo, useState } from "react";
import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import { Alert, Button, Card, CardHeader, EmptyState, PageHeader, StatusBadge } from "@/components/ui";
import {
  conditionRecords, listPendingConditioning, refusalsOf, type ConditioningRefusal, type ContainerHit,
  type MasterFacts,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons } from "@/lib/icons";
import { useRole } from "../../demandes/_components/useRole";
import { ARCHIVE_STATUS } from "../../records/_components/archiveStatus";
import { BoxPicker, Gauge, boxWhere } from "../_components/BoxPicker";
import { BOX_FINISH, etiquetteHref } from "../_components/kinds";

/**
 * À conditionner (étude du 24 novembre 2026, lot 1) : les dossiers enregistrés qui ne sont
 * dans aucune boîte. On coche, on choisit une boîte (existante ou nouvelle), on met en boîte
 * d'un geste — tout ou rien. La boîte peut rester en zone tampon : on la placera ensuite.
 */

const CRUMBS = [{ label: "Salle d'archives", href: "/magasin" }, { label: "À conditionner" }];
const PAGE_SIZE = 50;

type Done = { box: ContainerHit; count: number };

export default function ConditionnerPage() {
  const qc = useQueryClient();
  const role = useRole();
  const canAct = role === "ARCHIVIST" || role === "ARCHIVES_HEAD";
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [box, setBox] = useState<ContainerHit | null>(null);
  const [done, setDone] = useState<Done | null>(null);
  const [refusals, setRefusals] = useState<ConditioningRefusal[]>([]);

  const pending = useQuery({
    queryKey: ["conditioning-pending", page],
    queryFn: () => listPendingConditioning(page, PAGE_SIZE),
    enabled: role !== null,
  });
  const items = pending.data?.items ?? [];
  const selectable = items.filter((r) => r.status !== "FROZEN");
  const allChecked = selectable.length > 0 && selectable.every((r) => selected.has(r.cote));

  const toggle = (cote: string) => setSelected((s) => {
    const next = new Set(s);
    if (next.has(cote)) next.delete(cote); else next.add(cote);
    return next;
  });
  const toggleAll = () => setSelected((s) => {
    const next = new Set(s);
    if (allChecked) selectable.forEach((r) => next.delete(r.cote));
    else selectable.forEach((r) => next.add(r.cote));
    return next;
  });

  const capacity = box?.container.capacity ?? null;
  const after = (box?.recordCount ?? 0) + selected.size;
  const overflow = capacity !== null && after > capacity;

  const mutation = useMutation({
    mutationFn: () => conditionRecords(box!.container.barcode, [...selected]),
    onSuccess: (r) => {
      setDone({ box: { ...box!, container: r.container, recordCount: r.recordCount }, count: r.stored.length });
      setBox({ ...box!, container: r.container, recordCount: r.recordCount });
      setSelected(new Set());
      setRefusals([]);
      void qc.invalidateQueries({ queryKey: ["conditioning-pending"] });
      void qc.invalidateQueries({ queryKey: ["container-search"] });
      void qc.invalidateQueries({ queryKey: ["records"] });
    },
    onError: (e) => { setRefusals(refusalsOf(e)); setDone(null); },
  });

  const total = pending.data?.total ?? 0;
  const subtitle = pending.data
    ? total === 0 ? "Tous les dossiers enregistrés sont en boîte."
      : `${total} dossier${total > 1 ? "s" : ""} dans aucune boîte, les plus anciens d'abord`
    : "Dossiers enregistrés qui attendent d'être mis en boîte";

  return (
    <section className="fade-up">
      <PageHeader icon="conditioning" tone="kraft" title="À conditionner" subtitle={subtitle} crumbs={CRUMBS} />

      {pending.isError && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="danger" title="Impossible de charger la liste">{(pending.error as Error).message}</Alert>
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 380px", gap: 20, alignItems: "start" }}>
        {/* Dossiers */}
        <Card padding={0} style={{ overflow: "hidden" }}>
          {pending.isLoading && <p style={{ padding: 16, color: "var(--text-tertiary)", fontSize: 13 }}>Chargement…</p>}
          {pending.data && items.length === 0 && (
            <EmptyState icon={<Icons.success size={32} />} title="Rien à conditionner"
              description="Chaque dossier enregistré est déjà dans une boîte." />
          )}
          {items.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th style={{ width: 40 }}>
                    <input type="checkbox" aria-label="Tout cocher sur cette page" checked={allChecked}
                      disabled={!canAct || selectable.length === 0} onChange={toggleAll} />
                  </th>
                  <th>Cote</th>
                  <th>Statut</th>
                  <th title="Service producteur, selon la GED maître">Service</th>
                  <th title="Sort final, selon la GED maître">Sort final</th>
                  <th title="Date à laquelle le sort final s'applique, selon la GED maître">Échéance</th>
                  <th>Enregistrée le</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((r) => {
                  const frozen = r.status === "FROZEN";
                  const meta = ARCHIVE_STATUS[r.status];
                  const facts = pending.data?.master[r.id];
                  const unknown = pending.data?.standalone ? "sans maître" : "pas encore lu";
                  return (
                    <tr key={r.id} onClick={() => canAct && !frozen && toggle(r.cote)}
                      style={{ cursor: canAct && !frozen ? "pointer" : "default",
                        background: selected.has(r.cote) ? "var(--brand-50)" : undefined }}>
                      <td onClick={(e) => e.stopPropagation()}>
                        <input type="checkbox" aria-label={`Choisir ${r.cote}`} checked={selected.has(r.cote)}
                          disabled={!canAct || frozen} onChange={() => toggle(r.cote)}
                          title={frozen ? "Sous gel : à mettre en boîte après la levée du gel" : undefined} />
                      </td>
                      <td><code>{r.cote}</code></td>
                      <td>
                        <StatusBadge meta={meta} />
                        {frozen && <span style={{ marginLeft: 8, fontSize: 12, color: "var(--text-tertiary)" }}>attend la levée du gel</span>}
                      </td>
                      <MasterCell value={facts?.serviceCode ?? null} missing={facts ? "non indiqué" : unknown} code />
                      <MasterCell value={facts ? fateLabel(facts) : null} missing={facts ? "non indiqué" : unknown} />
                      <MasterCell value={facts?.disposalDate ? formatDay(facts.disposalDate) : null}
                        missing={facts ? "non indiquée" : unknown} />
                      <td style={{ color: "var(--text-secondary)", whiteSpace: "nowrap" }}>{formatInstant(r.registeredAt)}</td>
                      <td style={{ textAlign: "right" }}>
                        <Link href={`/records/detail?cote=${encodeURIComponent(r.cote)}`} onClick={(e) => e.stopPropagation()}
                          style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 13, fontWeight: 600 }}>
                          Détail <Icons.chevronRight size={14} weight="bold" />
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
          {pending.data && total > PAGE_SIZE && (
            <footer style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px",
              borderTop: "1px solid var(--border-subtle)", fontSize: 13, color: "var(--text-secondary)" }}>
              <span>Page {page + 1} / {Math.ceil(total / PAGE_SIZE)}</span>
              <div style={{ display: "flex", gap: 8 }}>
                <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Précédent</Button>
                <Button variant="secondary" size="sm" disabled={(page + 1) * PAGE_SIZE >= total}
                  onClick={() => setPage(page + 1)}>Suivant</Button>
              </div>
            </footer>
          )}
        </Card>

        {/* Boîte */}
        <div style={{ position: "sticky", top: 84, display: "flex", flexDirection: "column", gap: 16 }}>
          <Card>
            <CardHeader title="Boîte" subtitle={box ? undefined : "Existante, ou nouvelle si aucune ne convient"} />
            {!canAct && role !== null && (
              <Alert tone="info">La mise en boîte est réservée à l&apos;archiviste et au responsable des archives.</Alert>
            )}
            {canAct && (box
              ? <ChosenBox hit={box} adding={selected.size} onChange={() => { setBox(null); setDone(null); setRefusals([]); }} />
              : <BoxPicker onPick={(h) => { setBox(h); setDone(null); setRefusals([]); }} />)}

            {canAct && box && (
              <div style={{ marginTop: 14 }}>
                {overflow && (
                  <div style={{ marginBottom: 10 }}>
                    <Alert tone="warning">
                      Trop de dossiers pour cette boîte : {after} pour {capacity} places. Décoche-en ou prends une autre boîte.
                    </Alert>
                  </div>
                )}
                <Button fullWidth size="lg" icon={<Icons.box size={16} />} loading={mutation.isPending}
                  disabled={selected.size === 0 || overflow} onClick={() => mutation.mutate()}>
                  {selected.size === 0 ? "Coche des dossiers"
                    : `Mettre ${selected.size} dossier${selected.size > 1 ? "s" : ""} en boîte`}
                </Button>
              </div>
            )}
          </Card>

          {mutation.isError && (
            <Alert tone="danger" title="Rien n'a été mis en boîte">
              {refusals.length === 0 ? (mutation.error as Error).message : (
                <ul style={{ margin: "6px 0 0", paddingLeft: 18 }}>
                  {refusals.map((f, i) => <li key={i}>{f.message}</li>)}
                </ul>
              )}
            </Alert>
          )}

          {done && <DoneCard done={done} />}
        </div>
      </div>
    </section>
  );
}

function ChosenBox({ hit, adding, onChange }: { hit: ContainerHit; adding: number; onChange: () => void }) {
  const c = hit.container;
  return (
    <div>
      <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
        <ArchiveBoxIso size={56} finish={BOX_FINISH[c.state]} />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ fontWeight: 700, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{c.label || c.barcode}</div>
          <code style={{ fontSize: 12, color: "var(--text-secondary)" }}>{c.barcode}</code>
          <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 2 }}>{boxWhere(hit)}</div>
        </div>
        <Button variant="secondary" size="sm" onClick={onChange}>Changer</Button>
      </div>
      <div style={{ marginTop: 12 }}><Gauge count={hit.recordCount} adding={adding} capacity={c.capacity} /></div>
    </div>
  );
}

function DoneCard({ done }: { done: Done }) {
  const c = done.box.container;
  const inBuffer = !done.box.address && !done.box.onLoan;
  const href = useMemo(() => etiquetteHref(c.barcode, c.label), [c.barcode, c.label]);
  return (
    <Card style={{ background: "var(--success-50)", borderColor: "var(--success-500)" }}>
      <div style={{ display: "flex", gap: 10, alignItems: "flex-start" }}>
        <Icons.success size={22} weight="fill" style={{ color: "var(--success-500)", flexShrink: 0 }} />
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 700 }}>
            {done.count} dossier{done.count > 1 ? "s" : ""} mis en boîte
          </div>
          <div style={{ fontSize: 13, marginTop: 2 }}>
            {c.label || c.barcode} : {done.box.recordCount}{c.capacity !== null ? ` / ${c.capacity}` : ""} dossiers.
            {inBuffer && " Elle est en zone tampon : range-la avec « Placer » (téléphone, 2 scans) ou depuis la Salle d'archives."}
          </div>
          <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
            <Link href={href}><Button variant="secondary" size="sm" icon={<Icons.label size={14} />}>Étiquette de la boîte</Button></Link>
          </div>
        </div>
      </div>
    </Card>
  );
}

const FATES: Record<string, string> = {
  DESTRUCTION: "Destruction",
  CONSERVATION: "Conservation",
  VERSEMENT_AN: "Versement",
};

/** Sort final lisible ; une valeur que JokkoRM ne sait pas traduire est montrée telle quelle. */
function fateLabel(f: MasterFacts): string | null {
  if (f.finalFate) return FATES[f.finalFate] ?? f.finalFate;
  return f.finalFateSource ? `« ${f.finalFateSource} » (non reconnu)` : null;
}

/** AAAA-MM-JJ → JJ/MM/AAAA, sans fuseau (une échéance est un jour, pas un instant). */
function formatDay(iso: string): string {
  const [y, m, d] = iso.split("-");
  return d && m && y ? `${d}/${m}/${y}` : iso;
}

/** Donnée lue chez le maître (copie locale) ; absente = dit pourquoi, en discret. */
function MasterCell({ value, missing, code }: { value: string | null; missing: string; code?: boolean }) {
  if (!value) {
    return <td style={{ color: "var(--text-tertiary)", fontSize: 12, whiteSpace: "nowrap" }}>{missing}</td>;
  }
  return <td style={{ whiteSpace: "nowrap" }}>{code ? <code>{value}</code> : value}</td>;
}
