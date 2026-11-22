"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  downloadGhostCard,
  findContainerByBarcode,
  markLoanOut,
  requestLoan,
  type LoanResponse,
  type PhysicalContainerResponse,
} from "@/lib/api";

export default function NewPretPage() {
  const router = useRouter();
  const [barcode, setBarcode] = useState("");
  const [container, setContainer] = useState<PhysicalContainerResponse | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [borrowerName, setBorrowerName] = useState("");
  const [borrowerDept, setBorrowerDept] = useState("");
  const [purpose, setPurpose] = useState("");
  const [days, setDays] = useState(14);
  const [resultLoan, setResultLoan] = useState<LoanResponse | null>(null);

  async function handleLookup() {
    setLookupError(null);
    setContainer(null);
    if (!barcode.trim()) return;
    try {
      const c = await findContainerByBarcode(barcode.trim());
      setContainer(c);
    } catch (e) {
      setLookupError((e as Error).message);
    }
  }

  const requestMutation = useMutation({
    mutationFn: () => requestLoan({
      containerId: container!.id,
      borrowerName,
      borrowerDept: borrowerDept || undefined,
      purpose: purpose || undefined,
      expectedReturnAt: new Date(Date.now() + days * 86400_000).toISOString(),
    }),
    onSuccess: async (loan) => {
      // Enchaîne directement : marque sorti + télécharge le fantôme
      const outLoan = await markLoanOut(loan.id);
      setResultLoan(outLoan);
      await downloadGhostCard(loan.id);
    },
  });

  if (resultLoan) {
    return (
      <section>
        <div style={{ background: "#e8f5e9", border: "1px solid #a5d6a7", borderRadius: 6, padding: "1.5rem", maxWidth: 640 }}>
          <h2 style={{ marginTop: 0, color: "#2e7d32" }}>✅ Prêt enregistré, fantôme téléchargé</h2>
          <p style={{ marginBottom: "0.5rem" }}>
            La boîte <strong>{container?.barcode}</strong> est marquée <strong>sortie</strong> pour{" "}
            <strong>{resultLoan.borrowerName}</strong>.
          </p>
          <p style={{ marginBottom: "1rem", color: "#555", fontSize: "0.9rem" }}>
            Le fantôme PDF+QR a été téléchargé. Imprime-le et glisse-le dans l'emplacement
            physique de la boîte.
          </p>
          <div style={{ display: "flex", gap: 10 }}>
            <Link href="/prets" style={{ padding: "0.5rem 1rem", background: "#1e2d5e", color: "white", borderRadius: 4, textDecoration: "none", fontWeight: 500 }}>
              Voir la liste des prêts
            </Link>
            <button type="button" onClick={() => router.push("/prets/new")}
              style={{ padding: "0.5rem 1rem", background: "white", color: "#1e2d5e", border: "1px solid #1e2d5e", borderRadius: 4, cursor: "pointer" }}>
              Nouvelle demande
            </button>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section>
      <Link href="/prets">← Retour aux prêts</Link>
      <h1 style={{ margin: "0.5rem 0 1rem" }}>Nouvelle demande de prêt</h1>

      <div style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "1.25rem", maxWidth: 640 }}>
        {/* Étape 1 : scan de la boîte */}
        <section style={{ marginBottom: "1.25rem" }}>
          <label style={labelStyle}>1. Scanner ou saisir le code-barres de la boîte</label>
          <div style={{ display: "flex", gap: 8 }}>
            <input
              value={barcode}
              onChange={(e) => setBarcode(e.target.value.toUpperCase())}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); handleLookup(); } }}
              placeholder="BOX-261122-XXXXXX"
              autoFocus
              style={{ ...inputStyle, fontFamily: "monospace" }}
            />
            <button type="button" onClick={handleLookup} style={{
              padding: "0.4rem 1rem", background: "#1e2d5e", color: "white",
              border: "none", borderRadius: 3, cursor: "pointer", fontWeight: 600,
            }}>
              Chercher
            </button>
          </div>
          {lookupError && (
            <div style={{ marginTop: 8, color: "#a00", fontSize: "0.85rem" }}>{lookupError}</div>
          )}
          {container && (
            <div style={{ marginTop: 10, padding: "0.5rem 0.75rem", background: "#e8f5e9", border: "1px solid #a5d6a7", borderRadius: 4, fontSize: "0.9rem" }}>
              ✅ <strong>{container.barcode}</strong>
              {container.label && <> — {container.label}</>}
              <div style={{ fontSize: "0.8rem", color: "#555", marginTop: 3 }}>
                État : {container.state} · {container.positionId ? "rangée" : "en transit"}
              </div>
            </div>
          )}
        </section>

        {/* Étape 2 : infos emprunteur */}
        {container && (
          <form onSubmit={(e) => { e.preventDefault(); requestMutation.mutate(); }}>
            <section style={{ marginBottom: "1rem" }}>
              <label style={labelStyle}>2. Emprunteur *</label>
              <input value={borrowerName} onChange={(e) => setBorrowerName(e.target.value)}
                required placeholder="Nom complet" style={inputStyle} />
            </section>
            <section style={{ marginBottom: "1rem" }}>
              <label style={labelStyle}>Service</label>
              <input value={borrowerDept} onChange={(e) => setBorrowerDept(e.target.value)}
                placeholder="ex. DRH, Juridique, Finances" style={inputStyle} />
            </section>
            <section style={{ marginBottom: "1rem" }}>
              <label style={labelStyle}>Motif</label>
              <input value={purpose} onChange={(e) => setPurpose(e.target.value)}
                placeholder="ex. Audit RH 2026-Q4" style={inputStyle} />
            </section>
            <section style={{ marginBottom: "1rem" }}>
              <label style={labelStyle}>Durée du prêt (jours) *</label>
              <input type="number" min={1} max={365} value={days}
                onChange={(e) => setDays(Math.max(1, Math.min(365, parseInt(e.target.value, 10) || 1)))}
                style={inputStyle} />
              <small style={{ color: "#666", fontSize: "0.8rem" }}>
                Retour prévu : {new Date(Date.now() + days * 86400_000).toLocaleDateString("fr-FR")}
              </small>
            </section>

            <div style={{ marginTop: "1.25rem", display: "flex", gap: 10 }}>
              <button type="submit" disabled={!borrowerName || requestMutation.isPending} style={{
                padding: "0.6rem 1.25rem", background: requestMutation.isPending ? "#8591b3" : "#1e2d5e",
                color: "white", border: "none", borderRadius: 3, fontWeight: 600,
                cursor: requestMutation.isPending ? "wait" : "pointer",
              }}>
                {requestMutation.isPending ? "⏳ Traitement…" : "📤 Enregistrer + télécharger fantôme"}
              </button>
              <Link href="/prets" style={{ padding: "0.6rem 1.25rem", background: "white", color: "#1e2d5e", border: "1px solid #1e2d5e", borderRadius: 3, textDecoration: "none", display: "inline-flex", alignItems: "center" }}>
                Annuler
              </Link>
            </div>

            {requestMutation.isError && (
              <div style={{ marginTop: 10, background: "#fff3f3", border: "1px solid #ffbaba", padding: "0.5rem 0.75rem", borderRadius: 4, color: "#a00", fontSize: "0.85rem" }}>
                {(requestMutation.error as Error).message}
              </div>
            )}
          </form>
        )}
      </div>
    </section>
  );
}

const labelStyle: React.CSSProperties = {
  display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#333", marginBottom: 4,
};
const inputStyle: React.CSSProperties = {
  padding: "0.4rem 0.6rem", border: "1px solid #c8cbd4", borderRadius: 3,
  fontSize: "0.9rem", fontFamily: "inherit", width: "100%", boxSizing: "border-box",
};
