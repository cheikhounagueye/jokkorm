"use client";

import { useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  ArrowRight,
  Boxes,
  CheckCheck,
  CheckCircle2,
  FileText,
  Inbox,
  Package,
  Package2,
  Printer,
  Send,
  ShieldCheck,
  Sparkles,
  Warehouse,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Button, Card, EmptyState, KpiCard } from "@/components/ui";
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
  type UserRole,
} from "@/lib/api";

const ROLE_GREETING: Record<UserRole, { title: string; subtitle: string; accent: string }> = {
  REQUESTER: {
    title: "Rechercher un dossier, demander un accès",
    subtitle: "Prêt de la boîte entière, extraction de pièces, ou numérisation à distance.",
    accent: "linear-gradient(135deg, #3b82f6 0%, #1e40af 100%)",
  },
  ARCHIVIST: {
    title: "File de travail du jour",
    subtitle: "Qualifie les demandes, gère les prêts, surveille la zone tampon.",
    accent: "linear-gradient(135deg, #10b981 0%, #065f46 100%)",
  },
  ARCHIVES_HEAD: {
    title: "Vue consolidée du service",
    subtitle: "Approuvez les demandes qualifiées, pilotez les KPI du magasin.",
    accent: "linear-gradient(135deg, #f59e0b 0%, #b45309 100%)",
  },
  AUDITOR: {
    title: "Historique et traçabilité",
    subtitle: "Consultation de l'audit chain du tenant.",
    accent: "linear-gradient(135deg, #64748b 0%, #334155 100%)",
  },
};

export default function Home() {
  const [role, setRole] = useState<UserRole>("REQUESTER");
  const [actor, setActor] = useState("");
  useEffect(() => {
    setRole(getCurrentRole());
    setActor(getCurrentActor());
  }, []);

  const greeting = ROLE_GREETING[role];

  return (
    <section>
      {/* Hero bannière */}
      <div style={{
        background: greeting.accent,
        borderRadius: "var(--radius-lg)",
        padding: "28px 32px",
        marginBottom: 24,
        color: "white",
        boxShadow: "var(--shadow-md)",
        position: "relative",
        overflow: "hidden",
      }}>
        <div style={{
          position: "absolute", top: -40, right: -40,
          width: 180, height: 180,
          borderRadius: "50%",
          background: "rgba(255, 255, 255, 0.08)",
        }} />
        <div style={{
          position: "absolute", top: 40, right: 80,
          width: 100, height: 100,
          borderRadius: "50%",
          background: "rgba(255, 255, 255, 0.05)",
        }} />
        <div style={{ position: "relative" }}>
          <div style={{ fontSize: 12, fontWeight: 500, textTransform: "uppercase", letterSpacing: "0.08em", opacity: 0.85, marginBottom: 6 }}>
            Bonjour {actor && <span style={{ fontWeight: 700 }}>{actor}</span>} · {new Date().toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" })}
          </div>
          <h1 style={{ color: "white", fontSize: 24, fontWeight: 700, marginBottom: 6 }}>{greeting.title}</h1>
          <p style={{ fontSize: 14, opacity: 0.9, maxWidth: 560 }}>{greeting.subtitle}</p>
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
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16, marginBottom: 24 }}>
        <KpiCard label="Mes demandes" value={total} icon={<Send size={18} />} tone="brand" href="/demandes" />
        <KpiCard label="En cours de traitement" value={active} icon={<Inbox size={18} />} tone="warning" />
        <KpiCard label="Clôturées" value={completed} icon={<CheckCircle2 size={18} />} tone="success" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16 }}>
        <Card>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
            <div>
              <h2>Que veux-tu demander ?</h2>
              <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
                Choisis le type de demande, on te guide ensuite.
              </p>
            </div>
            <Link href="/demandes/new">
              <Button variant="primary" size="lg" icon={<Sparkles size={16} />} iconRight={<ArrowRight size={16} />}>
                Nouvelle demande
              </Button>
            </Link>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12 }}>
            <RequestTypeCard
              icon={<Package size={22} />}
              title="Prêt boîte"
              desc="Consultation longue"
              tone="brand"
              href="/demandes/new"
            />
            <RequestTypeCard
              icon={<FileText size={22} />}
              title="Extraction"
              desc="2-3 documents précis"
              tone="info"
              href="/demandes/new"
            />
            <RequestTypeCard
              icon={<Printer size={22} />}
              title="Numérisation"
              desc="Lien Maarch envoyé"
              tone="success"
              href="/demandes/new"
            />
          </div>
        </Card>

        <Card>
          <h2 style={{ marginBottom: 12 }}>Dernières demandes</h2>
          {!mine.data || mine.data.length === 0 ? (
            <div style={{ padding: "20px 0", textAlign: "center", color: "var(--text-tertiary)", fontSize: 13 }}>
              Aucune demande encore.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {mine.data.slice(0, 5).map((r) => (
                <Link key={r.id} href={`/demandes`} style={{
                  display: "flex", alignItems: "center", gap: 10, padding: "8px 10px",
                  border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)",
                  color: "var(--text-primary)", textDecoration: "none",
                }}>
                  <div style={{ width: 6, height: 6, borderRadius: "50%", background: statusDot(r.status) }} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {r.purpose}
                    </div>
                    <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>
                      {r.type.replace(/_/g, " ").toLowerCase()}
                    </div>
                  </div>
                </Link>
              ))}
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
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16, marginBottom: 24 }}>
        <KpiCard label="À qualifier" value={qualCount} icon={<Inbox size={18} />}
          tone={qualCount > 5 ? "danger" : qualCount > 0 ? "warning" : "neutral"}
          href="/demandes/qualifier" />
        <KpiCard label="À exécuter" value={fulfillCount} icon={<CheckCheck size={18} />}
          tone={fulfillCount > 0 ? "brand" : "neutral"}
          href="/demandes/executer" />
        <KpiCard label="Prêts actifs" value={loansCount} icon={<FileText size={18} />}
          tone={overdueCount > 0 ? "danger" : "neutral"}
          delta={overdueCount > 0 ? { value: overdueCount, label: "en retard", positive: false } : undefined}
          href="/prets" />
        <KpiCard label="Zone tampon" value={stale} icon={<AlertTriangle size={18} />}
          tone={stale > 0 ? "warning" : "neutral"}
          href="/magasin/lots" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        <RecentRequests queryFn={listToQualify} title="File de qualification" href="/demandes/qualifier" emptyLabel="Rien à qualifier." />
        <RecentRequests queryFn={listToFulfill} title="À exécuter" href="/demandes/executer" emptyLabel="Rien à exécuter." />
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
  const qualCount = toQualify.data?.length ?? 0;
  const loansCount = activeLoans.data?.length ?? 0;
  const batchCount = batches.data?.length ?? 0;

  return (
    <>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16, marginBottom: 24 }}>
        <KpiCard label="À approuver" value={approveCount} icon={<ShieldCheck size={18} />}
          tone={approveCount > 0 ? "warning" : "success"}
          href="/demandes/approuver" />
        <KpiCard label="En qualification" value={qualCount} icon={<Inbox size={18} />} tone="brand" href="/demandes" />
        <KpiCard label="Prêts actifs" value={loansCount} icon={<FileText size={18} />} href="/prets" />
        <KpiCard label="Lots de boîtes" value={batchCount} icon={<Package2 size={18} />} href="/magasin/lots" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        <RecentRequests queryFn={listToApprove} title="En attente d'approbation" href="/demandes/approuver" emptyLabel="Aucune demande à approuver." />
        <Card>
          <h2 style={{ marginBottom: 12 }}>Accès rapide</h2>
          <div style={{ display: "grid", gap: 8 }}>
            <QuickLink href="/magasin" icon={<Warehouse size={18} />} label="Salle d'archives" desc="Vue magasin et topographie" />
            <QuickLink href="/magasin/lots" icon={<Package2 size={18} />} label="Lots de boîtes" desc="Provisionnement et étiquettes" />
            <QuickLink href="/records" icon={<Boxes size={18} />} label="Toutes les archives" desc="Vue consolidée" />
          </div>
        </Card>
      </div>
    </>
  );
}

function AuditorHome() {
  return (
    <Card>
      <EmptyState
        icon={<ShieldCheck size={32} />}
        title="Audit — lecture seule"
        description="Vue historique complète à venir en phase 5R.E."
      />
    </Card>
  );
}

function RequestTypeCard({ icon, title, desc, tone, href }: {
  icon: React.ReactNode; title: string; desc: string; tone: "brand" | "info" | "success"; href: string;
}) {
  const bg = `var(--${tone === "brand" ? "brand" : tone}-50)`;
  const color = `var(--${tone === "brand" ? "brand" : tone}-500)`;
  return (
    <Link href={href} style={{
      display: "block", padding: "18px 16px",
      border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)",
      background: "var(--surface-card)", color: "var(--text-primary)",
      textDecoration: "none", transition: "all 150ms ease",
    }}
    onMouseEnter={(e) => {
      e.currentTarget.style.borderColor = color;
      e.currentTarget.style.transform = "translateY(-2px)";
      e.currentTarget.style.boxShadow = "var(--shadow-md)";
    }}
    onMouseLeave={(e) => {
      e.currentTarget.style.borderColor = "var(--border-subtle)";
      e.currentTarget.style.transform = "translateY(0)";
      e.currentTarget.style.boxShadow = "none";
    }}>
      <div style={{
        width: 40, height: 40, borderRadius: "var(--radius-md)",
        background: bg, color,
        display: "inline-flex", alignItems: "center", justifyContent: "center",
        marginBottom: 10,
      }}>{icon}</div>
      <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 2 }}>{title}</div>
      <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>{desc}</div>
    </Link>
  );
}

function QuickLink({ href, icon, label, desc }: { href: string; icon: React.ReactNode; label: string; desc?: string }) {
  return (
    <Link href={href} style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "12px 14px",
      border: "1px solid var(--border-subtle)",
      borderRadius: "var(--radius-md)",
      color: "var(--text-primary)",
      textDecoration: "none",
      transition: "background 150ms",
    }}
    onMouseEnter={(e) => e.currentTarget.style.background = "var(--surface-hover)"}
    onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}>
      <div style={{
        width: 36, height: 36, borderRadius: "var(--radius-md)",
        background: "var(--brand-50)", color: "var(--brand-500)",
        display: "inline-flex", alignItems: "center", justifyContent: "center",
      }}>{icon}</div>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 13, fontWeight: 600 }}>{label}</div>
        {desc && <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>{desc}</div>}
      </div>
      <ArrowRight size={14} style={{ color: "var(--text-tertiary)" }} />
    </Link>
  );
}

function RecentRequests({ queryFn, title, href, emptyLabel }: {
  queryFn: () => Promise<any[]>; title: string; href: string; emptyLabel: string;
}) {
  const query = useQuery({ queryKey: [title], queryFn });
  return (
    <Card padding={0}>
      <div style={{ padding: "16px 20px 12px", borderBottom: "1px solid var(--border-subtle)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h3 style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)", fontWeight: 600 }}>
            {title}
          </h3>
          <Link href={href} style={{ fontSize: 12, color: "var(--brand-500)", fontWeight: 500, display: "inline-flex", alignItems: "center", gap: 3 }}>
            Tout voir <ArrowRight size={12} />
          </Link>
        </div>
      </div>
      {query.data && query.data.length === 0 && (
        <div style={{ padding: 32, textAlign: "center", color: "var(--text-tertiary)", fontSize: 13 }}>
          {emptyLabel}
        </div>
      )}
      {query.data && query.data.length > 0 && (
        <div>
          {query.data.slice(0, 5).map((r: any) => (
            <div key={r.id} style={{
              padding: "12px 20px",
              borderBottom: "1px solid var(--border-subtle)",
              display: "flex", justifyContent: "space-between", alignItems: "center",
            }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 500 }}>{r.requesterName}</div>
                <div style={{ fontSize: 11, color: "var(--text-tertiary)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  {r.purpose}
                </div>
              </div>
              <div style={{ fontSize: 10, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.05em", padding: "2px 8px", background: "var(--surface-sunken)", borderRadius: "var(--radius-full)" }}>
                {r.type?.replace(/_/g, " ")}
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

function statusDot(status: string): string {
  switch (status) {
    case "DRAFT": return "var(--text-tertiary)";
    case "SUBMITTED": return "var(--brand-500)";
    case "QUALIFIED": return "var(--warning-500)";
    case "APPROVED": return "var(--success-500)";
    case "FULFILLED": return "var(--brand-500)";
    case "COMPLETED": return "var(--text-tertiary)";
    case "REJECTED": case "CANCELLED": return "var(--danger-500)";
    default: return "var(--text-tertiary)";
  }
}
