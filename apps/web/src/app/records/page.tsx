"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { listRecords, type PhysicalArchiveStatus } from "@/lib/api";
import { formatInstant, statusColor, statusLabel } from "@/lib/format";

const STATUSES: PhysicalArchiveStatus[] = [
  "REGISTERED", "STORED", "FROZEN", "DESTROYED", "TRANSFERRED",
];

export default function RecordsPage() {
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [statusFilter, setStatusFilter] = useState<PhysicalArchiveStatus | undefined>();

  const query = useQuery({
    queryKey: ["records", page, size, statusFilter ?? "all"],
    queryFn: () => listRecords({ page, size, status: statusFilter }),
  });

  return (
    <section>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "1rem", marginBottom: "1rem" }}>
        <div>
          <h1 style={{ margin: "0 0 0.25rem" }}>Enveloppes physiques</h1>
          <p style={{ margin: 0, color: "#666", fontSize: "0.9rem" }}>
            Tenant courant : <code>demo</code>
          </p>
        </div>
        <div style={{ display: "flex", gap: "0.4rem", flexWrap: "wrap" }}>
          <Pill active={!statusFilter} onClick={() => { setStatusFilter(undefined); setPage(0); }}>
            Tous
          </Pill>
          {STATUSES.map((s) => (
            <Pill key={s} active={statusFilter === s} onClick={() => { setStatusFilter(s); setPage(0); }} color={statusColor(s)}>
              {statusLabel(s)}
            </Pill>
          ))}
        </div>
      </header>

      {query.isLoading && <p>Chargement…</p>}
      {query.isError && <ErrorBanner error={query.error as Error} />}

      {query.data && (
        <>
          <div style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, overflow: "hidden" }}>
            <table>
              <thead>
                <tr>
                  <th>Cote</th>
                  <th>Statut</th>
                  <th>Enregistrée le</th>
                  <th>Dernière transition</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {query.data.content.length === 0 && (
                  <tr><td colSpan={5} style={{ textAlign: "center", color: "#999", padding: "2rem" }}>
                    Aucune enveloppe pour les filtres actuels.
                  </td></tr>
                )}
                {query.data.content.map((r) => (
                  <tr key={r.id}>
                    <td><code>{r.cote}</code></td>
                    <td><StatusBadge status={r.status} /></td>
                    <td>{formatInstant(r.registeredAt)}</td>
                    <td>{formatInstant(r.lastTransitionAt)}</td>
                    <td style={{ textAlign: "right" }}>
                      <Link href={`/records/detail?cote=${encodeURIComponent(r.cote)}`}>Détail →</Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <Pagination
            page={query.data.page}
            totalPages={query.data.totalPages}
            totalElements={query.data.totalElements}
            onChange={setPage}
          />
        </>
      )}
    </section>
  );
}

function Pill(props: {
  active: boolean;
  onClick: () => void;
  color?: string;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={props.onClick}
      style={{
        padding: "0.35rem 0.75rem",
        borderRadius: 999,
        border: "1px solid " + (props.active ? "#1e2d5e" : "#d8dae0"),
        background: props.active ? "#1e2d5e" : "white",
        color: props.active ? "white" : (props.color ?? "#555"),
        fontSize: "0.85rem",
        fontWeight: 500,
      }}
    >
      {props.children}
    </button>
  );
}

function StatusBadge({ status }: { status: PhysicalArchiveStatus }) {
  return (
    <span style={{
      display: "inline-block",
      padding: "0.2rem 0.55rem",
      borderRadius: 4,
      background: statusColor(status) + "22",
      color: statusColor(status),
      fontSize: "0.8rem",
      fontWeight: 600,
    }}>
      {statusLabel(status)}
    </span>
  );
}

function Pagination(props: {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (p: number) => void;
}) {
  if (props.totalElements === 0) return null;
  return (
    <footer style={{
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      marginTop: "0.75rem",
      color: "#666",
      fontSize: "0.9rem",
    }}>
      <span>{props.totalElements} enveloppe(s) · page {props.page + 1} / {Math.max(props.totalPages, 1)}</span>
      <div style={{ display: "flex", gap: "0.5rem" }}>
        <button disabled={props.page === 0} onClick={() => props.onChange(props.page - 1)}>← Précédent</button>
        <button disabled={props.page >= props.totalPages - 1} onClick={() => props.onChange(props.page + 1)}>Suivant →</button>
      </div>
    </footer>
  );
}

function ErrorBanner({ error }: { error: Error }) {
  return (
    <div style={{
      background: "#fff3f3",
      border: "1px solid #ffbaba",
      padding: "0.75rem 1rem",
      borderRadius: 4,
      color: "#a00",
    }}>
      <strong>Erreur :</strong> {error.message}
    </div>
  );
}
