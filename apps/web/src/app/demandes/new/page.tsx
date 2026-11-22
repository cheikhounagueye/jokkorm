"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  createAccessRequest,
  findContainerByBarcode,
  submitAccessRequest,
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

export default function NewDemandePage() {
  const router = useRouter();
  const [type, setType] = useState<RequestType>("BORROW_BOX");
  const [barcode, setBarcode] = useState("");
  const [container, setContainer] = useState<PhysicalContainerResponse | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [dept, setDept] = useState("");
  const [purpose, setPurpose] = useState("");
  const [piecesDescription, setPiecesDescription] = useState("");
  const [days, setDays] = useState(14);

  async function handleLookup() {
    setLookupError(null); setContainer(null);
    if (!barcode.trim()) return;
    try { setContainer(await findContainerByBarcode(barcode.trim())); }
    catch (e) { setLookupError((e as Error).message); }
  }

  const mutation = useMutation({
    mutationFn: async () => {
      const r = await createAccessRequest({
        type, containerId: container!.id,
        requesterName: name, requesterDept: dept || undefined,
        purpose,
        piecesDescription: type === "BORROW_BOX" ? undefined : piecesDescription,
        expectedReturnAt: type === "BORROW_BOX"
          ? new Date(Date.now() + days * 86400_000).toISOString()
          : undefined,
      });
      // Enchaîne submit pour simplifier le flow utilisateur
      await submitAccessRequest(r.id);
      return r;
    },
    onSuccess: (r) => router.push(`/demandes?submitted=${r.id}`),
  });

  return (
    <section>
      <Link href="/demandes">← Retour aux demandes</Link>
      <h1 style={{ margin: "0.5rem 0 1rem" }}>Nouvelle demande d'accès</h1>

      <form onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}
        style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "1.25rem", maxWidth: 720 }}>

        <section style={{ marginBottom: "1.25rem" }}>
          <label style={labelStyle}>1. Que veux-tu faire ?</label>
          <div style={{ display: "grid", gap: 8 }}>
            {TYPE_OPTIONS.map((opt) => (
              <label key={opt.value} style={{
                display: "block", padding: "0.75rem 1rem",
                border: `1px solid ${type === opt.value ? "#1e2d5e" : "#e3e5ea"}`,
                background: type === opt.value ? "#eef2ff" : "white",
                borderRadius: 4, cursor: "pointer",
              }}>
                <input type="radio" name="type" value={opt.value}
                  checked={type === opt.value}
                  onChange={() => setType(opt.value)}
                  style={{ marginRight: 8 }} />
                <strong>{opt.emoji} {opt.label}</strong>
                <div style={{ fontSize: "0.85rem", color: "#555", marginTop: 3, marginLeft: 22 }}>{opt.desc}</div>
              </label>
            ))}
          </div>
        </section>

        <section style={{ marginBottom: "1.25rem" }}>
          <label style={labelStyle}>2. Boîte concernée — scanner ou saisir le code-barres</label>
          <div style={{ display: "flex", gap: 8 }}>
            <input value={barcode} onChange={(e) => setBarcode(e.target.value.toUpperCase())}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); handleLookup(); } }}
              placeholder="BOX-261122-XXXXXX" style={{ ...inputStyle, fontFamily: "monospace" }} />
            <button type="button" onClick={handleLookup} style={primaryBtn}>Chercher</button>
          </div>
          {lookupError && <div style={{ color: "#a00", fontSize: "0.85rem", marginTop: 6 }}>{lookupError}</div>}
          {container && (
            <div style={{ marginTop: 8, padding: "0.5rem 0.75rem", background: "#e8f5e9", border: "1px solid #a5d6a7", borderRadius: 4, fontSize: "0.9rem" }}>
              ✅ <strong>{container.barcode}</strong>{container.label && ` — ${container.label}`}
            </div>
          )}
        </section>

        {container && <>
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
                placeholder="Décris les pièces : contrat de travail Awa Diop signé 2015, 3 derniers bulletins de paie…"
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
            <button type="submit" disabled={!name || !purpose || mutation.isPending || (type !== "BORROW_BOX" && !piecesDescription)} style={primaryBtn}>
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
            Ta demande sera qualifiée par l'archiviste, puis validée par le responsable des archives
            avant exécution.
          </p>
        </>}
      </form>
    </section>
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
