"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import RoleSwitcher from "@/components/RoleSwitcher";
import {
  listToQualify,
  qualifyAccessRequest,
  rejectAccessRequest,
  type AccessRequestResponse,
  type QualificationCheck,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";

export default function QualifierPage() {
  const query = useQuery({ queryKey: ["toQualify"], queryFn: listToQualify });
  const [selected, setSelected] = useState<AccessRequestResponse | null>(null);
  const [notes, setNotes] = useState("");
  const [checksResult, setChecksResult] = useState<QualificationCheck[] | null>(null);
  const qc = useQueryClient();

  const qualifyMut = useMutation({
    mutationFn: () => qualifyAccessRequest(selected!.id, notes),
    onSuccess: (r) => {
      setChecksResult(r.checks);
      if (r.qualified) {
        qc.invalidateQueries({ queryKey: ["toQualify"] });
        setSelected(null); setNotes("");
      }
    },
  });

  const rejectMut = useMutation({
    mutationFn: ({ reason }: { reason: string }) => rejectAccessRequest(selected!.id, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["toQualify"] });
      setSelected(null); setNotes("");
    },
  });

  return (
    <section>
      <RoleSwitcher />
      <Link href="/demandes">← Retour</Link>
      <h1 style={{ margin: "0.5rem 0 1rem" }}>À qualifier (archiviste)</h1>

      <div style={{ display: "grid", gridTemplateColumns: "360px 1fr", gap: "1rem" }}>
        <section style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "0.75rem", maxHeight: "72vh", overflow: "auto" }}>
          <h2 style={{ marginTop: 0, fontSize: "0.85rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555" }}>
            File d'attente
          </h2>
          {query.data && query.data.length === 0 && <p style={{ color: "#999" }}>Rien à qualifier.</p>}
          {query.data?.map((r) => (
            <button key={r.id} type="button" onClick={() => { setSelected(r); setChecksResult(null); setNotes(""); }}
              style={{
                display: "block", width: "100%", textAlign: "left", padding: "0.6rem 0.8rem",
                marginBottom: 6, border: `1px solid ${selected?.id === r.id ? "#1e2d5e" : "#e3e5ea"}`,
                background: selected?.id === r.id ? "#eef2ff" : "white", borderRadius: 4, cursor: "pointer",
              }}>
              <div style={{ fontWeight: 600, fontSize: "0.9rem" }}>{r.requesterName}</div>
              <div style={{ fontSize: "0.85rem", color: "#555" }}>{r.purpose}</div>
              <div style={{ fontSize: "0.75rem", color: "#999", marginTop: 3 }}>
                {r.type} · {formatInstant(r.createdAt)}
              </div>
            </button>
          ))}
        </section>

        <section>
          {!selected && <p style={{ color: "#999" }}>Sélectionne une demande à gauche.</p>}
          {selected && (
            <div style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "1.25rem" }}>
              <h2 style={{ marginTop: 0 }}>{selected.requesterName} <span style={{ fontSize: "0.8em", color: "#777" }}>({selected.requesterDept})</span></h2>
              <dl style={{ fontSize: "0.9rem" }}>
                <dt style={{ fontWeight: 600 }}>Type</dt><dd style={{ margin: "0 0 0.5rem" }}>{selected.type}</dd>
                <dt style={{ fontWeight: 600 }}>Motif</dt><dd style={{ margin: "0 0 0.5rem" }}>{selected.purpose}</dd>
                {selected.piecesDescription && <>
                  <dt style={{ fontWeight: 600 }}>Pièces</dt>
                  <dd style={{ margin: "0 0 0.5rem", whiteSpace: "pre-wrap" }}>{selected.piecesDescription}</dd>
                </>}
                {selected.expectedReturnAt && <>
                  <dt style={{ fontWeight: 600 }}>Retour prévu</dt>
                  <dd style={{ margin: "0 0 0.5rem" }}>{formatInstant(selected.expectedReturnAt)}</dd>
                </>}
              </dl>

              {checksResult && (
                <section style={{ marginTop: 16 }}>
                  <h3 style={{ fontSize: "0.9rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555" }}>
                    Contrôles de disponibilité
                  </h3>
                  <ul style={{ listStyle: "none", padding: 0, fontSize: "0.9rem" }}>
                    {checksResult.map((c, i) => (
                      <li key={i} style={{
                        padding: "0.35rem 0.6rem", marginBottom: 3, borderRadius: 3,
                        background: c.passed ? "#e8f5e9"
                          : c.severity === "BLOCKING" ? "#fff3f3" : "#fff4e5",
                        color: c.passed ? "#2e7d32"
                          : c.severity === "BLOCKING" ? "#b71c1c" : "#8a4b00",
                      }}>
                        {c.passed ? "✅" : c.severity === "BLOCKING" ? "🚫" : "⚠️"}
                        <strong style={{ marginLeft: 6 }}>{c.code}</strong>
                        {" — "}{c.message}
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              <section style={{ marginTop: 16 }}>
                <label style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, marginBottom: 4 }}>
                  Notes de qualification (optionnel)
                </label>
                <textarea value={notes} onChange={(e) => setNotes(e.target.value)}
                  placeholder="ex. Boîte vérifiée, OK pour validation hiérarchique"
                  style={{ width: "100%", minHeight: 60, padding: "0.4rem 0.6rem", border: "1px solid #c8cbd4", borderRadius: 3, fontFamily: "inherit", fontSize: "0.9rem", boxSizing: "border-box" }} />
              </section>

              <div style={{ marginTop: 12, display: "flex", gap: 10 }}>
                <button type="button" onClick={() => qualifyMut.mutate()} disabled={qualifyMut.isPending}
                  style={{ padding: "0.5rem 1.1rem", background: "#2e7d32", color: "white", border: "none", borderRadius: 3, fontWeight: 600, cursor: "pointer" }}>
                  {qualifyMut.isPending ? "⏳…" : "✅ Qualifier → responsable"}
                </button>
                <button type="button" onClick={() => {
                  const reason = prompt("Motif de rejet :");
                  if (reason) rejectMut.mutate({ reason });
                }} disabled={rejectMut.isPending}
                  style={{ padding: "0.5rem 1.1rem", background: "white", color: "#b71c1c", border: "1px solid #b71c1c", borderRadius: 3, fontWeight: 600, cursor: "pointer" }}>
                  🚫 Rejeter
                </button>
              </div>
              {qualifyMut.isError && <div style={{ color: "#a00", marginTop: 8, fontSize: "0.85rem" }}>{(qualifyMut.error as Error).message}</div>}
            </div>
          )}
        </section>
      </div>
    </section>
  );
}
