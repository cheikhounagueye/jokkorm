"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { findRecordByCote } from "@/lib/api";
import { formatInstant, statusColor, statusLabel } from "@/lib/format";

/**
 * useSearchParams() doit être wrappé dans un boundary Suspense en Next 15 pour
 * ne pas bailout le static rendering. Le wrapper est la page elle-même ;
 * le vrai contenu est dans RecordDetail.
 */
export default function RecordDetailPage() {
  return (
    <Suspense fallback={<p>Chargement…</p>}>
      <RecordDetail />
    </Suspense>
  );
}

function RecordDetail() {
  const params = useSearchParams();
  const cote = params.get("cote") ?? "";

  const query = useQuery({
    enabled: cote.length > 0,
    queryKey: ["record", cote],
    queryFn: () => findRecordByCote(cote),
  });

  if (!cote) {
    return <p>Aucune cote fournie. <Link href="/records">← Retour à la liste</Link></p>;
  }

  return (
    <section>
      <Link href="/records">← Retour à la liste</Link>
      <h1 style={{ margin: "0.5rem 0 0.25rem" }}>
        <code style={{ fontSize: "1.3rem" }}>{cote}</code>
      </h1>

      {query.isLoading && <p>Chargement…</p>}
      {query.isError && (
        <div style={{ background: "#fff3f3", border: "1px solid #ffbaba", padding: "0.75rem 1rem", borderRadius: 4, marginTop: "1rem", color: "#a00" }}>
          <strong>Erreur :</strong> {(query.error as Error).message}
        </div>
      )}

      {query.data && (
        <div style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "1.25rem", marginTop: "1rem" }}>
          <dl style={{ display: "grid", gridTemplateColumns: "180px 1fr", rowGap: "0.75rem", margin: 0 }}>
            <dt style={{ color: "#666" }}>Identifiant interne</dt>
            <dd style={{ margin: 0 }}><code>{query.data.id}</code></dd>

            <dt style={{ color: "#666" }}>Statut</dt>
            <dd style={{ margin: 0 }}>
              <span style={{
                display: "inline-block",
                padding: "0.3rem 0.75rem",
                borderRadius: 4,
                background: statusColor(query.data.status) + "22",
                color: statusColor(query.data.status),
                fontWeight: 600,
              }}>
                {statusLabel(query.data.status)}
              </span>
            </dd>

            <dt style={{ color: "#666" }}>Enregistrée le</dt>
            <dd style={{ margin: 0 }}>{formatInstant(query.data.registeredAt)}</dd>

            <dt style={{ color: "#666" }}>Dernière transition</dt>
            <dd style={{ margin: 0 }}>{formatInstant(query.data.lastTransitionAt)}</dd>
          </dl>

          <hr style={{ border: "none", borderTop: "1px solid #e3e5ea", margin: "1.25rem 0" }} />

          <h2 style={{ marginTop: 0, fontSize: "1rem", color: "#555" }}>Métadonnées côté maître</h2>
          <p style={{ color: "#999", margin: 0 }}>
            Titre, plan de classement, DUA, sort final, gel contentieux —{" "}
            <strong>détenus par la GED/SAE maître</strong>, à récupérer via{" "}
            <code>SaeMasterPort.fetchByCote</code>. L&apos;écran consolidé arrive en phase 4.
          </p>
        </div>
      )}
    </section>
  );
}
