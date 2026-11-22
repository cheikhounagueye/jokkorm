"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { getConsolidatedRecord, type MasterStatus } from "@/lib/api";
import { formatInstant, statusColor, statusLabel } from "@/lib/format";

/**
 * useSearchParams() doit être wrappé dans un boundary Suspense en Next 15 pour
 * ne pas bailout le static rendering.
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
    queryKey: ["consolidated", cote],
    queryFn: () => getConsolidatedRecord(cote),
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
        <>
          <PhysicalPanel data={query.data.local} />
          <MasterPanel data={query.data.master} />
          <HybridExplanation />
        </>
      )}
    </section>
  );
}

function PhysicalPanel({ data }: { data: import("@/lib/api").RecordResponse }) {
  return (
    <section style={{
      background: "white",
      border: "1px solid #e3e5ea",
      borderTop: "4px solid #2e7d32",
      borderRadius: 6,
      padding: "1.25rem",
      marginTop: "1rem",
    }}>
      <h2 style={{ marginTop: 0, fontSize: "1rem", color: "#2e7d32", textTransform: "uppercase", letterSpacing: "0.03em" }}>
        Partie physique · détenue par JokkoRM
      </h2>
      <dl style={{ display: "grid", gridTemplateColumns: "180px 1fr", rowGap: "0.75rem", margin: 0 }}>
        <dt style={{ color: "#666" }}>Identifiant interne</dt>
        <dd style={{ margin: 0 }}><code>{data.id}</code></dd>

        <dt style={{ color: "#666" }}>Statut</dt>
        <dd style={{ margin: 0 }}>
          <span style={{
            display: "inline-block",
            padding: "0.3rem 0.75rem",
            borderRadius: 4,
            background: statusColor(data.status) + "22",
            color: statusColor(data.status),
            fontWeight: 600,
          }}>
            {statusLabel(data.status)}
          </span>
        </dd>

        <dt style={{ color: "#666" }}>Enregistrée le</dt>
        <dd style={{ margin: 0 }}>{formatInstant(data.registeredAt)}</dd>

        <dt style={{ color: "#666" }}>Dernière transition</dt>
        <dd style={{ margin: 0 }}>{formatInstant(data.lastTransitionAt)}</dd>
      </dl>
    </section>
  );
}

function MasterPanel({ data }: { data: import("@/lib/api").ConsolidatedRecordResponse["master"] }) {
  const status = data.status;

  if (status !== "AVAILABLE") {
    return (
      <section style={{
        background: "#fffbea",
        border: "1px solid #f0d26c",
        borderTop: "4px solid #a07b00",
        borderRadius: 6,
        padding: "1.25rem",
        marginTop: "1rem",
      }}>
        <h2 style={{ marginTop: 0, fontSize: "1rem", color: "#7a5c00", textTransform: "uppercase", letterSpacing: "0.03em" }}>
          Partie numérique · détenue par la GED maître
        </h2>
        <p style={{ margin: 0 }}>
          <MasterStatusBanner status={status} />
        </p>
        <p style={{ margin: "0.5rem 0 0", color: "#666", fontSize: "0.9rem" }}>
          Titre, plan de classement, DUA, sort final et gel contentieux sont{" "}
          <strong>détenus par la GED/SAE maître</strong> (pattern 2 gardiens). La vue physique
          ci-dessus reste valide et utilisable seule.
        </p>
      </section>
    );
  }

  return (
    <section style={{
      background: "white",
      border: "1px solid #e3e5ea",
      borderTop: "4px solid #1e2d5e",
      borderRadius: 6,
      padding: "1.25rem",
      marginTop: "1rem",
    }}>
      <h2 style={{ marginTop: 0, fontSize: "1rem", color: "#1e2d5e", textTransform: "uppercase", letterSpacing: "0.03em" }}>
        Partie numérique · détenue par la GED maître
      </h2>
      <dl style={{ display: "grid", gridTemplateColumns: "180px 1fr", rowGap: "0.75rem", margin: 0 }}>
        {data.title && (<>
          <dt style={{ color: "#666" }}>Titre</dt>
          <dd style={{ margin: 0 }}>{data.title}</dd>
        </>)}
        {data.classificationPath && (<>
          <dt style={{ color: "#666" }}>Plan de classement</dt>
          <dd style={{ margin: 0 }}><code>{data.classificationPath}</code></dd>
        </>)}
        {data.duaYears != null && (<>
          <dt style={{ color: "#666" }}>DUA</dt>
          <dd style={{ margin: 0 }}>{data.duaYears} an{data.duaYears > 1 ? "s" : ""}</dd>
        </>)}
        {data.finalFate && (<>
          <dt style={{ color: "#666" }}>Sort final</dt>
          <dd style={{ margin: 0 }}>{data.finalFate}</dd>
        </>)}
        <dt style={{ color: "#666" }}>Gel contentieux</dt>
        <dd style={{ margin: 0 }}>
          {data.legalHold
            ? <span style={{ color: "#b71c1c", fontWeight: 600 }}>Actif — toute sortie bloquée</span>
            : <span style={{ color: "#2e7d32" }}>Non</span>}
        </dd>
      </dl>
    </section>
  );
}

function MasterStatusBanner({ status }: { status: MasterStatus }) {
  if (status === "UNAVAILABLE") {
    return (
      <>
        <strong>Information maître indisponible.</strong> Soit la cote n&apos;existe pas (encore)
        côté GED maître, soit le déploiement est en mode <em>standalone</em> (sans maître
        branché — adapter <code>noop</code>).
      </>
    );
  }
  // UNREACHABLE
  return (
    <>
      <strong>GED maître injoignable.</strong> Erreur réseau, timeout ou authentification —
      les détails sont dans les logs backend. Réessayer dans quelques instants.
    </>
  );
}

function HybridExplanation() {
  return (
    <aside style={{
      marginTop: "1rem",
      padding: "0.75rem 1rem",
      background: "#f0f2f8",
      border: "1px solid #d4daee",
      borderRadius: 4,
      fontSize: "0.85rem",
      color: "#555",
    }}>
      Pattern <strong>« une seule archive, deux gardiens »</strong> — chaque dossier a deux
      supports et deux responsables. Le physique est détenu par JokkoRM, le numérique par la
      GED/SAE maître.{" "}
      <a href="https://github.com/cheikhounagueye/jokkorm/tree/main/docs/pattern"
         target="_blank" rel="noreferrer">Lire la spec complète.</a>
    </aside>
  );
}
