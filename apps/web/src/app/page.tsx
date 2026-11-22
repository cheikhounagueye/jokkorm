"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import {
  Button,
  Card,
  EmptyState,
  IconTile,
  KpiCard,
  REQUEST_TYPE,
  type Tone,
} from "@/components/ui";
import {
  getBufferZoneAlerts,
  getCurrentActor,
  getCurrentRole,
  listActiveLoans,
  listBatches,
  listMyRequests,
  listToApprove,
  listToFulfill,
  listToQualify,
  type AccessRequestResponse,
  type RequestType,
  type UserRole,
} from "@/lib/api";
import { Icons, type IconName } from "@/lib/icons";

const ROLE_GREETING: Record<UserRole, { title: string; subtitle: string; gradient: string }> = {
  REQUESTER: {
    title: "Rechercher un dossier, demander un accès",
    subtitle: "Prêt de la boîte entière, extraction de pièces, ou numérisation à distance.",
    gradient: "linear-gradient(120deg, #2f6df6 0%, #1e3a8a 100%)",
  },
  ARCHIVIST: {
    title: "File de travail du jour",
    subtitle: "Qualifie les demandes, gère les prêts, surveille la zone tampon.",
    gradient: "linear-gradient(120deg, #0f9f6e 0%, #065f46 100%)",
  },
  ARCHIVES_HEAD: {
    title: "Vue consolidée du service",
    subtitle: "Approuvez les demandes qualifiées, pilotez les indicateurs du magasin.",
    gradient: "linear-gradient(120deg, #d97706 0%, #92400e 100%)",
  },
  AUDITOR: {
    title: "Historique et traçabilité",
    subtitle: "Consultation de la chaîne d'audit du tenant.",
    gradient: "linear-gradient(120deg, #64748b 0%, #1e293b 100%)",
  },
};

export default function Home() {
  const [role, setRole] = useState<UserRole>("REQUESTER");
  const [actor, setActor] = useState("");
  useEffect(() => {
    setRole(getCurrentRole());
    setActor(getCurrentActor());
  }, []);

  const g = ROLE_GREETING[role];

  return (
    <section className="fade-up">
      <div style={{
        background: g.gradient,
        borderRadius: "var(--radius-lg)",
        padding: "26px 32px",
        marginBottom: 20,
        color: "white",
        boxShadow: "var(--shadow-md)",
        display: "flex", alignItems: "center", gap: 24,
        overflow: "hidden",
      }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 12, fontWeight: 500, textTransform: "uppercase", letterSpacing: "0.08em", opacity: 0.85, marginBottom: 6 }}>
            Bonjour {actor && <strong>{actor}</strong>} · {new Date().toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" })}
          </div>
          <h1 style={{ color: "white", fontSize: 24, marginBottom: 6 }}>{g.title}</h1>
          <p style={{ fontSize: 14, opacity: 0.9, maxWidth: 560 }}>{g.subtitle}</p>
        </div>
        <div aria-hidden style={{ display: "flex", alignItems: "flex-end", marginRight: 8, flexShrink: 0 }}>
          <ArchiveBoxIso size={74} finish="kraft" />
          <div style={{ marginLeft: -22, marginBottom: 18 }}><ArchiveBoxIso size={64} finish="sealed" /></div>
          <div style={{ marginLeft: -20 }}><ArchiveBoxIso size={80} finish="kraft" /></div>
        </div>
      </div>

      {role === "REQUESTER" && <RequesterHome />}
      {role === "ARCHIVIST" && <ArchivistHome />}
      {role === "ARCHIVES_HEAD" && <ArchivesHeadHome />}
      {role === "AUDITOR" && <AuditorHome />}
    </section>
  );
}

function RequesterHome() {
  const mine = useQuery({ queryKey: ["mine"], queryFn: listMyRequests });
  const total = mine.data?.length ?? 0;
  const active = mine.data?.filter((r) => ["SUBMITTED", "QUALIFIED", "APPROVED", "FULFILLED"].includes(r.status)).length ?? 0;
  const completed = mine.data?.filter((r) => r.status === "COMPLETED").length ?? 0;

  return (
    <>
      <div style={grid(3)}>
        <KpiCard label="Mes demandes" value={total} icon="requests" tone="brand" href="/demandes" />
        <KpiCard label="En cours de traitement" value={active} icon="pending" tone="warning" />
        <KpiCard label="Clôturées" value={completed} icon="success" tone="success" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16 }}>
        <Card>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, gap: 12 }}>
            <div>
              <h2>Que veux-tu demander ?</h2>
              <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
                Choisis le type de demande, on te guide ensuite.
              </p>
            </div>
            <Link href="/demandes/new">
              <Button size="lg" icon={<Icons.sparkle size={16} weight="fill" />} iconRight={<Icons.next size={16} weight="bold" />}>
                Nouvelle demande
              </Button>
            </Link>
          </div>
          <div style={grid(3, 12, 0)}>
            {(Object.keys(REQUEST_TYPE) as RequestType[]).map((t) => (
              <RequestTypeCard key={t} type={t} />
            ))}
          </div>
        </Card>

        <Card>
          <h2 style={{ marginBottom: 14 }}>Dernières demandes</h2>
          {!mine.data || mine.data.length === 0 ? (
            <EmptyState icon={<Icons.inbox size={28} />} title="Aucune demande" description="Tes demandes apparaîtront ici." />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {mine.data.slice(0, 5).map((r) => <RequestRow key={r.id} r={r} />)}
            </div>
          )}
        </Card>
      </div>
    </>
  );
}

function ArchivistHome() {
  const toQualify = useQuery({ queryKey: ["toQualify"], queryFn: listToQualify });
  const toFulfill = useQuery({ queryKey: ["toFulfill"], queryFn: listToFulfill });
  const activeLoans = useQuery({ queryKey: ["loans-active"], queryFn: listActiveLoans });
  const alerts = useQuery({ queryKey: ["buffer-alerts"], queryFn: getBufferZoneAlerts });

  const qualCount = toQualify.data?.length ?? 0;
  const fulfillCount = toFulfill.data?.length ?? 0;
  const loansCount = activeLoans.data?.length ?? 0;
  const overdueCount = activeLoans.data?.filter((l) => l.overdue).length ?? 0;
  const stale = alerts.data?.totalStaleContainers ?? 0;

  return (
    <>
      <div style={grid(4)}>
        <KpiCard label="À qualifier" value={qualCount} icon="qualify"
          tone={qualCount > 5 ? "danger" : qualCount > 0 ? "warning" : "neutral"} href="/demandes/qualifier" />
        <KpiCard label="À exécuter" value={fulfillCount} icon="fulfill"
          tone={fulfillCount > 0 ? "brand" : "neutral"} href="/demandes/executer" />
        <KpiCard label="Prêts actifs" value={loansCount} icon="loans"
          tone={overdueCount > 0 ? "danger" : "info"}
          delta={overdueCount > 0 ? { value: overdueCount, label: "en retard", positive: false } : undefined}
          href="/prets" />
        <KpiCard label="Zone tampon" value={stale} icon="warning"
          tone={stale > 0 ? "warning" : "neutral"} href="/magasin/lots" />
      </div>
      <div style={grid(2, 16, 0)}>
        <RequestFeed queryKey="toQualify" queryFn={listToQualify} title="File de qualification" href="/demandes/qualifier" empty="Rien à qualifier." />
        <RequestFeed queryKey="toFulfill" queryFn={listToFulfill} title="À exécuter" href="/demandes/executer" empty="Rien à exécuter." />
      </div>
    </>
  );
}

function ArchivesHeadHome() {
  const toQualify = useQuery({ queryKey: ["toQualify"], queryFn: listToQualify });
  const toApprove = useQuery({ queryKey: ["toApprove"], queryFn: listToApprove });
  const activeLoans = useQuery({ queryKey: ["loans-active"], queryFn: listActiveLoans });
  const batches = useQuery({ queryKey: ["batches"], queryFn: () => listBatches() });

  const approveCount = toApprove.data?.length ?? 0;

  return (
    <>
      <div style={grid(4)}>
        <KpiCard label="À approuver" value={approveCount} icon="approve"
          tone={approveCount > 0 ? "warning" : "success"} href="/demandes/approuver" />
        <KpiCard label="En qualification" value={toQualify.data?.length ?? 0} icon="qualify" tone="brand" href="/demandes" />
        <KpiCard label="Prêts actifs" value={activeLoans.data?.length ?? 0} icon="loans" tone="info" href="/prets" />
        <KpiCard label="Lots de boîtes" value={batches.data?.length ?? 0} icon="batches" tone="neutral" href="/magasin/lots" />
      </div>
      <div style={grid(2, 16, 0)}>
        <RequestFeed queryKey="toApprove" queryFn={listToApprove} title="En attente d'approbation" href="/demandes/approuver" empty="Aucune demande à approuver." />
        <Card>
          <h2 style={{ marginBottom: 14 }}>Accès rapide</h2>
          <div style={{ display: "grid", gap: 8 }}>
            <QuickLink href="/magasin" icon="archiveRoom" tone="kraft" label="Salle d'archives" desc="Topographie et boîtes rangées" />
            <QuickLink href="/magasin/lots" icon="batches" tone="brand" label="Lots de boîtes" desc="Provisionnement et étiquettes" />
            <QuickLink href="/records" icon="archives" tone="slate" label="Toutes les archives" desc="Vue consolidée" />
          </div>
        </Card>
      </div>
    </>
  );
}

function AuditorHome() {
  return (
    <Card>
      <EmptyState icon={<Icons.shield size={32} />} title="Audit — lecture seule"
        description="La vue historique complète arrive dans une prochaine phase." />
    </Card>
  );
}

function RequestTypeCard({ type }: { type: RequestType }) {
  const meta = REQUEST_TYPE[type];
  const desc = type === "BORROW_BOX" ? "Consultation longue" : type === "EXTRACT_PIECES" ? "2-3 documents précis" : "Lien Maarch envoyé";
  return (
    <Link href="/demandes/new" style={{
      display: "block", padding: "18px 16px",
      border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)",
      background: "var(--surface-card)", color: "var(--text-primary)",
      transition: "all 150ms ease",
    }}
    onMouseEnter={(e) => { e.currentTarget.style.transform = "translateY(-2px)"; e.currentTarget.style.boxShadow = "var(--shadow-md)"; }}
    onMouseLeave={(e) => { e.currentTarget.style.transform = "translateY(0)"; e.currentTarget.style.boxShadow = "none"; }}>
      <IconTile name={meta.icon} tone={meta.tone} size={44} />
      <div style={{ fontSize: 14, fontWeight: 600, marginTop: 12, marginBottom: 2 }}>{meta.short}</div>
      <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>{desc}</div>
    </Link>
  );
}

function RequestRow({ r }: { r: AccessRequestResponse }) {
  const meta = REQUEST_TYPE[r.type];
  return (
    <Link href="/demandes" style={{
      display: "flex", alignItems: "center", gap: 10, padding: "8px 10px",
      border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)",
      color: "var(--text-primary)",
    }}>
      <IconTile name={meta.icon} tone={meta.tone} size={30} variant="soft" />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{r.purpose}</div>
        <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>{meta.short}</div>
      </div>
    </Link>
  );
}

function QuickLink({ href, icon, tone, label, desc }: { href: string; icon: IconName; tone: Tone; label: string; desc: string }) {
  return (
    <Link href={href} style={{
      display: "flex", alignItems: "center", gap: 12, padding: "10px 12px",
      border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)",
      color: "var(--text-primary)", transition: "background 150ms",
    }}
    onMouseEnter={(e) => (e.currentTarget.style.background = "var(--surface-hover)")}
    onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}>
      <IconTile name={icon} tone={tone} size={36} />
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 13, fontWeight: 600 }}>{label}</div>
        <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>{desc}</div>
      </div>
      <Icons.chevronRight size={14} weight="bold" style={{ color: "var(--text-tertiary)" }} />
    </Link>
  );
}

function RequestFeed({ queryKey, queryFn, title, href, empty }: {
  queryKey: string; queryFn: () => Promise<AccessRequestResponse[]>; title: string; href: string; empty: string;
}) {
  const query = useQuery({ queryKey: [queryKey], queryFn });
  return (
    <Card padding={0}>
      <div style={{ padding: "14px 20px", borderBottom: "1px solid var(--border-subtle)", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h3>{title}</h3>
        <Link href={href} style={{ fontSize: 12, fontWeight: 600, display: "inline-flex", alignItems: "center", gap: 4 }}>
          Tout voir <Icons.next size={12} weight="bold" />
        </Link>
      </div>
      {query.data && query.data.length === 0 && (
        <div style={{ padding: 32, textAlign: "center", color: "var(--text-tertiary)", fontSize: 13 }}>{empty}</div>
      )}
      {query.data?.slice(0, 5).map((r) => {
        const meta = REQUEST_TYPE[r.type];
        return (
          <div key={r.id} style={{ padding: "10px 20px", borderBottom: "1px solid var(--border-subtle)", display: "flex", alignItems: "center", gap: 12 }}>
            <IconTile name={meta.icon} tone={meta.tone} size={32} variant="soft" />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>{r.requesterName}</div>
              <div style={{ fontSize: 12, color: "var(--text-tertiary)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{r.purpose}</div>
            </div>
            <span style={{ fontSize: 11, color: "var(--text-secondary)" }}>{meta.short}</span>
          </div>
        );
      })}
    </Card>
  );
}

function grid(cols: number, gap = 16, mb = 20): React.CSSProperties {
  return { display: "grid", gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`, gap, marginBottom: mb };
}
