"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import ArchiveSearchForm from "@/components/ArchiveSearchForm";
import RoleSwitcher from "@/components/RoleSwitcher";
import {
  createAccessRequest,
  findContainerByBarcode,
  getCurrentRole,
  submitAccessRequest,
  type MasterSearchResult,
  type PhysicalContainerResponse,
  type RequestType,
} from "@/lib/api";

const TYPE_OPTIONS: { value: RequestType; label: string; desc: string; emoji: string }[] = [
  { value: "BORROW_BOX", label: "Prêt de la boîte entière", emoji: "📦",
    desc: "Je veux la boîte complète pour consultation étendue (audit, instruction longue)." },
  { value: "EXTRACT_PIECES", label: "Extraction de pièces", emoji: "📑",
    desc: "Je veux seulement 2-3 documents précis. La boîte reste en magasin." },
  { value: "DIGITIZE_EXTRACT", label: "Numérisation d'extraits", emoji: "🖨️",
    desc: "Je veux consulter à distance. Vous numérisez et m'envoyez un lien Maarch." },
];

/** Objet sélectionné pour la demande : soit par recherche métier, soit par scan barcode. */
type Selection = {
  containerId: string;
  displayTitle: string;    // ce qu'on affiche au user (pas de barcode)
  displayMeta?: string;    // sous-titre (service / classification)
  barcode?: string;        // seulement affiché pour ARCHIVIST
};

export default function NewDemandePage() {
  const router = useRouter();
  const [mode, setMode] = useState<"search" | "barcode">("search");
  const [selection, setSelection] = useState<Selection | null>(null);
  const [type, setType] = useState<RequestType>("BORROW_BOX");
  const [name, setName] = useState("");
  const [dept, setDept] = useState("");
  const [purpose, setPurpose] = useState("");
  const [piecesDescription, setPiecesDescription] = useState("");
  const [days, setDays] = useState(14);
  const role = typeof window !== "undefined" ? getCurrentRole() : "REQUESTER";
  const canUseBarcodeMode = role === "ARCHIVIST" || role === "ARCHIVES_HEAD";

  function selectFromSearch(r: MasterSearchResult) {
    setSelection({
      containerId: r.containerId!, // requis car availableInPhysical=true a été vérifié dans le composant
      displayTitle: r.title,
      displayMeta: `${r.service ?? ""} · ${r.classificationPath ?? r.cote}`,
      barcode: r.containerBarcode ?? undefined,
    });
  }

  const mutation = useMutation({
    mutationFn: async () => {
      const r = await createAccessRequest({
        type, containerId: selection!.containerId,
        requesterName: name, requesterDept: dept || undefined,
        purpose,
        piecesDescription: type === "BORROW_BOX" ? undefined : piecesDescription,
        expectedReturnAt: type === "BORROW_BOX"
          ? new Date(Date.now() + days * 86400_000).toISOString()
          : undefined,
      });
      await submitAccessRequest(r.id);
      return r;
    },
    onSuccess: () => router.push("/demandes"),
  });

  return (
    <section>
      <RoleSwitcher />
      <Link href="/demandes">← Retour aux demandes</Link>
      <h1 style={{ margin: "0.5rem 0 1rem" }}>Nouvelle demande d'accès</h1>

      <div style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "1.25rem", maxWidth: 900 }}>

        {!selection && (
          <>
            <div style={{ marginBottom: "1rem" }}>
              <h2 style={{ marginTop: 0, fontSize: "1rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555" }}>
                1. Quel dossier recherches-tu ?
              </h2>
              {canUseBarcodeMode && (
                <div style={{ marginBottom: "0.75rem", display: "flex", gap: 6, fontSize: "0.85rem" }}>
                  <button type="button" onClick={() => setMode("search")} style={tabBtn(mode === "search")}>
                    🔍 Recherche métier
                  </button>
                  <button type="button" onClick={() => setMode("barcode")} style={tabBtn(mode === "barcode")}>
                    📷 Scan code-barres (expert)
                  </button>
                </div>
              )}
            </div>

            {mode === "search" ? (
              <ArchiveSearchForm onSelect={selectFromSearch} />
            ) : (
              <BarcodeSearchBox onFound={(c) => setSelection({
                containerId: c.id,
                displayTitle: c.label ?? c.barcode,
                displayMeta: `Boîte · état ${c.state}`,
                barcode: c.barcode,
              })} />
            )}
          </>
        )}

        {selection && (
          <>
            <section style={{ marginBottom: "1.25rem", padding: "0.75rem 1rem", background: "#e8f5e9", border: "1px solid #a5d6a7", borderRadius: 4 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div>
                  <div style={{ fontWeight: 600 }}>✅ {selection.displayTitle}</div>
                  <div style={{ fontSize: "0.85rem", color: "#555", marginTop: 3 }}>{selection.displayMeta}</div>
                  {canUseBarcodeMode && selection.barcode && (
                    <div style={{ fontSize: "0.75rem", color: "#999", marginTop: 3 }}>
                      <code>{selection.barcode}</code>
                    </div>
                  )}
                </div>
                <button type="button" onClick={() => setSelection(null)}
                  style={{ padding: "0.25rem 0.6rem", background: "white", border: "1px solid #c8cbd4", borderRadius: 3, cursor: "pointer", fontSize: "0.8rem" }}>
                  Changer
                </button>
              </div>
            </section>

            <form onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}>
              <section style={{ marginBottom: "1.25rem" }}>
                <label style={labelStyle}>2. Que veux-tu faire ?</label>
                <div style={{ display: "grid", gap: 8 }}>
                  {TYPE_OPTIONS.map((opt) => (
                    <label key={opt.value} style={{
                      display: "block", padding: "0.75rem 1rem",
                      border: `1px solid ${type === opt.value ? "#1e2d5e" : "#e3e5ea"}`,
                      background: type === opt.value ? "#eef2ff" : "white",
                      borderRadius: 4, cursor: "pointer",
                    }}>
                      <input type="radio" name="type" value={opt.value}
                        checked={type === opt.value} onChange={() => setType(opt.value)}
                        style={{ marginRight: 8 }} />
                      <strong>{opt.emoji} {opt.label}</strong>
                      <div style={{ fontSize: "0.85rem", color: "#555", marginTop: 3, marginLeft: 22 }}>{opt.desc}</div>
                    </label>
                  ))}
                </div>
              </section>

              <section style={{ marginBottom: "1rem" }}>
                <label style={labelStyle}>3. Qui demande ? *</label>
                <input value={name} onChange={(e) => setName(e.target.value)} required placeholder="Nom complet" style={inputStyle} />
              </section>
              <section style={{ marginBottom: "1rem" }}>
                <label style={labelStyle}>Service</label>
                <input value={dept} onChange={(e) => setDept(e.target.value)} placeholder="ex. DRH, Juridique, Finances" style={inputStyle} />
              </section>
              <section style={{ marginBottom: "1rem" }}>
                <label style={labelStyle}>Motif *</label>
                <input value={purpose} onChange={(e) => setPurpose(e.target.value)} required
                  placeholder="ex. Audit RH 2026-Q4" style={inputStyle} />
              </section>

              {type !== "BORROW_BOX" && (
                <section style={{ marginBottom: "1rem" }}>
                  <label style={labelStyle}>Pièces demandées *</label>
                  <textarea value={piecesDescription} onChange={(e) => setPiecesDescription(e.target.value)} required
                    placeholder="Décris les pièces : contrat de travail signé 2015, 3 derniers bulletins de paie…"
                    style={{ ...inputStyle, minHeight: 80, resize: "vertical" }} />
                </section>
              )}

              {type === "BORROW_BOX" && (
                <section style={{ marginBottom: "1rem" }}>
                  <label style={labelStyle}>Durée du prêt (jours) *</label>
                  <input type="number" min={1} max={365} value={days}
                    onChange={(e) => setDays(Math.max(1, Math.min(365, parseInt(e.target.value, 10) || 1)))}
                    style={inputStyle} />
                  <small style={{ color: "#666", fontSize: "0.8rem" }}>
                    Retour prévu : {new Date(Date.now() + days * 86400_000).toLocaleDateString("fr-FR")}
                  </small>
                </section>
              )}

              <div style={{ marginTop: "1.25rem", display: "flex", gap: 10 }}>
                <button type="submit" disabled={!name || !purpose || mutation.isPending || (type !== "BORROW_BOX" && !piecesDescription)}
                  style={primaryBtn}>
                  {mutation.isPending ? "⏳ Envoi…" : "📨 Soumettre la demande"}
                </button>
                <Link href="/demandes" style={{ ...secondaryBtn, textDecoration: "none" }}>Annuler</Link>
              </div>

              {mutation.isError && (
                <div style={{ marginTop: 10, background: "#fff3f3", border: "1px solid #ffbaba", padding: "0.5rem 0.75rem", borderRadius: 4, color: "#a00", fontSize: "0.85rem" }}>
                  {(mutation.error as Error).message}
                </div>
              )}
              <p style={{ marginTop: 12, fontSize: "0.8rem", color: "#666" }}>
                Ta demande sera qualifiée par l'archiviste puis validée par le responsable des archives avant exécution.
              </p>
            </form>
          </>
        )}
      </div>
    </section>
  );
}

function BarcodeSearchBox({ onFound }: { onFound: (c: PhysicalContainerResponse) => void }) {
  const [barcode, setBarcode] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleLookup() {
    setError(null);
    if (!barcode.trim()) return;
    try { onFound(await findContainerByBarcode(barcode.trim())); }
    catch (e) { setError((e as Error).message); }
  }

  return (
    <div>
      <div style={{ display: "flex", gap: 8 }}>
        <input value={barcode} onChange={(e) => setBarcode(e.target.value.toUpperCase())}
          onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); handleLookup(); } }}
          placeholder="BOX-261122-XXXXXX" style={{ ...inputStyle, fontFamily: "monospace" }} autoFocus />
        <button type="button" onClick={handleLookup} style={primaryBtn}>Chercher</button>
      </div>
      {error && <div style={{ color: "#a00", fontSize: "0.85rem", marginTop: 6 }}>{error}</div>}
    </div>
  );
}

const labelStyle: React.CSSProperties = {
  display: "block", fontSize: "0.9rem", fontWeight: 600, color: "#333", marginBottom: 6,
};
const inputStyle: React.CSSProperties = {
  padding: "0.4rem 0.6rem", border: "1px solid #c8cbd4", borderRadius: 3,
  fontSize: "0.9rem", fontFamily: "inherit", width: "100%", boxSizing: "border-box",
};
const primaryBtn: React.CSSProperties = {
  padding: "0.5rem 1.1rem", background: "#1e2d5e", color: "white",
  border: "none", borderRadius: 3, fontSize: "0.9rem", fontWeight: 600, cursor: "pointer",
};
const secondaryBtn: React.CSSProperties = {
  padding: "0.5rem 1.1rem", background: "white", color: "#1e2d5e",
  border: "1px solid #1e2d5e", borderRadius: 3, fontSize: "0.9rem",
  display: "inline-flex", alignItems: "center",
};
const tabBtn = (active: boolean): React.CSSProperties => ({
  padding: "0.3rem 0.8rem",
  background: active ? "#1e2d5e" : "white",
  color: active ? "white" : "#1e2d5e",
  border: "1px solid #1e2d5e", borderRadius: 3, cursor: "pointer", fontWeight: 600,
});
