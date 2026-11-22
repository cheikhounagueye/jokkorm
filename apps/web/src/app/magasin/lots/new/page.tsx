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
import { createBatch } from "@/lib/api";
import { Icons } from "@/lib/icons";
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

  const mutation = useMutation({
    mutationFn: () => createBatch({
      reference,
      size,
      labelPattern: labelPattern || undefined,
      defaultCapacity: capacity ? parseInt(capacity, 10) : undefined,
    }),
    onSuccess: (r) => router.push(`/magasin/lots/${r.batch.id}`),
  });

  const digits = Math.max(3, String(size).length);
  const seq = `${"1".padStart(digits, "0")}/${String(size).padStart(digits, "0")}`;
  const pages = Math.ceil(size / AVERY_TEMPLATES.AVERY_L7160.perPage);

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

            <div style={{ display: "flex", gap: 16, fontSize: 13, color: "var(--text-secondary)", borderTop: "1px solid var(--border-subtle)", paddingTop: 12 }}>
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
