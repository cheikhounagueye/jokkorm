"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import RoleSwitcher from "@/components/RoleSwitcher";
import {
  downloadGhostCard,
  fulfillAccessRequest,
  listToFulfill,
  type AccessRequestResponse,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";

export default function ExecuterPage() {
  const query = useQuery({ queryKey: ["toFulfill"], queryFn: listToFulfill });
  const [selected, setSelected] = useState<AccessRequestResponse | null>(null);
  const [notes, setNotes] = useState("");
  const [pdfFile, setPdfFile] = useState<File | null>(null);
  const qc = useQueryClient();

  const fulfillMut = useMutation({
    mutationFn: async () => {
      let base64: string | undefined;
      if (selected!.type === "DIGITIZE_EXTRACT") {
        if (!pdfFile) throw new Error("Upload le PDF numérisé avant d'exécuter");
        const buf = await pdfFile.arrayBuffer();
        base64 = btoa(String.fromCharCode(...new Uint8Array(buf)));
      }
      return fulfillAccessRequest(selected!.id, notes || undefined, base64);
    },
    onSuccess: async (r) => {
      // Pour BORROW_BOX, télécharge le fantôme automatiquement
      if (selected?.type === "BORROW_BOX" && r.createdLoanId) {
        await downloadGhostCard(r.createdLoanId);
      }
      qc.invalidateQueries({ queryKey: ["toFulfill"] });
      setSelected(null); setNotes(""); setPdfFile(null);
    },
  });

  return (
    <section>
      <RoleSwitcher />
      <Link href="/demandes">← Retour</Link>
      <h1 style={{ margin: "0.5rem 0 1rem" }}>À exécuter (archiviste)</h1>

      <div style={{ display: "grid", gridTemplateColumns: "360px 1fr", gap: "1rem" }}>
        <section style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "0.75rem", maxHeight: "72vh", overflow: "auto" }}>
          <h2 style={{ marginTop: 0, fontSize: "0.85rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555" }}>Demandes approuvées</h2>
          {query.data?.length === 0 && <p style={{ color: "#999" }}>Rien à exécuter.</p>}
          {query.data?.map((r) => (
            <button key={r.id} type="button" onClick={() => { setSelected(r); setNotes(""); setPdfFile(null); }}
              style={{
                display: "block", width: "100%", textAlign: "left", padding: "0.6rem 0.8rem",
                marginBottom: 6, border: `1px solid ${selected?.id === r.id ? "#2e7d32" : "#e3e5ea"}`,
                background: selected?.id === r.id ? "#e8f5e9" : "white", borderRadius: 4, cursor: "pointer",
              }}>
              <div style={{ fontWeight: 600 }}>{r.requesterName}</div>
              <div style={{ fontSize: "0.85rem", color: "#555" }}>{r.type} · {r.purpose}</div>
            </button>
          ))}
        </section>

        <section>
          {!selected && <p style={{ color: "#999" }}>Sélectionne une demande.</p>}
          {selected && (
            <div style={{ background: "white", border: "1px solid #e3e5ea", borderLeft: "4px solid #2e7d32", borderRadius: 6, padding: "1.25rem" }}>
              <h2 style={{ marginTop: 0 }}>{selected.requesterName} — {selected.type}</h2>
              <dl style={{ fontSize: "0.9rem" }}>
                <dt style={{ fontWeight: 600 }}>Motif</dt><dd style={{ margin: "0 0 0.5rem" }}>{selected.purpose}</dd>
                {selected.piecesDescription && <>
                  <dt style={{ fontWeight: 600 }}>Pièces</dt><dd style={{ margin: "0 0 0.5rem", whiteSpace: "pre-wrap" }}>{selected.piecesDescription}</dd>
                </>}
                <dt style={{ fontWeight: 600 }}>Approuvé par</dt>
                <dd style={{ margin: "0 0 0.5rem" }}><code>{selected.approvedBy}</code> le {formatInstant(selected.approvedAt!)}</dd>
              </dl>

              <section style={{ marginTop: 16, background: "#f8f9fb", padding: "0.75rem 1rem", borderRadius: 4, fontSize: "0.85rem" }}>
                {selected.type === "BORROW_BOX" && (
                  <p style={{ margin: 0 }}>
                    <strong>Action :</strong> Scanne la boîte, sors-la du magasin, remets-la à l'emprunteur.
                    Le fantôme PDF+QR sera téléchargé automatiquement pour impression.
                  </p>
                )}
                {selected.type === "EXTRACT_PIECES" && (
                  <p style={{ margin: 0 }}>
                    <strong>Action :</strong> Sors temporairement la boîte, extrais les pièces listées,
                    remets la boîte en place avec un fantôme interne, remets les pièces à l'emprunteur.
                  </p>
                )}
                {selected.type === "DIGITIZE_EXTRACT" && (
                  <p style={{ margin: 0 }}>
                    <strong>Action :</strong> Sors la boîte, numérise les pièces listées, remets la boîte.
                    Upload le PDF ici — JokkoRM l'envoie à Maarch RM (actuellement <em>staging</em>).
                  </p>
                )}
              </section>

              {selected.type === "DIGITIZE_EXTRACT" && (
                <section style={{ marginTop: 12 }}>
                  <label style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, marginBottom: 4 }}>
                    PDF numérisé *
                  </label>
                  <input type="file" accept="application/pdf"
                    onChange={(e) => setPdfFile(e.target.files?.[0] ?? null)}
                    style={{ fontSize: "0.9rem" }} />
                  {pdfFile && <div style={{ fontSize: "0.8rem", color: "#555", marginTop: 4 }}>
                    📎 {pdfFile.name} ({Math.round(pdfFile.size / 1024)} KB)
                  </div>}
                </section>
              )}

              <section style={{ marginTop: 12 }}>
                <label style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, marginBottom: 4 }}>
                  Notes d'exécution (optionnel)
                </label>
                <textarea value={notes} onChange={(e) => setNotes(e.target.value)}
                  style={{ width: "100%", minHeight: 50, padding: "0.4rem 0.6rem", border: "1px solid #c8cbd4", borderRadius: 3, fontFamily: "inherit", fontSize: "0.9rem", boxSizing: "border-box" }} />
              </section>

              <div style={{ marginTop: 12, display: "flex", gap: 10 }}>
                <button type="button" onClick={() => fulfillMut.mutate()}
                  disabled={fulfillMut.isPending || (selected.type === "DIGITIZE_EXTRACT" && !pdfFile)}
                  style={{ padding: "0.5rem 1.1rem", background: "#2e7d32", color: "white", border: "none", borderRadius: 3, fontWeight: 600, cursor: "pointer" }}>
                  {fulfillMut.isPending ? "⏳ Exécution…" : "⚡ Exécuter"}
                </button>
              </div>
              {fulfillMut.isError && <div style={{ color: "#a00", marginTop: 8, fontSize: "0.85rem" }}>{(fulfillMut.error as Error).message}</div>}
            </div>
          )}
        </section>
      </div>
    </section>
  );
}
