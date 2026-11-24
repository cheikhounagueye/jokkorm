"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useMemo, useState } from "react";
import {
  Alert,
  Button,
  Card,
  CardHeader,
  Field,
  Input,
  PageHeader,
  Segmented,
} from "@/components/ui";
import { Icons } from "@/lib/icons";
import { useTenantImage } from "@/lib/useTenantImage";
import { BoxPicker } from "../_components/BoxPicker";
import { etiquetteHref } from "../_components/kinds";

type Symbology = "CODE128" | "QR";

const CRUMBS = [
  { label: "Salle d'archives", href: "/magasin" },
  { label: "Étiquettes", href: "/magasin/etiquette" },
  { label: "Boîte" },
];

export default function EtiquettePage() {
  return (
    <Suspense fallback={<Card><p style={{ color: "var(--text-tertiary)", fontSize: 13 }}>Chargement…</p></Card>}>
      <EtiquetteInner />
    </Suspense>
  );
}

function EtiquetteInner() {
  const router = useRouter();
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
  const image = useTenantImage(imgSrc);

  if (!barcode) {
    return (
      <section className="fade-up">
        <PageHeader icon="label" tone="kraft" title="Étiquettes"
          subtitle="Une étiquette dit qui est la boîte ; où elle est et dans quel état, le scan le dit."
          crumbs={[{ label: "Salle d'archives", href: "/magasin" }, { label: "Étiquettes" }]} />
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 460px) minmax(0, 1fr)", gap: 20, alignItems: "start" }}>
          <Card>
            <CardHeader title="Étiquette de boîte" subtitle="Réimprimer redonne toujours le même code." />
            <BoxPicker allowCreate={false} forContent={false}
              onPick={(h) => router.push(etiquetteHref(h.container.barcode, h.container.label))} />
          </Card>
          <Card>
            <CardHeader title="Autres étiquettes" />
            <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, lineHeight: 1.6, color: "var(--text-secondary)" }}>
              <li><strong>Lot de boîtes</strong> : la planche entière d&apos;un lot, depuis <Link href="/magasin/lots">Lots de boîtes</Link>.</li>
              <li><strong>Emplacements</strong> : choisis une salle, une travée ou une tablette dans la{" "}
                <Link href="/magasin">Salle d&apos;archives</Link>, puis « Étiquettes d&apos;emplacement ».</li>
              <li><strong>Fantôme de prêt</strong> : imprimé à la sortie de la boîte, depuis <Link href="/prets">Prêts</Link>.</li>
            </ul>
          </Card>
        </div>
      </section>
    );
  }

  return (
    <section className="fade-up">
      <div className="no-print">
        <PageHeader
          icon="label"
          tone="kraft"
          title="Étiquette de boîte"
          subtitle={<>Code-barres <code>{barcode}</code>{label && <> · {label}</>}</>}
          crumbs={CRUMBS}
          actions={
            <Button size="lg" onClick={() => window.print()} icon={<Icons.print size={16} />}>
              Imprimer {copies > 1 ? `${copies} étiquettes` : "l'étiquette"}
            </Button>
          }
        />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "minmax(300px, 380px) minmax(0, 1fr)", gap: 20, alignItems: "start" }}
        className="etiquette-layout">
        <div className="no-print">
          <Card>
            <CardHeader title="Rendu" subtitle="Le contenu du code-barres reste immuable." />

            <div style={{ fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", marginBottom: 6 }}>Symbologie</div>
            <div style={{ marginBottom: 16 }}>
              <Segmented<Symbology>
                value={symbology}
                onChange={setSymbology}
                options={[
                  { value: "CODE128", label: "Code128", icon: <Icons.barcode size={16} /> },
                  { value: "QR", label: "QR", icon: <Icons.qr size={16} /> },
                ]}
              />
              <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 6 }}>
                {symbology === "CODE128" ? "Lu par toutes les douchettes 1D." : "Lu par smartphone ou scanner 2D."}
              </div>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <Field label="Largeur (px)">
                <Input type="number" min={100} max={2000} value={widthPx}
                  onChange={(e) => setWidthPx(parseInt(e.target.value, 10) || 600)} />
              </Field>
              <Field label="Hauteur (px)" hint={symbology === "QR" ? "Ignorée pour le QR (carré)." : undefined}>
                <Input type="number" min={100} max={2000} value={heightPx}
                  onChange={(e) => setHeightPx(parseInt(e.target.value, 10) || 180)}
                  disabled={symbology === "QR"}
                  style={{ opacity: symbology === "QR" ? 0.5 : 1 }} />
              </Field>
            </div>

            <Field label="Nombre de copies">
              <Input type="number" min={1} max={100} value={copies}
                onChange={(e) => setCopies(Math.max(1, parseInt(e.target.value, 10) || 1))} />
            </Field>

            <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, color: "var(--text-primary)", marginBottom: 16, cursor: "pointer" }}>
              <input type="checkbox" checked={showHumanText}
                onChange={(e) => setShowHumanText(e.target.checked)}
                style={{ width: 16, height: 16, padding: 0 }} />
              Afficher le code en clair sous le code-barres
            </label>

            <Alert tone="info">
              Seuls la symbologie, la taille et le texte changent. Le code <code>{barcode}</code> ne peut pas être modifié.
            </Alert>
          </Card>
        </div>

        <div className="label-backing">
          {image.error && (
            <div className="no-print" style={{ marginBottom: 16 }}>
              <Alert tone="danger" title="Code-barres indisponible">{image.error}</Alert>
            </div>
          )}
          <div className="print-area" style={{ display: "flex", flexWrap: "wrap", gap: 16 }}>
            {Array.from({ length: copies }).map((_, i) => (
              <article key={i} className="label-card">
                {label && <div className="label-title">{label}</div>}
                {image.src ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={image.src} alt={`Code-barres ${barcode}`} style={{ maxWidth: "100%", display: "block" }} />
                ) : (
                  <div style={{ width: 300, height: 90, borderRadius: 4, background: "var(--surface-sunken)" }} />
                )}
              </article>
            ))}
          </div>
        </div>
      </div>

      <style>{`
        .label-backing {
          padding: 24px;
          border-radius: var(--radius-lg);
          border: 1px solid var(--border-subtle);
          background-color: color-mix(in srgb, var(--kraft-500) 12%, var(--surface-sunken));
          background-image: radial-gradient(color-mix(in srgb, var(--kraft-700) 18%, transparent) 1px, transparent 1px);
          background-size: 14px 14px;
        }
        .label-card {
          display: inline-block;
          max-width: 100%;
          background: #ffffff;
          color: #111111;
          padding: 14px 16px;
          border: 1px solid #d9cdb6;
          border-radius: 10px;
          box-shadow: 0 1px 0 rgba(255,255,255,0.8) inset, 0 8px 20px -8px rgba(60, 40, 10, 0.35);
        }
        .label-title {
          font-size: 13px;
          font-weight: 600;
          margin-bottom: 8px;
        }
        @media print {
          .etiquette-layout { display: block !important; }
          .label-backing { padding: 0 !important; border: none !important; background: none !important; }
          .print-area { display: block !important; }
          .label-card {
            display: block;
            page-break-after: always;
            break-after: page;
            border: none !important;
            box-shadow: none !important;
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
    </section>
  );
}
