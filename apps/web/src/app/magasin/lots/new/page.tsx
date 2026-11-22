"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { createBatch } from "@/lib/api";

export default function NewLotPage() {
  const router = useRouter();
  const [reference, setReference] = useState("");
  const [size, setSize] = useState(10);
  const [labelPattern, setLabelPattern] = useState("");
  const [capacity, setCapacity] = useState("");

  const mutation = useMutation({
    mutationFn: () => createBatch({
      reference,
      size,
      labelPattern: labelPattern || undefined,
      defaultCapacity: capacity ? parseInt(capacity, 10) : undefined,
    }),
    onSuccess: (r) => router.push(`/magasin/lots/${r.batch.id}`),
  });

  return (
    <section>
      <Link href="/magasin/lots">← Retour aux lots</Link>
      <h1 style={{ margin: "0.5rem 0 1rem" }}>Nouveau lot</h1>

      <form
        onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}
        style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "1.25rem", maxWidth: 640 }}
      >
        <Field label="Référence" required>
          <input
            value={reference}
            onChange={(e) => setReference(e.target.value)}
            placeholder="ex. Commande DMG 2026-11"
            required
            maxLength={255}
            style={inputStyle}
          />
        </Field>

        <Field label="Nombre de boîtes à provisionner" required>
          <input
            type="number"
            min={1}
            max={1000}
            value={size}
            onChange={(e) => setSize(Math.max(1, Math.min(1000, parseInt(e.target.value, 10) || 1)))}
            required
            style={inputStyle}
          />
          <small style={{ color: "#666", fontSize: "0.8rem" }}>
            Entre 1 et 1000. Chaque boîte reçoit un barcode BOX-YYMMDD-xxxxxx unique.
          </small>
        </Field>

        <Field label="Libellé commun (optionnel)">
          <input
            value={labelPattern}
            onChange={(e) => setLabelPattern(e.target.value)}
            placeholder="ex. Marchés publics DMG"
            style={inputStyle}
          />
          <small style={{ color: "#666", fontSize: "0.8rem" }}>
            Suffixé automatiquement par <code>· NNN/NNN</code> pour chaque boîte. Vide = pas de libellé.
          </small>
        </Field>

        <Field label="Capacité commune (optionnel)">
          <input
            type="number"
            min={1}
            value={capacity}
            onChange={(e) => setCapacity(e.target.value)}
            placeholder="ex. 50 dossiers par boîte"
            style={inputStyle}
          />
        </Field>

        <div style={{ marginTop: "1rem", display: "flex", gap: 10 }}>
          <button type="submit" disabled={mutation.isPending || !reference} style={primaryButtonStyle}>
            {mutation.isPending ? "Création…" : `Créer le lot de ${size} boîte${size > 1 ? "s" : ""}`}
          </button>
          <Link href="/magasin/lots" style={secondaryButtonStyle}>Annuler</Link>
        </div>

        {mutation.isError && (
          <div style={{ marginTop: 10, background: "#fff3f3", border: "1px solid #ffbaba", padding: "0.5rem 0.75rem", borderRadius: 4, color: "#a00", fontSize: "0.9rem" }}>
            {(mutation.error as Error).message}
          </div>
        )}
      </form>
    </section>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: "0.9rem" }}>
      <label style={{ display: "block", fontSize: "0.85rem", fontWeight: 600, color: "#333", marginBottom: 4 }}>
        {label}{required && <span style={{ color: "#a00", marginLeft: 3 }}>*</span>}
      </label>
      {children}
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: "0.4rem 0.6rem",
  border: "1px solid #c8cbd4",
  borderRadius: 3,
  fontSize: "0.9rem",
  fontFamily: "inherit",
  width: "100%",
  boxSizing: "border-box",
  display: "block",
  marginBottom: 3,
};

const primaryButtonStyle: React.CSSProperties = {
  padding: "0.5rem 1.1rem",
  background: "#1e2d5e",
  color: "white",
  border: "none",
  borderRadius: 3,
  fontSize: "0.9rem",
  fontWeight: 600,
};

const secondaryButtonStyle: React.CSSProperties = {
  padding: "0.5rem 1.1rem",
  background: "white",
  color: "#1e2d5e",
  border: "1px solid #1e2d5e",
  borderRadius: 3,
  fontSize: "0.9rem",
  textDecoration: "none",
  display: "inline-flex",
  alignItems: "center",
};
