"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import RoleSwitcher from "@/components/RoleSwitcher";
import {
  getCurrentRole,
  listMyRequests,
  listToApprove,
  listToFulfill,
  listToQualify,
  type AccessRequestResponse,
  type AccessRequestStatus,
  type RequestType,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";

const STATUS_LABEL: Record<AccessRequestStatus, string> = {
  DRAFT: "Brouillon", SUBMITTED: "Soumis", QUALIFIED: "Qualifié",
  APPROVED: "Approuvé", FULFILLED: "Exécuté", COMPLETED: "Clôturé",
  REJECTED: "Rejeté", CANCELLED: "Annulé",
};
const STATUS_COLOR: Record<AccessRequestStatus, string> = {
  DRAFT: "#777", SUBMITTED: "#5b7cb3", QUALIFIED: "#a07b00",
  APPROVED: "#2e7d32", FULFILLED: "#1e2d5e", COMPLETED: "#555",
  REJECTED: "#b71c1c", CANCELLED: "#999",
};
const TYPE_LABEL: Record<RequestType, string> = {
  BORROW_BOX: "Prêt boîte", EXTRACT_PIECES: "Extraction pièces", DIGITIZE_EXTRACT: "Numérisation",
};
const TYPE_EMOJI: Record<RequestType, string> = {
  BORROW_BOX: "📦", EXTRACT_PIECES: "📑", DIGITIZE_EXTRACT: "🖨️",
};

export default function DemandesPage() {
  const role = typeof window !== "undefined" ? getCurrentRole() : "REQUESTER";

  const mine = useQuery({ queryKey: ["mine", role], queryFn: listMyRequests, enabled: role === "REQUESTER" });
  const toQualify = useQuery({ queryKey: ["toQualify", role], queryFn: listToQualify,
    enabled: role === "ARCHIVIST" || role === "ARCHIVES_HEAD" });
  const toApprove = useQuery({ queryKey: ["toApprove", role], queryFn: listToApprove,
    enabled: role === "ARCHIVES_HEAD" });
  const toFulfill = useQuery({ queryKey: ["toFulfill", role], queryFn: listToFulfill,
    enabled: role === "ARCHIVIST" });

  return (
    <section>
      <RoleSwitcher />
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
        <div>
          <h1 style={{ margin: "0 0 0.25rem" }}>Demandes d'accès</h1>
          <p style={{ margin: 0, color: "#666", fontSize: "0.9rem" }}>
            Workflow 4-eyes · 3 types · voir{" "}
            <a href="https://github.com/cheikhounagueye/jokkorm/blob/main/docs/pattern/07-demandes-utilisateurs-multi-types.md"
               target="_blank" rel="noreferrer">pattern 07</a>.
          </p>
        </div>
        {(role === "REQUESTER" || role === "ARCHIVIST" || role === "ARCHIVES_HEAD") && (
          <Link href="/demandes/new" style={{
            background: "#1e2d5e", color: "white", padding: "0.5rem 1rem",
            borderRadius: 4, textDecoration: "none", fontWeight: 500,
          }}>+ Nouvelle demande</Link>
        )}
      </header>

      {role === "REQUESTER" && <Section title="Mes demandes" query={mine} empty="Tu n'as pas encore de demande." />}

      {(role === "ARCHIVIST" || role === "ARCHIVES_HEAD") && (
        <Section title="📋 À qualifier (archiviste)" query={toQualify}
          empty="Aucune demande en attente de qualification." actionHref="/demandes/qualifier" actionLabel="Qualifier →" />
      )}

      {role === "ARCHIVES_HEAD" && (
        <Section title="✅ À approuver (responsable)" query={toApprove}
          empty="Aucune demande en attente d'approbation." actionHref="/demandes/approuver" actionLabel="Approuver →" />
      )}

      {role === "ARCHIVIST" && (
        <Section title="⚡ À exécuter (archiviste)" query={toFulfill}
          empty="Aucune demande approuvée en attente d'exécution." actionHref="/demandes/executer" actionLabel="Exécuter →" />
      )}

      {role === "AUDITOR" && (
        <div style={{ background: "#f5f5f5", padding: "1rem", borderRadius: 4, color: "#555", fontSize: "0.9rem" }}>
          Rôle AUDITOR : lecture seule — vue historique à venir en phase 5P.J.
        </div>
      )}
    </section>
  );
}

function Section({ title, query, empty, actionHref, actionLabel }: {
  title: string;
  query: ReturnType<typeof useQuery<AccessRequestResponse[]>>;
  empty: string;
  actionHref?: string;
  actionLabel?: string;
}) {
  return (
    <section style={{ marginBottom: "1.5rem" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.5rem" }}>
        <h2 style={{ margin: 0, fontSize: "1rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555" }}>{title}</h2>
        {actionHref && query.data && query.data.length > 0 && (
          <Link href={actionHref} style={{ fontSize: "0.85rem", color: "#1e2d5e" }}>{actionLabel}</Link>
        )}
      </div>
      {query.isLoading && <p style={{ color: "#999" }}>Chargement…</p>}
      {query.isError && <p style={{ color: "#a00", fontSize: "0.85rem" }}>{(query.error as Error).message}</p>}
      {query.data && query.data.length === 0 && (
        <p style={{ color: "#999", background: "white", padding: "1rem", border: "1px dashed #ccc", borderRadius: 4 }}>{empty}</p>
      )}
      {query.data && query.data.length > 0 && (
        <div style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, overflow: "hidden" }}>
          <table>
            <thead><tr>
              <th>Type</th><th>Demandeur</th><th>Objet</th><th>Statut</th><th>Créée</th>
            </tr></thead>
            <tbody>
              {query.data.map((r) => (
                <tr key={r.id}>
                  <td>{TYPE_EMOJI[r.type]} {TYPE_LABEL[r.type]}</td>
                  <td style={{ fontWeight: 500 }}>{r.requesterName} <span style={{ color: "#777", fontSize: "0.85em" }}>({r.requesterDept ?? "—"})</span></td>
                  <td style={{ fontSize: "0.9rem", color: "#555" }}>{r.purpose}</td>
                  <td>
                    <span style={{ display: "inline-block", padding: "0.15rem 0.55rem", borderRadius: 3,
                      background: STATUS_COLOR[r.status] + "22", color: STATUS_COLOR[r.status],
                      fontSize: "0.8rem", fontWeight: 600 }}>
                      {STATUS_LABEL[r.status]}
                    </span>
                  </td>
                  <td style={{ fontSize: "0.85rem", color: "#555" }}>{formatInstant(r.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
