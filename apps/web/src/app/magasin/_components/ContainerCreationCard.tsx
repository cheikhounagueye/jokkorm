"use client";

import { CopySimple } from "@phosphor-icons/react";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import { Alert, Button, Card, Field, IconTile, Input } from "@/components/ui";
import { createContainer, type PhysicalContainerResponse } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { etiquetteHref } from "./kinds";
import { LinkButton } from "./LinkButton";

export function ContainerCreationCard() {
  const [label, setLabel] = useState("");
  const [capacity, setCapacity] = useState("");
  const [created, setCreated] = useState<PhysicalContainerResponse | null>(null);

  const mutation = useMutation({
    mutationFn: () => createContainer({
      label: label || undefined,
      capacity: capacity ? parseInt(capacity, 10) : undefined,
    }),
    onSuccess: (c) => { setCreated(c); setLabel(""); setCapacity(""); },
  });

  return (
    <Card style={{ height: "100%" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 16 }}>
        <IconTile name="add" tone="kraft" size={40} />
        <div>
          <h2>Nouvelle boîte</h2>
          <p style={{ fontSize: 12, color: "var(--text-secondary)" }}>
            Code-barres généré automatiquement (<code>BOX-AAMMJJ-xxxxxx</code>).
          </p>
        </div>
      </div>

      <form onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}>
        <Field label="Libellé" hint="Unique : deux boîtes ne portent jamais le même libellé.">
          <Input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="ex. Marchés publics 2024" />
        </Field>
        <Field label="Capacité" hint="Nombre maximum de dossiers">
          <Input type="number" min={1} value={capacity} onChange={(e) => setCapacity(e.target.value)} placeholder="ex. 50" />
        </Field>
        <Button type="submit" icon={<Icons.box size={14} />} loading={mutation.isPending}>
          Créer la boîte
        </Button>
      </form>
      {mutation.isError && <div style={{ marginTop: 12 }}><Alert tone="danger">{(mutation.error as Error).message}</Alert></div>}

      {created && (
        <div style={{
          marginTop: 16, padding: 14,
          background: "var(--success-50)",
          border: "1px solid color-mix(in srgb, var(--success-500) 30%, transparent)",
          borderRadius: "var(--radius-md)",
          display: "flex", gap: 14, alignItems: "center", flexWrap: "wrap",
        }}>
          <ArchiveBoxIso size={64} finish="empty" />
          <div style={{ flex: 1, minWidth: 180 }}>
            <div style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 4 }}>Boîte créée — à étiqueter</div>
            <code style={{ fontSize: 17, fontWeight: 700, letterSpacing: "0.04em" }}>{created.barcode}</code>
            <div style={{ display: "flex", gap: 6, marginTop: 10, flexWrap: "wrap" }}>
              <LinkButton href={etiquetteHref(created.barcode, created.label)} newTab variant="primary" icon={<Icons.print size={14} />}>
                Imprimer l&apos;étiquette
              </LinkButton>
              <Button variant="secondary" size="sm" icon={<CopySimple size={13} />}
                onClick={() => navigator.clipboard.writeText(created.barcode)}>
                Copier le code-barres
              </Button>
            </div>
          </div>
        </div>
      )}
    </Card>
  );
}
