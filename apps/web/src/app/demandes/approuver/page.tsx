"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import RoleSwitcher from "@/components/RoleSwitcher";
import {
  approveAccessRequest,
  listToApprove,
  rejectAccessRequest,
  type AccessRequestResponse,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";

export default function ApprouverPage() {
  const query = useQuery({ queryKey: ["toApprove"], queryFn: listToApprove });
  const [selected, setSelected] = useState<AccessRequestResponse | null>(null);
  const [notes, setNotes] = useState("");
  const qc = useQueryClient();

  const approveMut = useMutation({
    mutationFn: () => approveAccessRequest(selected!.id, notes),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["toApprove"] });
      setSelected(null); setNotes("");
    },
  });
  const rejectMut = useMutation({
    mutationFn: ({ reason }: { reason: string }) => rejectAccessRequest(selected!.id, reason),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["toApprove"] }); setSelected(null); },
  });

  return (
    <section>
      <RoleSwitcher />
      <Link href="/demandes">← Retour</Link>
      <h1 style={{ margin: "0.5rem 0 1rem" }}>À approuver (responsable des archives)</h1>
      <p style={{ color: "#666", fontSize: "0.9rem", marginBottom: "1rem" }}>
        4-eyes strict : tu ne peux pas approuver une demande que tu as toi-même qualifiée.
      </p>

      <div style={{ display: "grid", gridTemplateColumns: "360px 1fr", gap: "1rem" }}>
        <section style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "0.75rem", maxHeight: "72vh", overflow: "auto" }}>
          <h2 style={{ marginTop: 0, fontSize: "0.85rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555" }}>File d'attente</h2>
          {query.data?.length === 0 && <p style={{ color: "#999" }}>Rien à approuver.</p>}
          {query.data?.map((r) => (
            <button key={r.id} type="button" onClick={() => { setSelected(r); setNotes(""); }}
              style={{
                display: "block", width: "100%", textAlign: "left", padding: "0.6rem 0.8rem",
                marginBottom: 6, border: `1px solid ${selected?.id === r.id ? "#8a4b00" : "#e3e5ea"}`,
                background: selected?.id === r.id ? "#fff8e1" : "white", borderRadius: 4, cursor: "pointer",
              }}>
              <div style={{ fontWeight: 600 }}>{r.requesterName}</div>
              <div style={{ fontSize: "0.85rem", color: "#555" }}>{r.purpose}</div>
              <div style={{ fontSize: "0.75rem", color: "#999" }}>
                Qualifié par <code>{r.qualifiedBy}</code>
              </div>
            </button>
          ))}
        </section>

        <section>
          {!selected && <p style={{ color: "#999" }}>Sélectionne une demande.</p>}
          {selected && (
            <div style={{ background: "white", border: "1px solid #e3e5ea", borderLeft: "4px solid #8a4b00", borderRadius: 6, padding: "1.25rem" }}>
              <h2 style={{ marginTop: 0 }}>{selected.requesterName}</h2>
              <dl style={{ fontSize: "0.9rem" }}>
                <dt style={{ fontWeight: 600 }}>Service</dt><dd style={{ margin: "0 0 0.5rem" }}>{selected.requesterDept ?? "—"}</dd>
                <dt style={{ fontWeight: 600 }}>Type</dt><dd style={{ margin: "0 0 0.5rem" }}>{selected.type}</dd>
                <dt style={{ fontWeight: 600 }}>Motif</dt><dd style={{ margin: "0 0 0.5rem" }}>{selected.purpose}</dd>
                {selected.piecesDescription && <>
                  <dt style={{ fontWeight: 600 }}>Pièces</dt><dd style={{ margin: "0 0 0.5rem", whiteSpace: "pre-wrap" }}>{selected.piecesDescription}</dd>
                </>}
                <dt style={{ fontWeight: 600 }}>Qualification</dt>
                <dd style={{ margin: "0 0 0.5rem" }}>
                  Par <code>{selected.qualifiedBy}</code> le {formatInstant(selected.qualifiedAt!)}
                  {selected.qualificationNotes && <> · <em>{selected.qualificationNotes}</em></>}
                </dd>
              </dl>

              <section style={{ marginTop: 16 }}>
                <label style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, marginBottom: 4 }}>
                  Notes d'approbation (optionnel)
                </label>
                <textarea value={notes} onChange={(e) => setNotes(e.target.value)}
                  style={{ width: "100%", minHeight: 60, padding: "0.4rem 0.6rem", border: "1px solid #c8cbd4", borderRadius: 3, fontFamily: "inherit", fontSize: "0.9rem", boxSizing: "border-box" }} />
              </section>

              <div style={{ marginTop: 12, display: "flex", gap: 10 }}>
                <button type="button" onClick={() => approveMut.mutate()} disabled={approveMut.isPending}
                  style={{ padding: "0.5rem 1.1rem", background: "#2e7d32", color: "white", border: "none", borderRadius: 3, fontWeight: 600, cursor: "pointer" }}>
                  {approveMut.isPending ? "⏳…" : "✅ Approuver"}
                </button>
                <button type="button" onClick={() => {
                  const reason = prompt("Motif de rejet :");
                  if (reason) rejectMut.mutate({ reason });
                }} disabled={rejectMut.isPending}
                  style={{ padding: "0.5rem 1.1rem", background: "white", color: "#b71c1c", border: "1px solid #b71c1c", borderRadius: 3, fontWeight: 600, cursor: "pointer" }}>
                  🚫 Rejeter
                </button>
              </div>
              {approveMut.isError && <div style={{ color: "#a00", marginTop: 8, fontSize: "0.85rem" }}>{(approveMut.error as Error).message}</div>}
            </div>
          )}
        </section>
      </div>
    </section>
  );
}
