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
  Send,
  ShieldCheck,
  Sparkles,
  Warehouse,
} from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Button, Card, CardHeader, EmptyState, KpiCard } from "@/components/ui";
import {
  getBufferZoneAlerts,
  getCurrentRole,
  listActiveLoans,
  listBatches,
  listMyRequests,
  listStorageTree,
  listToApprove,
  listToFulfill,
  listToQualify,
  type UserRole,
} from "@/lib/api";

export default function Home() {
  const [role, setRole] = useState<UserRole>("REQUESTER");
  useEffect(() => { setRole(getCurrentRole()); }, []);

  return (
    <section>
      <header style={{ marginBottom: 24 }}>
        <h1 style={{ marginBottom: 4 }}>Tableau de bord</h1>
        <p style={{ fontSize: 14, color: "var(--text-secondary)" }}>
          {role === "REQUESTER" && "Vos demandes d'accès et dossiers récents."}
          {role === "ARCHIVIST" && "File de travail, prêts actifs et alertes magasin."}
          {role === "ARCHIVES_HEAD" && "Vue consolidée et demandes à valider."}
          {role === "AUDITOR" && "Historique et traçabilité du tenant."}
        </p>
      </header>

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
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 16, marginBottom: 24 }}>
        <KpiCard label="Mes demandes" value={total} icon={<Send size={16} />} tone="brand" href="/demandes" />
        <KpiCard label="En cours" value={active} icon={<Inbox size={16} />} tone="warning" />
        <KpiCard label="Clôturées" value={completed} icon={<CheckCircle2 size={16} />} tone="success" />
      </div>

      <Card>
        <CardHeader
          title="Démarrer une nouvelle demande"
          subtitle="Prêt, extraction de pièces, ou numérisation à distance"
          action={
            <Link href="/demandes/new">
              <Button variant="primary" icon={<Sparkles size={14} />} iconRight={<ArrowRight size={14} />}>
                Nouvelle demande
              </Button>
            </Link>
          }
        />
        <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 12 }}>
          <MiniCard icon={<Package size={18} />} title="Prêt boîte" desc="Consultation longue" />
          <MiniCard icon={<FileText size={18} />} title="Extraction" desc="2-3 documents" />
          <MiniCard icon={<Sparkles size={18} />} title="Numérisation" desc="Lien Maarch envoyé" />
        </div>
      </Card>
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
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 16, marginBottom: 24 }}>
        <KpiCard label="À qualifier" value={qualCount} icon={<Inbox size={16} />}
          tone={qualCount > 5 ? "danger" : qualCount > 0 ? "warning" : "neutral"}
          href="/demandes/qualifier" />
        <KpiCard label="À exécuter" value={fulfillCount} icon={<CheckCheck size={16} />}
          tone={fulfillCount > 0 ? "brand" : "neutral"}
          href="/demandes/executer" />
        <KpiCard label="Prêts actifs" value={loansCount} icon={<FileText size={16} />}
          tone={overdueCount > 0 ? "danger" : "neutral"}
          delta={overdueCount > 0 ? { value: overdueCount, label: "en retard", positive: false } : undefined}
          href="/prets" />
        <KpiCard label="Zone tampon" value={stale} icon={<AlertTriangle size={16} />}
          tone={stale > 0 ? "warning" : "neutral"}
          href="/magasin/lots" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        <RecentRequests queryFn={listToQualify} title="File de qualification" href="/demandes/qualifier" />
        <RecentRequests queryFn={listToFulfill} title="À exécuter" href="/demandes/executer" />
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
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 16, marginBottom: 24 }}>
        <KpiCard label="À approuver" value={approveCount} icon={<ShieldCheck size={16} />}
          tone={approveCount > 0 ? "warning" : "success"}
          href="/demandes/approuver" />
        <KpiCard label="En qualification" value={qualCount} icon={<Inbox size={16} />} tone="brand" href="/demandes" />
        <KpiCard label="Prêts actifs" value={loansCount} icon={<FileText size={16} />} href="/prets" />
        <KpiCard label="Lots de boîtes" value={batchCount} icon={<Boxes size={16} />} href="/magasin/lots" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        <RecentRequests queryFn={listToApprove} title="En attente d'approbation" href="/demandes/approuver" />
        <Card>
          <CardHeader title="Accès rapide" />
          <div style={{ display: "grid", gap: 8 }}>
            <QuickLink href="/magasin" icon={<Warehouse size={16} />} label="Salle d'archives" />
            <QuickLink href="/magasin/lots" icon={<Package size={16} />} label="Lots de boîtes" />
            <QuickLink href="/records" icon={<Boxes size={16} />} label="Toutes les archives" />
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

function MiniCard({ icon, title, desc }: { icon: React.ReactNode; title: string; desc: string }) {
  return (
    <div style={{
      padding: "14px 16px", borderRadius: "var(--radius-md)",
      background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)",
    }}>
      <div style={{ color: "var(--brand-500)", marginBottom: 6 }}>{icon}</div>
      <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-primary)" }}>{title}</div>
      <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>{desc}</div>
    </div>
  );
}

function QuickLink({ href, icon, label }: { href: string; icon: React.ReactNode; label: string }) {
  return (
    <Link href={href} style={{
      display: "flex", alignItems: "center", gap: 10,
      padding: "10px 12px",
      border: "1px solid var(--border-subtle)",
      borderRadius: "var(--radius-md)",
      color: "var(--text-primary)",
      fontSize: 13, fontWeight: 500,
      textDecoration: "none",
      transition: "background 150ms",
    }}
    onMouseEnter={(e) => e.currentTarget.style.background = "var(--surface-hover)"}
    onMouseLeave={(e) => e.currentTarget.style.background = "transparent"}>
      <span style={{ color: "var(--brand-500)" }}>{icon}</span>
      {label}
      <ArrowRight size={14} style={{ marginLeft: "auto", color: "var(--text-tertiary)" }} />
    </Link>
  );
}

function RecentRequests({ queryFn, title, href }: {
  queryFn: () => Promise<any[]>; title: string; href: string;
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
          Rien pour l'instant.
        </div>
      )}
      {query.data && query.data.length > 0 && (
        <div>
          {query.data.slice(0, 5).map((r: any) => (
            <div key={r.id} style={{
              padding: "10px 20px",
              borderBottom: "1px solid var(--border-subtle)",
              display: "flex", justifyContent: "space-between", alignItems: "center",
            }}>
              <div>
                <div style={{ fontSize: 13, fontWeight: 500 }}>{r.requesterName}</div>
                <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>{r.purpose}</div>
              </div>
              <div style={{ fontSize: 10, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.05em" }}>
                {r.type?.replace(/_/g, " ")}
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
