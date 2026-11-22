"use client";

import { useState } from "react";
import { getCurrentRole, masterSearch, type MasterSearchResult } from "@/lib/api";

const TYPOLOGIES = [
  { value: "", label: "— Toutes typologies —" },
  { value: "RH", label: "Dossier RH / Contrat RH" },
  { value: "DMG", label: "Marché public / Direction moyens généraux" },
  { value: "FIN", label: "Dossier financier" },
  { value: "FACT", label: "Facture fournisseur" },
  { value: "EC", label: "Acte d'état civil" },
  { value: "JUR", label: "Dossier juridique" },
  { value: "URBA", label: "Dossier urbanisme" },
];

/**
 * Formulaire de recherche métier (pattern 08).
 * Remplace le scan barcode pour les REQUESTER : le user cherche par critères,
 * le système fait le matching avec la boîte physique en coulisse.
 */
export default function ArchiveSearchForm({
  onSelect,
}: {
  onSelect: (result: MasterSearchResult) => void;
}) {
  const [freeText, setFreeText] = useState("");
  const [service, setService] = useState("");
  const [typologie, setTypologie] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [results, setResults] = useState<MasterSearchResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const role = typeof window !== "undefined" ? getCurrentRole() : "REQUESTER";
  const isArchivist = role === "ARCHIVIST" || role === "ARCHIVES_HEAD";

  async function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!freeText && !service && !typologie && !dateFrom && !dateTo) {
      setError("Au moins un critère de recherche est requis");
      return;
    }
    setLoading(true); setError(null); setResults(null);
    try {
      const r = await masterSearch({
        freeText: freeText || undefined,
        service: service || undefined,
        typologie: typologie || undefined,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
      });
      setResults(r);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <form onSubmit={handleSearch} style={{ background: "#f8f9fb", padding: "1rem", borderRadius: 6, marginBottom: "1rem" }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 10 }}>
          <div>
            <label style={labelStyle}>Recherche libre</label>
            <input value={freeText} onChange={(e) => setFreeText(e.target.value)}
              placeholder="Nom, mots-clés, numéro…" style={inputStyle} />
          </div>
          <div>
            <label style={labelStyle}>Service producteur</label>
            <input value={service} onChange={(e) => setService(e.target.value)}
              placeholder="DRH, Juridique, Finances…" style={inputStyle} />
          </div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10, marginBottom: 10 }}>
          <div>
            <label style={labelStyle}>Typologie</label>
            <select value={typologie} onChange={(e) => setTypologie(e.target.value)} style={inputStyle}>
              {TYPOLOGIES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
          </div>
          <div>
            <label style={labelStyle}>Période du</label>
            <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} style={inputStyle} />
          </div>
          <div>
            <label style={labelStyle}>au</label>
            <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} style={inputStyle} />
          </div>
        </div>
        <button type="submit" disabled={loading} style={{
          padding: "0.5rem 1.25rem", background: "#1e2d5e", color: "white",
          border: "none", borderRadius: 3, fontWeight: 600, cursor: loading ? "wait" : "pointer",
        }}>
          {loading ? "⏳ Recherche…" : "🔍 Rechercher"}
        </button>
        {error && <div style={{ color: "#a00", fontSize: "0.85rem", marginTop: 8 }}>{error}</div>}
      </form>

      {results && (
        <div>
          <h3 style={{ fontSize: "0.9rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555", marginBottom: 8 }}>
            {results.length} résultat{results.length > 1 ? "s" : ""}
          </h3>
          {results.length === 0 && (
            <p style={{ color: "#999", padding: "1rem", background: "white", border: "1px dashed #ccc", borderRadius: 4 }}>
              Aucun dossier ne correspond à ces critères.
            </p>
          )}
          {results.length > 0 && (
            <div style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, overflow: "hidden" }}>
              <table>
                <thead><tr>
                  <th>Dossier</th><th>Service</th><th>Période</th><th>Disponibilité</th><th />
                </tr></thead>
                <tbody>
                  {results.map((r) => (
                    <tr key={r.cote}>
                      <td>
                        <div style={{ fontWeight: 500 }}>{r.title}</div>
                        <div style={{ fontSize: "0.75rem", color: "#999" }}>
                          {r.classificationPath ?? r.cote}
                          {isArchivist && r.containerBarcode && (
                            <> · <code style={{ fontSize: "0.9em" }}>{r.containerBarcode}</code></>
                          )}
                        </div>
                      </td>
                      <td style={{ fontSize: "0.85rem" }}>{r.service ?? "—"}</td>
                      <td style={{ fontSize: "0.85rem" }}>
                        {r.dateStart && r.dateEnd
                          ? `${r.dateStart.substring(0, 4)} – ${r.dateEnd.substring(0, 4)}`
                          : r.dateStart?.substring(0, 4) ?? "—"}
                      </td>
                      <td>
                        {r.availableInPhysical ? (
                          <span style={{ color: "#2e7d32", fontSize: "0.85rem", fontWeight: 500 }}>📦 En magasin</span>
                        ) : (
                          <span style={{ color: "#999", fontSize: "0.85rem" }}>Numérique seul</span>
                        )}
                        {r.communicable === false && (
                          <span style={{ color: "#b71c1c", fontSize: "0.75rem", marginLeft: 6 }}>⚠ restreint</span>
                        )}
                      </td>
                      <td style={{ textAlign: "right" }}>
                        <button type="button" onClick={() => onSelect(r)}
                          disabled={!r.availableInPhysical}
                          title={!r.availableInPhysical ? "Pas de boîte physique associée" : ""}
                          style={{
                            padding: "0.3rem 0.8rem",
                            background: r.availableInPhysical ? "#1e2d5e" : "#ccc",
                            color: "white", border: "none", borderRadius: 3,
                            cursor: r.availableInPhysical ? "pointer" : "not-allowed",
                            fontSize: "0.8rem", fontWeight: 600,
                          }}>
                          Demander →
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

const labelStyle: React.CSSProperties = {
  display: "block", fontSize: "0.8rem", fontWeight: 600, color: "#333", marginBottom: 3,
};
const inputStyle: React.CSSProperties = {
  padding: "0.4rem 0.6rem", border: "1px solid #c8cbd4", borderRadius: 3,
  fontSize: "0.9rem", fontFamily: "inherit", width: "100%", boxSizing: "border-box",
};
