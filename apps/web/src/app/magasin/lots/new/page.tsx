"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  Alert,
  Button,
  Card,
  CardHeader,
  Field,
  Input,
  PageHeader,
} from "@/components/ui";
import { cm, createBatch, ml } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { BoxSpine, FormatPicker } from "../../_components/FormatPicker";
import { spineLabel, useBoxFormats } from "../../_components/useBoxFormats";
import { BoxPile } from "../_components/BoxPile";
import { AVERY_TEMPLATES, LabelSticker } from "../_components/LabelSheet";

function todayYYMMDD(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, "0");
  return `${String(d.getFullYear()).slice(2)}${p(d.getMonth() + 1)}${p(d.getDate())}`;
}

export default function NewLotPage() {
  const router = useRouter();
  const [reference, setReference] = useState("");
  const [size, setSize] = useState(10);
  const [labelPattern, setLabelPattern] = useState("");
  const [capacity, setCapacity] = useState("");
  const boxFormats = useBoxFormats();
  // Vide = le format par défaut du tenant, connu une fois le catalogue chargé.
  const [chosenFormatId, setChosenFormatId] = useState("");
  const formatId = chosenFormatId || boxFormats.defaultFormat?.id || "";
  const format = boxFormats.formatOf(formatId);

  const mutation = useMutation({
    mutationFn: () => createBatch({
      reference,
      size,
      labelPattern: labelPattern || undefined,
      defaultCapacity: capacity ? parseInt(capacity, 10) : undefined,
      boxFormatId: formatId || undefined,
    }),
    onSuccess: (r) => router.push(`/magasin/lots/${r.batch.id}`),
  });

  const digits = Math.max(3, String(size).length);
  const seq = `${"1".padStart(digits, "0")}/${String(size).padStart(digits, "0")}`;
  const pages = Math.ceil(size / AVERY_TEMPLATES.AVERY_L7160.perPage);
  /** Longueur de rayonnage que le lot occupera une fois rangé (pattern 10). */
  const lotLengthMm = format ? size * format.spineMm : null;

  return (
    <section className="fade-up">
      <PageHeader
        icon="batches"
        tone="kraft"
        title="Nouveau lot"
        subtitle="Provisionne des boîtes en une fois : codes-barres générés, étiquettes prêtes à imprimer."
        crumbs={[
          { label: "Salle d'archives", href: "/magasin" },
          { label: "Lots de boîtes", href: "/magasin/lots" },
          { label: "Nouveau lot" },
        ]}
      />

      <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1.25fr) minmax(320px, 1fr)", gap: 20, alignItems: "start" }}>
        <Card>
          <CardHeader title="Paramètres du lot" subtitle="Seuls la référence et le nombre de boîtes sont obligatoires." />
          <form onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}>
            <Field label="Référence" required hint="Usage interne : bon de commande, livraison prestataire…">
              <Input
                value={reference}
                onChange={(e) => setReference(e.target.value)}
                placeholder="ex. Commande DMG 2026-11"
                required
                maxLength={255}
              />
            </Field>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
              <Field label="Nombre de boîtes" required hint="Entre 1 et 1000. Un code-barres unique par boîte.">
                <Input
                  type="number"
                  min={1}
                  max={1000}
                  value={size}
                  onChange={(e) => setSize(Math.max(1, Math.min(1000, parseInt(e.target.value, 10) || 1)))}
                  required
                />
              </Field>
              <Field label="Capacité par boîte" hint="Nombre de dossiers, pour signaler une boîte pleine.">
                <Input
                  type="number"
                  min={1}
                  value={capacity}
                  onChange={(e) => setCapacity(e.target.value)}
                  placeholder="ex. 50"
                />
              </Field>
            </div>

            <Field label="Libellé commun" hint="Imprimé sur chaque étiquette, suivi automatiquement de · NNN/NNN.">
              <Input
                value={labelPattern}
                onChange={(e) => setLabelPattern(e.target.value)}
                placeholder="ex. Marchés publics DMG"
              />
            </Field>

            <Field label="Format des boîtes"
              hint={boxFormats.query.isError
                ? "Catalogue indisponible : le format par défaut du tenant sera appliqué."
                : "Le dos de la boîte fixe la place prise sur la tablette."}>
              {boxFormats.active.length > 0 ? (
                <FormatPicker formats={boxFormats.active} value={formatId} onChange={setChosenFormatId} />
              ) : (
                <div style={{ fontSize: 13, color: "var(--text-tertiary)" }}>
                  {boxFormats.query.isLoading ? "Chargement du catalogue…" : "Aucun format actif."}
                </div>
              )}
              <div style={{ marginTop: 8, fontSize: 12 }}>
                <Link href="/magasin/formats" style={{ color: "var(--brand-600)", fontWeight: 500 }}>
                  Gérer le catalogue des formats
                </Link>
              </div>
            </Field>

            {mutation.isError && (
              <div style={{ marginBottom: 16 }}>
                <Alert tone="danger" title="Création impossible">{(mutation.error as Error).message}</Alert>
              </div>
            )}

            <div style={{ display: "flex", gap: 10, paddingTop: 4 }}>
              <Button type="submit" size="lg" loading={mutation.isPending} disabled={!reference}
                icon={<Icons.add size={16} weight="bold" />}>
                Créer le lot de {size} boîte{size > 1 ? "s" : ""}
              </Button>
              <Link href="/magasin/lots">
                <Button type="button" variant="secondary" size="lg">Annuler</Button>
              </Link>
            </div>
          </form>
        </Card>

        <div style={{ position: "sticky", top: 80 }}>
          <Card>
            <CardHeader title="Aperçu" subtitle="Ce qui sera provisionné et imprimé." />

            <div style={{
              padding: 18,
              borderRadius: "var(--radius-md)",
              background: "color-mix(in srgb, var(--kraft-500) 10%, var(--surface-sunken))",
              border: "1px solid var(--border-subtle)",
              marginBottom: 18,
            }}>
              <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)", marginBottom: 10 }}>
                Étiquette de la boîte n° 1
              </div>
              <LabelSticker
                title={labelPattern ? `${labelPattern} · ${seq}` : "Sans libellé"}
                seq={seq}
                code={`BOX-${todayYYMMDD()}-XXXXXX`}
              />
            </div>

            <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)", marginBottom: 12 }}>
              Le lot
            </div>
            <div style={{ minHeight: 120, display: "flex", alignItems: "flex-end", marginBottom: 16 }}>
              <BoxPile count={size} max={24} perRow={6} size={46} />
            </div>

            {format && lotLengthMm !== null && (
              <div style={{
                display: "flex", alignItems: "flex-end", gap: 14, padding: "12px 14px", marginBottom: 16,
                borderRadius: "var(--radius-md)", border: "1px solid var(--border-subtle)",
                background: "var(--surface-sunken)",
              }}>
                <BoxSpine spineMm={format.spineMm} heightMm={format.heightMm} scale={0.16} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)" }}>
                    Place sur les tablettes
                  </div>
                  <div style={{ fontSize: 22, fontWeight: 700, lineHeight: 1.2 }}>
                    {lotLengthMm >= 1000 ? ml(lotLengthMm) : cm(lotLengthMm)}
                  </div>
                  <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                    {size} × {spineLabel(format.spineMm)}, sans compter la marge de manipulation
                  </div>
                </div>
              </div>
            )}

            <div style={{ display: "flex", gap: 16, flexWrap: "wrap", fontSize: 13, color: "var(--text-secondary)", borderTop: "1px solid var(--border-subtle)", paddingTop: 12 }}>
              <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                <Icons.box size={16} /> <strong style={{ color: "var(--text-primary)" }}>{size}</strong> boîte{size > 1 ? "s" : ""}
              </span>
              <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                <Icons.print size={16} /> <strong style={{ color: "var(--text-primary)" }}>{pages}</strong> page{pages > 1 ? "s" : ""} A4 (L7160)
              </span>
            </div>
          </Card>
        </div>
      </div>
    </section>
  );
}
