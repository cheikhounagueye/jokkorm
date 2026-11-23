"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import {
  ACCESS_STATUS,
  Button,
  Card,
  EmptyState,
  PageHeader,
  StatusBadge,
} from "@/components/ui";
import {
  listMyRequests,
  listToApprove,
  listToFulfill,
  listToQualify,
  type AccessRequestResponse,
} from "@/lib/api";
import { Icons, type IconName } from "@/lib/icons";
import { FourEyesBanner, RequestProgress } from "./_components/FourEyes";
import { QueueHeader, RequestRow } from "./_components/RequestBits";
import { useRole } from "./_components/useRole";

export default function DemandesPage() {
  const role = useRole();

  const mine = useQuery({ queryKey: ["mine", role], queryFn: listMyRequests, enabled: role === "REQUESTER" });
  const toQualify = useQuery({ queryKey: ["toQualify", role], queryFn: listToQualify,
    enabled: role === "ARCHIVIST" || role === "ARCHIVES_HEAD" });
  const toApprove = useQuery({ queryKey: ["toApprove", role], queryFn: listToApprove,
    enabled: role === "ARCHIVES_HEAD" });
  const toFulfill = useQuery({ queryKey: ["toFulfill", role], queryFn: listToFulfill,
    enabled: role === "ARCHIVIST" });

  return (
    <section className="fade-up">
      <PageHeader
        icon="requests"
        tone="brand"
        title="Demandes d'accès"
        subtitle="Prêt de boîte, extraction de pièces ou numérisation — validées en 4 yeux."
        crumbs={[{ label: "Accueil", href: "/" }, { label: "Demandes" }]}
        actions={role && role !== "AUDITOR" && (
          <Link href="/demandes/new">
            <Button icon={<Icons.add size={16} weight="bold" />}>Nouvelle demande</Button>
          </Link>
        )}
      />

      <FourEyesBanner role={role} />

      {role === "REQUESTER" && (
        <Queue icon="inbox" title="Mes demandes" query={mine}
          emptyTitle="Aucune demande" emptyText="Lance ta première demande : on te guide pas à pas."
          emptyAction={<Link href="/demandes/new"><Button variant="secondary" icon={<Icons.add size={16} />}>Nouvelle demande</Button></Link>} />
      )}

      {(role === "ARCHIVIST" || role === "ARCHIVES_HEAD") && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 16 }}>
          <Queue icon="qualify" title="À qualifier" query={toQualify} href="/demandes/qualifier" actionLabel="Qualifier"
            emptyTitle="File vide" emptyText="Aucune demande en attente de qualification." />
          {role === "ARCHIVES_HEAD" ? (
            <Queue icon="approve" title="À approuver" query={toApprove} href="/demandes/approuver" actionLabel="Approuver"
              emptyTitle="File vide" emptyText="Aucune demande en attente d'approbation." />
          ) : (
            <Queue icon="fulfill" title="À exécuter" query={toFulfill} href="/demandes/executer" actionLabel="Exécuter"
              emptyTitle="File vide" emptyText="Aucune demande approuvée à exécuter." />
          )}
        </div>
      )}

      {role === "AUDITOR" && (
        <Card>
          <EmptyState icon={<Icons.shield size={28} />} title="Rôle auditeur — lecture seule"
            description="La vue historique des demandes arrive dans une prochaine phase." />
        </Card>
      )}
    </section>
  );
}

function Queue({
  icon,
  title,
  query,
  href,
  actionLabel,
  emptyTitle,
  emptyText,
  emptyAction,
}: {
  icon: IconName;
  title: string;
  query: { data?: AccessRequestResponse[]; isLoading: boolean; isError: boolean; error: unknown };
  href?: string;
  actionLabel?: string;
  emptyTitle: string;
  emptyText: string;
  emptyAction?: React.ReactNode;
}) {
  const items = query.data ?? [];
  return (
    <Card padding={0} style={{ overflow: "hidden" }}>
      <QueueHeader icon={icon} title={title} count={query.data?.length}
        action={href && items.length > 0 && (
          <Link href={href} style={{ fontSize: 12, fontWeight: 600, display: "inline-flex", alignItems: "center", gap: 4 }}>
            {actionLabel} <Icons.next size={12} weight="bold" />
          </Link>
        )} />
      {query.isLoading && <div style={{ padding: 24, fontSize: 13, color: "var(--text-tertiary)" }}>Chargement…</div>}
      {query.isError && (
        <div style={{ padding: 16, fontSize: 13, color: "var(--danger-500)" }}>{(query.error as Error).message}</div>
      )}
      {query.data && items.length === 0 && (
        <div style={{ padding: 16 }}>
          <EmptyState icon={<Icons.inbox size={28} />} title={emptyTitle} description={emptyText} action={emptyAction} />
        </div>
      )}
      {items.map((r) => (
        <RequestRow key={r.id} r={r} right={
          <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
            <RequestProgress r={r} />
            <StatusBadge meta={ACCESS_STATUS[r.status]} />
          </div>
        } />
      ))}
    </Card>
  );
}
