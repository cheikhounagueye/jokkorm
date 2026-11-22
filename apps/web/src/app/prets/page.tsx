"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import {
  Alert,
  Button,
  Card,
  EmptyState,
  KpiCard,
  LOAN_STATUS,
  PageHeader,
  StatusBadge,
} from "@/components/ui";
import {
  downloadGhostCard,
  listActiveLoans,
  markLoanReturned,
  type LoanResponse,
} from "@/lib/api";
import { Icons } from "@/lib/icons";
import { daysUntil, relativeDue, shortDate } from "./_components/dates";

export default function PretsPage() {
  const query = useQuery({
    queryKey: ["loans-active"],
    queryFn: listActiveLoans,
    refetchInterval: 30_000,
  });
  const qc = useQueryClient();

  const returnMutation = useMutation({
    mutationFn: markLoanReturned,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["loans-active"] }),
  });

  const loans = query.data ?? [];
  const overdue = loans.filter((l) => l.overdue).length;
  const dueSoon = loans.filter((l) => !l.overdue && daysUntil(l.expectedReturnAt) <= 7).length;

  return (
    <section className="fade-up">
      <PageHeader
        icon="loans"
        tone="info"
        title="Prêts physiques"
        subtitle="Boîtes sorties du magasin. Un fantôme PDF+QR occupe leur emplacement."
        crumbs={[{ label: "Accueil", href: "/" }, { label: "Prêts" }]}
        actions={
          <Link href="/prets/new">
            <Button icon={<Icons.add size={16} weight="bold" />}>Nouveau prêt</Button>
          </Link>
        }
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: 16, marginBottom: 20 }}>
        <KpiCard label="Prêts actifs" value={loans.length} icon="loans" tone="info" />
        <KpiCard label="En retard" value={overdue} icon="alert" tone={overdue > 0 ? "danger" : "neutral"} />
        <KpiCard label="Retour sous 7 jours" value={dueSoon} icon="calendar" tone={dueSoon > 0 ? "warning" : "neutral"} />
      </div>

      {query.isError && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="danger" title="Impossible de charger les prêts">{(query.error as Error).message}</Alert>
        </div>
      )}
      {returnMutation.isError && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="danger" title="Retour non enregistré">{(returnMutation.error as Error).message}</Alert>
        </div>
      )}

      {query.isLoading && (
        <Card><div style={{ color: "var(--text-tertiary)", fontSize: 13 }}>Chargement des prêts…</div></Card>
      )}

      {query.data && loans.length === 0 && (
        <Card>
          <EmptyState
            icon={<ArchiveBoxIso size={64} finish="kraft" />}
            title="Aucun prêt actif"
            description="Toutes les boîtes sont rangées en magasin."
            action={
              <Link href="/prets/new">
                <Button variant="secondary" icon={<Icons.add size={16} weight="bold" />}>Enregistrer un prêt</Button>
              </Link>
            }
          />
        </Card>
      )}

      {loans.length > 0 && (
        <Card padding={0} style={{ overflow: "hidden" }}>
          <table>
            <thead>
              <tr>
                <th>Statut</th>
                <th>Emprunteur</th>
                <th>Motif</th>
                <th>Sortie</th>
                <th>Retour prévu</th>
                <th style={{ textAlign: "right" }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {loans.map((l) => (
                <LoanRow
                  key={l.id}
                  loan={l}
                  onReturn={() => returnMutation.mutate(l.id)}
                  disabled={returnMutation.isPending}
                />
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </section>
  );
}

function LoanRow({ loan, onReturn, disabled }: {
  loan: LoanResponse;
  onReturn: () => void;
  disabled: boolean;
}) {
  const displayStatus = loan.overdue && loan.status === "OUT" ? "OVERDUE" : loan.status;
  const late = loan.overdue;
  const soon = !late && daysUntil(loan.expectedReturnAt) <= 7;
  const dueColor = late ? "var(--danger-500)" : soon ? "var(--warning-700)" : "var(--text-tertiary)";
  const initials = loan.borrowerName.split(/\s+/).map((p) => p[0]).join("").slice(0, 2).toUpperCase();

  return (
    <tr style={{ background: late ? "var(--danger-50)" : undefined }}>
      <td style={{ boxShadow: late ? "inset 3px 0 0 var(--danger-500)" : undefined }}>
        <StatusBadge meta={LOAN_STATUS[displayStatus]} />
      </td>
      <td>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{
            width: 32, height: 32, borderRadius: "var(--radius-full)", flexShrink: 0,
            background: "var(--brand-50)", color: "var(--brand-500)",
            display: "inline-flex", alignItems: "center", justifyContent: "center",
            fontSize: 12, fontWeight: 700,
          }}>{initials}</span>
          <div>
            <div style={{ fontWeight: 600 }}>{loan.borrowerName}</div>
            <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>{loan.borrowerDept ?? "Service non précisé"}</div>
          </div>
        </div>
      </td>
      <td style={{ color: "var(--text-secondary)", maxWidth: 260 }}>{loan.purpose ?? "—"}</td>
      <td style={{ color: "var(--text-secondary)", whiteSpace: "nowrap" }}>{loan.outAt ? shortDate(loan.outAt) : "—"}</td>
      <td style={{ whiteSpace: "nowrap" }}>
        <div style={{ fontWeight: late ? 700 : 500, color: late ? "var(--danger-700)" : "var(--text-primary)" }}>
          {shortDate(loan.expectedReturnAt)}
        </div>
        <div style={{ fontSize: 12, fontWeight: soon || late ? 600 : 400, color: dueColor, display: "inline-flex", alignItems: "center", gap: 4 }}>
          {late ? <Icons.alert size={12} weight="fill" /> : <Icons.pending size={12} />}
          {relativeDue(loan.expectedReturnAt)}
        </div>
      </td>
      <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>
        <div style={{ display: "inline-flex", gap: 6 }}>
          <Button variant="secondary" size="sm" icon={<Icons.ghost size={14} />}
            onClick={() => downloadGhostCard(loan.id)} title="Télécharger le fantôme PDF+QR">
            Fantôme
          </Button>
          <Button size="sm" icon={<Icons.returned size={14} weight="bold" />}
            onClick={onReturn} disabled={disabled} title="Enregistrer le retour de la boîte">
            Retour
          </Button>
        </div>
      </td>
    </tr>
  );
}
