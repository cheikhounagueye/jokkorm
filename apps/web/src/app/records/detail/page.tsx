"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, type ReactNode } from "react";
import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import {
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  IconTile,
  PageHeader,
  StatusBadge,
} from "@/components/ui";
import {
  getConsolidatedRecord,
  type ConsolidatedRecordResponse,
  type MasterStatus,
  type RecordResponse,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons } from "@/lib/icons";
import { ARCHIVE_STATUS } from "../_components/archiveStatus";

/**
 * useSearchParams() doit être wrappé dans un boundary Suspense en Next 15 pour
 * ne pas bailout le static rendering.
 */
export default function RecordDetailPage() {
  return (
    <Suspense fallback={<p style={{ color: "var(--text-tertiary)" }}>Chargement…</p>}>
      <RecordDetail />
    </Suspense>
  );
}

function RecordDetail() {
  const params = useSearchParams();
  const cote = params.get("cote") ?? "";

  const query = useQuery({
    enabled: cote.length > 0,
    queryKey: ["consolidated", cote],
    queryFn: () => getConsolidatedRecord(cote),
  });

  const crumbs = [{ label: "Accueil", href: "/" }, { label: "Archives", href: "/records" }, { label: cote || "—" }];

  if (!cote) {
    return (
      <section className="fade-up">
        <PageHeader icon="record" tone="brand" title="Fiche d'archive" crumbs={crumbs} />
        <Card>
          <EmptyState icon={<Icons.record size={32} />} title="Aucune cote fournie"
            action={<Link href="/records"><Button variant="secondary">Retour à la liste</Button></Link>} />
        </Card>
      </section>
    );
  }

  return (
    <section className="fade-up">
      <PageHeader
        icon="record"
        tone="brand"
        title={<span style={{ fontFamily: "var(--font-mono)", letterSpacing: 0 }}>{cote}</span>}
        subtitle="Une seule archive, deux gardiens : le physique chez JokkoRM, le numérique chez la GED maître."
        crumbs={crumbs}
        actions={
          <Link href="/records">
            <Button variant="secondary" icon={<Icons.returned size={16} />}>Retour à la liste</Button>
          </Link>
        }
      />

      {query.isLoading && (
        <Card><div style={{ color: "var(--text-tertiary)", fontSize: 13 }}>Chargement de la fiche…</div></Card>
      )}
      {query.isError && (
        <Alert tone="danger" title="Fiche introuvable">{(query.error as Error).message}</Alert>
      )}

      {query.data && (
        <>
          <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1fr)", gap: 16, alignItems: "stretch" }}>
            <PhysicalPanel data={query.data.local} />
            <MasterPanel data={query.data.master} />
          </div>
          <div style={{ marginTop: 16 }}>
            <Alert tone="info" title="Pattern « une seule archive, deux gardiens »">
              Chaque dossier a deux supports et deux responsables : le physique est détenu par JokkoRM,
              le numérique par la GED/SAE maître.{" "}
              <a href="https://github.com/cheikhounagueye/jokkorm/tree/main/docs/pattern"
                target="_blank" rel="noreferrer" style={{ fontWeight: 600 }}>Lire la spécification</a>.
            </Alert>
          </div>
        </>
      )}
    </section>
  );
}

function PanelHeader({ icon, tone, title, owner }: {
  icon: Parameters<typeof IconTile>[0]["name"];
  tone: Parameters<typeof IconTile>[0]["tone"];
  title: string;
  owner: string;
}) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 18 }}>
      <IconTile name={icon} tone={tone} size={40} />
      <div>
        <h2>{title}</h2>
        <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>{owner}</div>
      </div>
    </div>
  );
}

function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "160px 1fr", gap: 12, padding: "10px 0", borderTop: "1px solid var(--border-subtle)", alignItems: "center" }}>
      <dt style={{ fontSize: 12, fontWeight: 600, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.04em" }}>{label}</dt>
      <dd style={{ margin: 0, fontSize: 14, color: "var(--text-primary)", minWidth: 0 }}>{children}</dd>
    </div>
  );
}

function PhysicalPanel({ data }: { data: RecordResponse }) {
  const meta = ARCHIVE_STATUS[data.status];
  const finish = data.status === "DESTROYED" ? "destroyed" : data.status === "FROZEN" ? "sealed" : data.containerId ? "kraft" : "empty";
  return (
    <Card>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
        <PanelHeader icon="box" tone="kraft" title="Partie physique" owner="Détenue par JokkoRM" />
        <ArchiveBoxIso size={72} finish={finish} title={meta.label} />
      </div>
      <dl style={{ margin: 0 }}>
        <InfoRow label="Statut"><StatusBadge meta={meta} /></InfoRow>
        <InfoRow label="Rangement">
          {data.containerId
            ? <Badge tone="brand" icon={<Icons.box size={12} weight="bold" />}>Dans une boîte</Badge>
            : <span style={{ color: "var(--text-tertiary)" }}>Pas encore rangée</span>}
        </InfoRow>
        <InfoRow label="Enregistrée le">{formatInstant(data.registeredAt)}</InfoRow>
        <InfoRow label="Dernière transition">{formatInstant(data.lastTransitionAt)}</InfoRow>
        <InfoRow label="Identifiant"><code style={{ fontSize: 12, wordBreak: "break-all" }}>{data.id}</code></InfoRow>
      </dl>
    </Card>
  );
}

function MasterPanel({ data }: { data: ConsolidatedRecordResponse["master"] }) {
  if (data.status !== "AVAILABLE") {
    return (
      <Card>
        <PanelHeader icon="cloudUpload" tone="slate" title="Partie numérique" owner="Détenue par la GED maître" />
        <MasterStatusBanner status={data.status} />
        <p style={{ marginTop: 14, color: "var(--text-secondary)", fontSize: 13 }}>
          Titre, plan de classement, DUA, sort final et gel contentieux sont <strong>détenus par la
          GED/SAE maître</strong>. La partie physique reste valide et utilisable seule.
        </p>
      </Card>
    );
  }

  return (
    <Card>
      <PanelHeader icon="cloudUpload" tone="brand" title="Partie numérique" owner="Détenue par la GED maître (Maarch RM)" />
      <dl style={{ margin: 0 }}>
        {data.title && <InfoRow label="Titre"><span style={{ fontWeight: 600 }}>{data.title}</span></InfoRow>}
        {data.classificationPath && (
          <InfoRow label="Plan de classement">
            <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
              <Icons.topology size={14} style={{ color: "var(--text-tertiary)", flexShrink: 0 }} />
              <code style={{ fontSize: 12 }}>{data.classificationPath}</code>
            </span>
          </InfoRow>
        )}
        {data.duaYears != null && (
          <InfoRow label="DUA">
            <Badge tone="info" icon={<Icons.pending size={12} weight="bold" />}>
              {data.duaYears} an{data.duaYears > 1 ? "s" : ""}
            </Badge>
          </InfoRow>
        )}
        {data.finalFate && <InfoRow label="Sort final">{data.finalFate}</InfoRow>}
        <InfoRow label="Gel contentieux">
          {data.legalHold
            ? <Badge tone="danger" icon={<Icons.locked size={12} weight="bold" />}>Actif · sortie bloquée</Badge>
            : <Badge tone="success" icon={<Icons.success size={12} weight="bold" />}>Aucun</Badge>}
        </InfoRow>
      </dl>
    </Card>
  );
}

function MasterStatusBanner({ status }: { status: MasterStatus }) {
  if (status === "UNAVAILABLE") {
    return (
      <Alert tone="warning" title="Information maître indisponible">
        Soit la cote n&apos;existe pas (encore) côté GED maître, soit le déploiement est en mode
        {" "}<em>standalone</em> (adapter <code>noop</code>).
      </Alert>
    );
  }
  return (
    <Alert tone="danger" title="GED maître injoignable">
      Erreur réseau, délai dépassé ou authentification : détails dans les logs backend.
      Réessayer dans quelques instants.
    </Alert>
  );
}
