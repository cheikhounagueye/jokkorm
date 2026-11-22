"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { ArchiveBoxIso, type BoxFinish } from "@/components/illustrations/ArchiveBoxIso";
import {
  Alert,
  BATCH_STATUS,
  Button,
  CONTAINER_STATE,
  Card,
  CardHeader,
  PageHeader,
  Segmented,
  StatusBadge,
} from "@/components/ui";
import { downloadBatchLabelsPdf, getBatchHealth } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons } from "@/lib/icons";
import { BoxPile } from "../_components/BoxPile";
import { AVERY_TEMPLATES, SheetPreview, type AveryTemplate } from "../_components/LabelSheet";

type Symbology = "CODE128" | "QR";

const CRUMBS_BASE = [
  { label: "Salle d'archives", href: "/magasin" },
  { label: "Lots de boîtes", href: "/magasin/lots" },
];

export default function LotDetailPage() {
  const params = useParams<{ id: string }>();
  const batchId = params.id;

  const [template, setTemplate] = useState<AveryTemplate>("AVERY_L7160");
  const [symbology, setSymbology] = useState<Symbology>("CODE128");
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["batch-health", batchId],
    queryFn: () => getBatchHealth(batchId),
    refetchInterval: 15_000,
  });

  async function handleDownload() {
    setDownloading(true);
    setDownloadError(null);
    try {
      await downloadBatchLabelsPdf(batchId, template, symbology);
      // Après succès, re-fetch health pour afficher firstPrintedAt
      query.refetch();
    } catch (e) {
      setDownloadError((e as Error).message);
    } finally {
      setDownloading(false);
    }
  }

  if (query.isLoading || query.isError || !query.data) {
    return (
      <section>
        <PageHeader icon="batches" tone="kraft" title="Lot de boîtes" crumbs={[...CRUMBS_BASE, { label: "Lot" }]} />
        {query.isError ? (
          <Alert tone="danger" title="Lot introuvable"
            action={<Link href="/magasin/lots"><Button variant="secondary" size="sm">Retour aux lots</Button></Link>}>
            {(query.error as Error).message}
          </Alert>
        ) : (
          <Card><p style={{ color: "var(--text-tertiary)", fontSize: 13 }}>Chargement du lot…</p></Card>
        )}
      </section>
    );
  }

  const h = query.data;
  const b = h.batch;
  const t = AVERY_TEMPLATES[template];
  const pages = Math.ceil(b.size / t.perPage);
  const done = h.deploymentPercent === 100;

  const states: { key: string; label: string; value: number; finish: BoxFinish; color: string }[] = [
    { key: "transit", label: "En transit", value: h.inTransit, finish: "stale", color: "var(--warning-500)" },
    { key: "empty", label: "Vides rangées", value: h.storedEmpty, finish: "empty", color: toneColor(CONTAINER_STATE.EMPTY.tone) },
    { key: "open", label: "Ouvertes", value: h.open, finish: "kraft", color: toneColor(CONTAINER_STATE.OPEN.tone) },
    { key: "sealed", label: "Scellées", value: h.sealed, finish: "sealed", color: toneColor(CONTAINER_STATE.SEALED.tone) },
    { key: "destroyed", label: "Détruites", value: h.destroyed, finish: "destroyed", color: toneColor(CONTAINER_STATE.DESTROYED.tone) },
  ];

  return (
    <section className="fade-up">
      <PageHeader
        icon="batches"
        tone="kraft"
        title={b.reference}
        subtitle={<>{b.size} boîte{b.size > 1 ? "s" : ""} · créé le {formatInstant(b.createdAt)} par <code>{b.createdBy}</code></>}
        crumbs={[...CRUMBS_BASE, { label: b.reference }]}
        actions={<StatusBadge meta={BATCH_STATUS[b.status]} />}
      />

      {/* Déploiement */}
      <Card style={{ marginBottom: 20 }}>
        <div style={{ display: "flex", gap: 32, alignItems: "center", flexWrap: "wrap" }}>
          <div style={{ flex: "1 1 360px", minWidth: 0 }}>
            <CardHeader
              title="Déploiement en magasin"
              subtitle={<><strong style={{ color: "var(--text-primary)" }}>{h.deployed}</strong> sur {h.total} boîtes rangées à une position</>}
              action={
                <span style={{ fontSize: 28, fontWeight: 700, letterSpacing: "-0.02em", color: done ? "var(--success-500)" : "var(--brand-500)" }}>
                  {h.deploymentPercent}%
                </span>
              }
            />
            <div role="progressbar" aria-valuenow={h.deploymentPercent} aria-valuemin={0} aria-valuemax={100}
              style={{ height: 10, background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-full)", overflow: "hidden" }}>
              <div style={{
                width: `${h.deploymentPercent}%`, height: "100%",
                background: done ? "var(--success-500)" : "linear-gradient(90deg, var(--brand-300), var(--brand-500))",
                borderRadius: "var(--radius-full)",
                transition: "width 300ms ease",
              }} />
            </div>
            {h.inTransit > 0 && (
              <p style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 10, display: "flex", alignItems: "center", gap: 6 }}>
                <Icons.warning size={14} style={{ color: "var(--warning-500)" }} />
                {h.inTransit} boîte{h.inTransit > 1 ? "s" : ""} encore en zone tampon : scanne-les à leur position pour les ranger.
              </p>
            )}
          </div>
          <div style={{ flex: "0 1 auto" }}>
            <BoxPile
              count={h.total}
              max={24}
              perRow={8}
              size={34}
              finishAt={(i) => (i < h.deployed ? "kraft" : "stale")}
            />
          </div>
        </div>
      </Card>

      {/* États des conteneurs */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(5, minmax(0, 1fr))", gap: 12, marginBottom: 20 }}>
        {states.map((s) => (
          <Card key={s.key} padding={16} style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <ArchiveBoxIso size={46} finish={s.finish} />
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 24, fontWeight: 700, lineHeight: 1.1, letterSpacing: "-0.02em", color: s.value > 0 ? s.color : "var(--text-tertiary)" }}>
                {s.value}
              </div>
              <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--text-secondary)" }}>
                {s.label}
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* Impression */}
      <Card>
        <div style={{ display: "flex", gap: 32, alignItems: "flex-start", flexWrap: "wrap" }}>
          <div style={{ flex: "1 1 420px", minWidth: 0 }}>
            <CardHeader title="Impression des étiquettes" subtitle="PDF A4 prêt à imprimer sur planches autocollantes Avery." />

            <div style={{ fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", marginBottom: 6 }}>Planche</div>
            <Segmented<AveryTemplate>
              value={template}
              onChange={setTemplate}
              options={(Object.keys(AVERY_TEMPLATES) as AveryTemplate[]).map((k) => ({
                value: k,
                label: `${AVERY_TEMPLATES[k].code} · ${AVERY_TEMPLATES[k].perPage}/A4`,
              }))}
            />
            <div style={{ fontSize: 12, color: "var(--text-tertiary)", margin: "6px 0 16px" }}>
              Étiquettes de {t.dims} · {t.cols} colonnes × {t.rows} lignes
            </div>

            <div style={{ fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", marginBottom: 6 }}>Symbologie</div>
            <Segmented<Symbology>
              value={symbology}
              onChange={setSymbology}
              options={[
                { value: "CODE128", label: "Code128 · douchette", icon: <Icons.barcode size={16} /> },
                { value: "QR", label: "QR · smartphone", icon: <Icons.qr size={16} /> },
              ]}
            />

            <div style={{ display: "flex", alignItems: "center", gap: 14, marginTop: 22, flexWrap: "wrap" }}>
              <Button size="lg" onClick={handleDownload} loading={downloading} icon={<Icons.download size={16} weight="bold" />}>
                Télécharger le PDF · {pages} page{pages > 1 ? "s" : ""} A4
              </Button>
              {b.firstPrintedAt && (
                <span style={{ fontSize: 12, color: "var(--text-tertiary)", display: "inline-flex", alignItems: "center", gap: 6 }}>
                  <Icons.history size={14} />
                  Imprimé le {formatInstant(b.firstPrintedAt)}
                  {b.lastPrintedAt && b.lastPrintedAt !== b.firstPrintedAt && <> · dernière fois le {formatInstant(b.lastPrintedAt)}</>}
                </span>
              )}
            </div>
            {downloadError && (
              <div style={{ marginTop: 14 }}>
                <Alert tone="danger" title="Génération du PDF impossible">{downloadError}</Alert>
              </div>
            )}
          </div>

          <div style={{ flex: "0 0 auto", textAlign: "center" }}>
            <SheetPreview template={template} filled={b.size} width={150} />
            <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 8 }}>Page 1 sur {pages}</div>
          </div>
        </div>
      </Card>
    </section>
  );
}

function toneColor(tone: string): string {
  return tone === "neutral" ? "var(--text-primary)" : `var(--${tone}-500)`;
}
