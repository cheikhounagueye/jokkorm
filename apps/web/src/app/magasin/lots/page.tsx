"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { getBufferZoneAlerts, listBatches, type BatchStatus } from "@/lib/api";
import { formatInstant } from "@/lib/format";

const STATUS_LABEL: Record<BatchStatus, string> = {
  CREATED: "Créé",
  PRINTED: "Imprimé",
  FULLY_DEPLOYED: "Déployé",
  ARCHIVED: "Archivé",
};

const STATUS_COLOR: Record<BatchStatus, string> = {
  CREATED: "#5b7cb3",
  PRINTED: "#a07b00",
  FULLY_DEPLOYED: "#2e7d32",
  ARCHIVED: "#777",
};

export default function LotsPage() {
  const query = useQuery({ queryKey: ["batches"], queryFn: () => listBatches() });
  const alertsQuery = useQuery({
    queryKey: ["buffer-alerts"],
    queryFn: getBufferZoneAlerts,
    refetchInterval: 60_000,
  });

  return (
    <section>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
        <div>
          <h1 style={{ margin: "0 0 0.25rem" }}>Lots de boîtes</h1>
          <p style={{ margin: 0, color: "#666", fontSize: "0.9rem" }}>
            Provisionnement en masse. Voir{" "}
            <a href="https://github.com/cheikhounagueye/jokkorm/blob/main/docs/pattern/06-provisioning-masse-boites.md"
               target="_blank" rel="noreferrer">pattern 06</a>.
          </p>
        </div>
        <Link href="/magasin/lots/new" style={{
          background: "#1e2d5e", color: "white", padding: "0.5rem 1rem",
          borderRadius: 4, textDecoration: "none", fontWeight: 500,
        }}>
          + Nouveau lot
        </Link>
      </header>

      {alertsQuery.data && alertsQuery.data.totalStaleContainers > 0 && (
        <section style={{
          background: "#fff4e5", border: "1px solid #ffb300", borderLeft: "4px solid #ed6c02",
          borderRadius: 6, padding: "0.75rem 1rem", marginBottom: "1rem",
        }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 6 }}>
            <span style={{ fontSize: "1.25rem" }}>⚠️</span>
            <strong style={{ color: "#8a4b00" }}>
              {alertsQuery.data.totalStaleContainers} boîte{alertsQuery.data.totalStaleContainers > 1 ? "s" : ""}
              {" "}en zone tampon depuis plus de {alertsQuery.data.thresholdDays} jours
            </strong>
          </div>
          <div style={{ fontSize: "0.85rem", color: "#555", marginBottom: 6 }}>
            Ces boîtes ont été provisionnées mais jamais rangées à une position physique.
            Il faut soit les ranger, soit prendre une décision (ex. retour prestataire).
          </div>
          <ul style={{ margin: "0.25rem 0 0 1.2rem", fontSize: "0.85rem" }}>
            {alertsQuery.data.byBatch.slice(0, 5).map((e) => (
              <li key={e.batchId}>
                <Link href={`/magasin/lots/${e.batchId}`} style={{ color: "#8a4b00", fontWeight: 500 }}>
                  {e.batchReference}
                </Link>
                {" — "}
                <span style={{ color: "#555" }}>
                  {e.staleCount} boîte{e.staleCount > 1 ? "s" : ""} · plus ancienne : {formatInstant(e.oldestCreatedAt)}
                </span>
              </li>
            ))}
            {alertsQuery.data.byBatch.length > 5 && (
              <li style={{ color: "#555" }}>… et {alertsQuery.data.byBatch.length - 5} lot{alertsQuery.data.byBatch.length - 5 > 1 ? "s" : ""} de plus</li>
            )}
          </ul>
        </section>
      )}

      {query.isLoading && <p>Chargement…</p>}
      {query.isError && (
        <div style={{ background: "#fff3f3", border: "1px solid #ffbaba", padding: "0.75rem", borderRadius: 4, color: "#a00" }}>
          {(query.error as Error).message}
        </div>
      )}

      {query.data && (
        <div style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, overflow: "hidden" }}>
          <table>
            <thead>
              <tr>
                <th>Référence</th>
                <th>Taille</th>
                <th>Statut</th>
                <th>Créé le</th>
                <th>Créé par</th>
                <th>Imprimé</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {query.data.length === 0 && (
                <tr><td colSpan={7} style={{ textAlign: "center", color: "#999", padding: "2rem" }}>
                  Aucun lot. Crée le premier avec le bouton en haut à droite.
                </td></tr>
              )}
              {query.data.map((b) => (
                <tr key={b.id}>
                  <td>
                    <Link href={`/magasin/lots/${b.id}`} style={{ color: "#1e2d5e", fontWeight: 500 }}>
                      {b.reference}
                    </Link>
                  </td>
                  <td>{b.size}</td>
                  <td>
                    <span style={{
                      display: "inline-block", padding: "0.15rem 0.55rem", borderRadius: 3,
                      background: STATUS_COLOR[b.status] + "22",
                      color: STATUS_COLOR[b.status], fontSize: "0.8rem", fontWeight: 600,
                    }}>
                      {STATUS_LABEL[b.status]}
                    </span>
                  </td>
                  <td>{formatInstant(b.createdAt)}</td>
                  <td style={{ color: "#555", fontSize: "0.85rem" }}>{b.createdBy}</td>
                  <td style={{ color: "#555", fontSize: "0.85rem" }}>
                    {b.lastPrintedAt ? formatInstant(b.lastPrintedAt) : <span style={{ color: "#aaa" }}>—</span>}
                  </td>
                  <td style={{ textAlign: "right" }}>
                    <Link href={`/magasin/lots/${b.id}`}>Détail →</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
