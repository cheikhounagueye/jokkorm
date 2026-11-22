"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useMemo, useState } from "react";

type Symbology = "CODE128" | "QR";

export default function EtiquettePage() {
  return (
    <Suspense fallback={<p>Chargement…</p>}>
      <EtiquetteInner />
    </Suspense>
  );
}

function EtiquetteInner() {
  const params = useSearchParams();
  const barcode = params.get("barcode") ?? "";
  const label = params.get("label") ?? "";

  const [symbology, setSymbology] = useState<Symbology>("CODE128");
  const [widthPx, setWidthPx] = useState(600);
  const [heightPx, setHeightPx] = useState(180);
  const [showHumanText, setShowHumanText] = useState(true);
  const [copies, setCopies] = useState(1);

  const imgSrc = useMemo(() => {
    if (!barcode) return "";
    const qs = new URLSearchParams({
      barcode,
      symbology,
      widthPx: String(widthPx),
      heightPx: String(heightPx),
      showLabel: String(showHumanText),
    });
    return `/api/v1/physical/containers/barcode-image?${qs.toString()}`;
  }, [barcode, symbology, widthPx, heightPx, showHumanText]);

  if (!barcode) {
    return <p>Aucun code-barres fourni dans l&apos;URL. Attendu : <code>?barcode=BOX-XXXXXX-XXXXXX</code>.</p>;
  }

  return (
    <>
      {/* Contrôles masqués à l'impression */}
      <section className="controls" style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "1rem", marginBottom: "1rem" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "0.75rem" }}>
          <h1 style={{ margin: 0, fontSize: "1.1rem" }}>Étiquette · <code>{barcode}</code></h1>
          <button onClick={() => window.print()} style={printButtonStyle}>
            🖨 Imprimer
          </button>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "0.75rem" }}>
          <Field label="Symbologie">
            <select value={symbology} onChange={(e) => setSymbology(e.target.value as Symbology)} style={inputStyle}>
              <option value="CODE128">Code128 (douchette 1D universelle)</option>
              <option value="QR">QR (smartphone / scanner 2D)</option>
            </select>
          </Field>
          <Field label="Largeur (px)">
            <input type="number" min={100} max={2000} value={widthPx}
                   onChange={(e) => setWidthPx(parseInt(e.target.value, 10) || 600)}
                   style={inputStyle} />
          </Field>
          <Field label="Hauteur (px, ignorée pour QR)">
            <input type="number" min={100} max={2000} value={heightPx}
                   onChange={(e) => setHeightPx(parseInt(e.target.value, 10) || 180)}
                   disabled={symbology === "QR"}
                   style={{ ...inputStyle, opacity: symbology === "QR" ? 0.5 : 1 }} />
          </Field>
          <Field label="Texte sous le barcode">
            <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: "0.9rem" }}>
              <input type="checkbox" checked={showHumanText}
                     onChange={(e) => setShowHumanText(e.target.checked)} />
              Afficher le contenu en clair
            </label>
          </Field>
          <Field label="Nombre de copies">
            <input type="number" min={1} max={100} value={copies}
                   onChange={(e) => setCopies(Math.max(1, parseInt(e.target.value, 10) || 1))}
                   style={inputStyle} />
          </Field>
        </div>
        <p style={{ margin: "0.75rem 0 0", fontSize: "0.8rem", color: "#666" }}>
          Le <strong>contenu</strong> du code-barres <code>{barcode}</code> est <strong>immuable</strong>.
          Seul le rendu visuel (symbologie, taille, texte) est configurable ci-dessus.
        </p>
      </section>

      {/* Zone imprimable — répétée selon copies */}
      <div className="print-area">
        {Array.from({ length: copies }).map((_, i) => (
          <article key={i} className="label-card" style={labelCardStyle}>
            {label && <div className="label-title" style={{ fontSize: "0.9rem", fontWeight: 600, marginBottom: 6 }}>{label}</div>}
            <img
              src={imgSrc}
              alt={barcode}
              style={{ maxWidth: "100%", display: "block" }}
            />
          </article>
        ))}
      </div>

      <style>{`
        @media print {
          /* Masque tout sauf la zone imprimable */
          .controls { display: none !important; }
          header, nav, aside { display: none !important; }
          body, html { background: white !important; margin: 0; padding: 0; }
          main { padding: 0 !important; max-width: none !important; }
          .print-area { display: block !important; }
          .label-card {
            page-break-after: always;
            break-after: page;
            border: none !important;
            padding: 0 !important;
            margin: 0 !important;
          }
          .label-card:last-child { page-break-after: auto; break-after: auto; }
        }
        @page {
          size: auto;
          margin: 10mm;
        }
      `}</style>
    </>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: "0.75rem", color: "#666", marginBottom: 3, textTransform: "uppercase", letterSpacing: "0.03em" }}>
        {label}
      </div>
      {children}
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: "0.35rem 0.55rem",
  border: "1px solid #c8cbd4",
  borderRadius: 3,
  fontSize: "0.85rem",
  fontFamily: "inherit",
  width: "100%",
  boxSizing: "border-box",
};

const printButtonStyle: React.CSSProperties = {
  padding: "0.5rem 1rem",
  background: "#1e2d5e",
  color: "white",
  border: "none",
  borderRadius: 4,
  fontSize: "0.95rem",
  fontWeight: 600,
};

const labelCardStyle: React.CSSProperties = {
  padding: "1rem",
  background: "white",
  border: "1px solid #e3e5ea",
  borderRadius: 4,
  marginBottom: "0.75rem",
  display: "inline-block",
};
