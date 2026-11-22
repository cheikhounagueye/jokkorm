"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { downloadBatchLabelsPdf, getBatchHealth, type BatchStatus } from "@/lib/api";
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

export default function LotDetailPage() {
  const params = useParams<{ id: string }>();
  const batchId = params.id;

  const [template, setTemplate] = useState<"AVERY_L7160" | "AVERY_L7159" | "AVERY_L7169">("AVERY_L7160");
  const [symbology, setSymbology] = useState<"CODE128" | "QR">("CODE128");
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  async function handleDownload() {
    setDownloading(true);
    setDownloadError(null);
    try {
      await downloadBatchLabelsPdf(batchId, template, symbology);
      // Après succès, re-fetch health pour afficher firstPrintedAt
      query.refetch();
    } catch (e) {
      setDownloadError((e as Error).message);
    } finally {
      setDownloading(false);
    }
  }

  const query = useQuery({
    queryKey: ["batch-health", batchId],
    queryFn: () => getBatchHealth(batchId),
    refetchInterval: 15_000,
  });

  if (query.isLoading) return <p>Chargement…</p>;
  if (query.isError) return (
    <div>
      <Link href="/magasin/lots">← Retour aux lots</Link>
      <p style={{ color: "#a00", marginTop: "1rem" }}>{(query.error as Error).message}</p>
    </div>
  );
  if (!query.data) return null;

  const h = query.data;
  const b = h.batch;

  return (
    <section>
      <Link href="/magasin/lots">← Retour aux lots</Link>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginTop: "0.5rem", marginBottom: "1rem" }}>
        <div>
          <h1 style={{ margin: "0 0 0.25rem" }}>{b.reference}</h1>
          <p style={{ margin: 0, color: "#666", fontSize: "0.9rem" }}>
            <strong>{b.size}</strong> boîte{b.size > 1 ? "s" : ""} · créé le {formatInstant(b.createdAt)} par <code>{b.createdBy}</code>
          </p>
        </div>
        <span style={{
          display: "inline-block", padding: "0.3rem 0.75rem", borderRadius: 4,
          background: STATUS_COLOR[b.status] + "22", color: STATUS_COLOR[b.status], fontWeight: 600,
        }}>
          {STATUS_LABEL[b.status]}
        </span>
      </header>

      {/* Progress bar */}
      <section style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "1.25rem", marginBottom: "0.75rem" }}>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6, fontSize: "0.85rem", color: "#555" }}>
          <span>Déploiement : <strong>{h.deployed}/{h.total}</strong> boîtes rangées</span>
          <span style={{ fontWeight: 600 }}>{h.deploymentPercent}%</span>
        </div>
        <div style={{ width: "100%", height: 10, background: "#eef0f5", borderRadius: 5, overflow: "hidden" }}>
          <div style={{
            width: `${h.deploymentPercent}%`, height: "100%",
            background: h.deploymentPercent === 100 ? "#2e7d32" : "#1e2d5e",
            transition: "width 0.3s",
          }} />
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 10, marginTop: "1rem" }}>
          <StatCard label="En transit" value={h.inTransit} color="#a07b00" />
          <StatCard label="Vides rangées" value={h.storedEmpty} color="#5b7cb3" />
          <StatCard label="Ouvertes" value={h.open} color="#2e7d32" />
          <StatCard label="Scellées" value={h.sealed} color="#a07b00" />
          <StatCard label="Détruites" value={h.destroyed} color="#b71c1c" />
        </div>
      </section>

      {/* Impression PDF */}
      <section style={{ background: "white", border: "1px solid #e3e5ea", borderTop: "4px solid #1e2d5e", borderRadius: 6, padding: "1.25rem" }}>
        <h2 style={{ marginTop: 0, fontSize: "1rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555" }}>
          Imprimer les étiquettes (PDF A4)
        </h2>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: "0.75rem" }}>
          <div>
            <div style={{ fontSize: "0.8rem", color: "#666", marginBottom: 3 }}>Template Avery</div>
            <select value={template} onChange={(e) => setTemplate(e.target.value as typeof template)} style={inputStyle}>
              <option value="AVERY_L7160">L7160 · 21 étiq/A4 (63.5 × 38.1 mm)</option>
              <option value="AVERY_L7159">L7159 · 24 étiq/A4 (63.5 × 33.9 mm)</option>
              <option value="AVERY_L7169">L7169 · 16 étiq/A4 (99.1 × 33.9 mm — allongé)</option>
            </select>
          </div>
          <div>
            <div style={{ fontSize: "0.8rem", color: "#666", marginBottom: 3 }}>Symbologie</div>
            <select value={symbology} onChange={(e) => setSymbology(e.target.value as typeof symbology)} style={inputStyle}>
              <option value="CODE128">Code128 (douchette 1D)</option>
              <option value="QR">QR (smartphone 2D)</option>
            </select>
          </div>
        </div>
        <button
          type="button"
          onClick={handleDownload}
          disabled={downloading}
          style={{
            display: "inline-block", padding: "0.6rem 1.25rem",
            background: downloading ? "#8591b3" : "#1e2d5e",
            color: "white", border: "none", borderRadius: 4,
            fontWeight: 600, fontSize: "0.95rem",
            cursor: downloading ? "wait" : "pointer",
          }}>
          {downloading
            ? "⏳ Génération…"
            : `📥 Télécharger PDF (${Math.ceil(b.size / templatePerPage(template))} page${Math.ceil(b.size / templatePerPage(template)) > 1 ? "s" : ""} A4)`}
        </button>
        {downloadError && (
          <div style={{ marginTop: 10, background: "#fff3f3", border: "1px solid #ffbaba", padding: "0.5rem 0.75rem", borderRadius: 4, color: "#a00", fontSize: "0.85rem" }}>
            {downloadError}
          </div>
        )}
        {b.firstPrintedAt && (
          <p style={{ margin: "0.5rem 0 0", color: "#666", fontSize: "0.85rem" }}>
            Première impression : {formatInstant(b.firstPrintedAt)}
            {b.lastPrintedAt !== b.firstPrintedAt && <> · dernière : {formatInstant(b.lastPrintedAt!)}</>}
          </p>
        )}
      </section>
    </section>
  );
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={{ padding: "0.5rem 0.75rem", background: color + "11", border: `1px solid ${color}44`, borderRadius: 4 }}>
      <div style={{ fontSize: "0.75rem", color: "#555", textTransform: "uppercase", letterSpacing: "0.03em" }}>{label}</div>
      <div style={{ fontSize: "1.4rem", fontWeight: 700, color }}>{value}</div>
    </div>
  );
}

function templatePerPage(t: string): number {
  return t === "AVERY_L7159" ? 24 : t === "AVERY_L7169" ? 16 : 21;
}

const inputStyle: React.CSSProperties = {
  padding: "0.4rem 0.6rem",
  border: "1px solid #c8cbd4",
  borderRadius: 3,
  fontSize: "0.9rem",
  fontFamily: "inherit",
  width: "100%",
  boxSizing: "border-box",
};
