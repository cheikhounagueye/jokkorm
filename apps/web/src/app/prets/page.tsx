"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import {
  downloadGhostCard,
  listActiveLoans,
  markLoanReturned,
  type LoanResponse,
  type LoanStatus,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";

const STATUS_LABEL: Record<LoanStatus, string> = {
  REQUESTED: "Demandé",
  OUT: "Sorti",
  OVERDUE: "En retard",
  RETURNED: "Retourné",
  CANCELLED: "Annulé",
};

const STATUS_COLOR: Record<LoanStatus, string> = {
  REQUESTED: "#5b7cb3",
  OUT: "#2e7d32",
  OVERDUE: "#b71c1c",
  RETURNED: "#777",
  CANCELLED: "#999",
};

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

  return (
    <section>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
        <div>
          <h1 style={{ margin: "0 0 0.25rem" }}>Prêts physiques</h1>
          <p style={{ margin: 0, color: "#666", fontSize: "0.9rem" }}>
            Boîtes actuellement sorties du magasin — fantôme PDF+QR laissé à la place.
          </p>
        </div>
        <Link href="/prets/new" style={{
          background: "#1e2d5e", color: "white", padding: "0.5rem 1rem",
          borderRadius: 4, textDecoration: "none", fontWeight: 500,
        }}>
          + Nouvelle demande
        </Link>
      </header>

      {query.isLoading && <p>Chargement…</p>}
      {query.isError && (
        <div style={{ background: "#fff3f3", border: "1px solid #ffbaba", padding: "0.75rem", borderRadius: 4, color: "#a00" }}>
          {(query.error as Error).message}
        </div>
      )}

      {query.data && query.data.length === 0 && (
        <div style={{ background: "#e8f5e9", border: "1px solid #a5d6a7", padding: "1rem", borderRadius: 6, color: "#2e7d32" }}>
          ✅ Aucun prêt actif. Toutes les boîtes sont en magasin.
        </div>
      )}

      {query.data && query.data.length > 0 && (
        <div style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, overflow: "hidden" }}>
          <table>
            <thead>
              <tr>
                <th>Statut</th>
                <th>Emprunteur</th>
                <th>Service</th>
                <th>Motif</th>
                <th>Sortie</th>
                <th>Retour prévu</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {query.data.map((l) => (
                <LoanRow
                  key={l.id}
                  loan={l}
                  onReturn={() => returnMutation.mutate(l.id)}
                  disabled={returnMutation.isPending}
                />
              ))}
            </tbody>
          </table>
        </div>
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
  return (
    <tr style={{ background: loan.overdue ? "#fff4e5" : undefined }}>
      <td>
        <span style={{
          display: "inline-block", padding: "0.15rem 0.55rem", borderRadius: 3,
          background: STATUS_COLOR[displayStatus] + "22",
          color: STATUS_COLOR[displayStatus], fontSize: "0.8rem", fontWeight: 600,
        }}>
          {STATUS_LABEL[displayStatus]}
        </span>
      </td>
      <td style={{ fontWeight: 500 }}>{loan.borrowerName}</td>
      <td style={{ color: "#555", fontSize: "0.9rem" }}>{loan.borrowerDept ?? "—"}</td>
      <td style={{ color: "#555", fontSize: "0.85rem" }}>{loan.purpose ?? "—"}</td>
      <td style={{ fontSize: "0.85rem" }}>{loan.outAt ? formatInstant(loan.outAt) : "—"}</td>
      <td style={{ fontSize: "0.85rem", color: loan.overdue ? "#b71c1c" : undefined, fontWeight: loan.overdue ? 600 : undefined }}>
        {formatInstant(loan.expectedReturnAt)}
      </td>
      <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>
        <button type="button" onClick={() => downloadGhostCard(loan.id)}
          style={{ marginRight: 6, padding: "0.3rem 0.6rem", background: "white", border: "1px solid #1e2d5e", borderRadius: 3, color: "#1e2d5e", fontSize: "0.8rem", cursor: "pointer" }}>
          📄 Fantôme
        </button>
        <button type="button" onClick={onReturn} disabled={disabled}
          style={{ padding: "0.3rem 0.6rem", background: "#2e7d32", color: "white", border: "none", borderRadius: 3, fontSize: "0.8rem", fontWeight: 600, cursor: disabled ? "wait" : "pointer" }}>
          ↩ Retour
        </button>
      </td>
    </tr>
  );
}
