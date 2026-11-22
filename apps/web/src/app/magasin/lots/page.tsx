"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { ArchiveBoxIso, type BoxFinish } from "@/components/illustrations/ArchiveBoxIso";
import {
  Alert,
  BATCH_STATUS,
  Button,
  Card,
  EmptyState,
  KpiCard,
  PageHeader,
  StatusBadge,
} from "@/components/ui";
import { getBufferZoneAlerts, listBatches, type BatchStatus } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons } from "@/lib/icons";

/** Rendu de la boîte d'illustration selon l'avancement du lot (pas un libellé de statut). */
const BOX_FINISH: Record<BatchStatus, BoxFinish> = {
  CREATED: "empty",
  PRINTED: "kraft",
  FULLY_DEPLOYED: "kraft",
  ARCHIVED: "empty",
};

const PATTERN_06_URL =
  "https://github.com/cheikhounagueye/jokkorm/blob/main/docs/pattern/06-provisioning-masse-boites.md";

export default function LotsPage() {
  const query = useQuery({ queryKey: ["batches"], queryFn: () => listBatches() });
  const alertsQuery = useQuery({
    queryKey: ["buffer-alerts"],
    queryFn: getBufferZoneAlerts,
    refetchInterval: 60_000,
  });

  const batches = query.data ?? [];
  const alerts = alertsQuery.data;
  const staleBatchIds = new Set(alerts?.byBatch.map((e) => e.batchId) ?? []);
  const totalBoxes = batches.reduce((sum, b) => sum + b.size, 0);
  const deployed = batches.filter((b) => b.status === "FULLY_DEPLOYED").length;
  const stale = alerts?.totalStaleContainers ?? 0;

  return (
    <section className="fade-up">
      <PageHeader
        icon="batches"
        tone="kraft"
        title="Lots de boîtes"
        subtitle="Provisionnement en masse, impression des étiquettes et suivi du rangement."
        crumbs={[{ label: "Salle d'archives", href: "/magasin" }, { label: "Lots de boîtes" }]}
        actions={
          <>
            <a href={PATTERN_06_URL} target="_blank" rel="noreferrer">
              <Button variant="ghost" icon={<Icons.link size={16} />}>Pattern 06</Button>
            </a>
            <Link href="/magasin/lots/new">
              <Button icon={<Icons.add size={16} weight="bold" />}>Nouveau lot</Button>
            </Link>
          </>
        }
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", gap: 16, marginBottom: 20 }}>
        <KpiCard label="Lots" value={batches.length} icon="batches" tone="brand" />
        <KpiCard label="Boîtes provisionnées" value={totalBoxes} icon="box" tone="info" />
        <KpiCard label="En zone tampon" value={stale} icon="warning" tone={stale > 0 ? "warning" : "neutral"} />
        <KpiCard label="Lots déployés" value={deployed} icon="success" tone={deployed > 0 ? "success" : "neutral"} />
      </div>

      {alerts && alerts.totalStaleContainers > 0 && (
        <div style={{ marginBottom: 20 }}>
          <Alert
            tone="warning"
            title={`${alerts.totalStaleContainers} boîte${alerts.totalStaleContainers > 1 ? "s" : ""} en zone tampon depuis plus de ${alerts.thresholdDays} jour${alerts.thresholdDays > 1 ? "s" : ""}`}
          >
            Provisionnées mais jamais rangées à une position physique : range-les, ou décide d&apos;un retour prestataire.
            <ul style={{ margin: "8px 0 0", paddingLeft: 18 }}>
              {alerts.byBatch.slice(0, 5).map((e) => (
                <li key={e.batchId} style={{ marginBottom: 2 }}>
                  <Link href={`/magasin/lots/${e.batchId}`} style={{ fontWeight: 600, color: "var(--warning-700)" }}>
                    {e.batchReference}
                  </Link>
                  {" — "}
                  {e.staleCount} boîte{e.staleCount > 1 ? "s" : ""} · la plus ancienne depuis le {formatInstant(e.oldestCreatedAt)}
                </li>
              ))}
              {alerts.byBatch.length > 5 && (
                <li>… et {alerts.byBatch.length - 5} lot{alerts.byBatch.length - 5 > 1 ? "s" : ""} de plus</li>
              )}
            </ul>
          </Alert>
        </div>
      )}

      {query.isError && (
        <div style={{ marginBottom: 20 }}>
          <Alert tone="danger" title="Impossible de charger les lots">{(query.error as Error).message}</Alert>
        </div>
      )}

      {query.isLoading && (
        <Card><p style={{ color: "var(--text-tertiary)", fontSize: 13 }}>Chargement des lots…</p></Card>
      )}

      {query.data && batches.length === 0 && (
        <Card>
          <EmptyState
            icon={<ArchiveBoxIso size={64} finish="empty" />}
            title="Aucun lot pour l'instant"
            description="Provisionne un premier lot : chaque boîte reçoit son code-barres, puis tu imprimes toutes les étiquettes d'un coup."
            action={
              <Link href="/magasin/lots/new">
                <Button icon={<Icons.add size={16} weight="bold" />}>Créer un lot</Button>
              </Link>
            }
          />
        </Card>
      )}

      {batches.length > 0 && (
        <Card padding={0} style={{ overflow: "hidden" }}>
          <table>
            <thead>
              <tr>
                <th>Lot</th>
                <th>Boîtes</th>
                <th>Statut</th>
                <th>Créé le</th>
                <th>Dernière impression</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {batches.map((b) => {
                const isStale = staleBatchIds.has(b.id);
                return (
                  <tr key={b.id}>
                    <td>
                      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                        <ArchiveBoxIso size={42} finish={isStale ? "stale" : BOX_FINISH[b.status]}
                          title={isStale ? "Boîtes en zone tampon" : undefined} />
                        <div style={{ minWidth: 0 }}>
                          <Link href={`/magasin/lots/${b.id}`} style={{ fontWeight: 600, color: "var(--text-primary)" }}>
                            {b.reference}
                          </Link>
                          <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>
                            par {b.createdBy}
                            {b.labelPattern && <> · « {b.labelPattern} »</>}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span style={{ fontWeight: 600 }}>{b.size}</span>
                      <span style={{ color: "var(--text-tertiary)" }}> boîte{b.size > 1 ? "s" : ""}</span>
                    </td>
                    <td><StatusBadge meta={BATCH_STATUS[b.status]} /></td>
                    <td style={{ color: "var(--text-secondary)" }}>{formatInstant(b.createdAt)}</td>
                    <td style={{ color: b.lastPrintedAt ? "var(--text-secondary)" : "var(--text-tertiary)" }}>
                      {b.lastPrintedAt ? formatInstant(b.lastPrintedAt) : "Jamais"}
                    </td>
                    <td style={{ textAlign: "right" }}>
                      <Link href={`/magasin/lots/${b.id}`} aria-label={`Ouvrir le lot ${b.reference}`}>
                        <Button variant="secondary" size="sm" iconRight={<Icons.chevronRight size={12} weight="bold" />}>
                          Ouvrir
                        </Button>
                      </Link>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </Card>
      )}
    </section>
  );
}
