"use client";

import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Alert, Button, Field, Input } from "@/components/ui";
import {
  createStorageLocation,
  type StorageLocationKind,
  type StorageLocationResponse,
} from "@/lib/api";
import { Icons } from "@/lib/icons";
import { KIND_META } from "./kinds";

/** Création d'un site (parent=null) ou d'un enfant du nœud sélectionné. */
export function CreateChildForm({
  parent,
  onCreated,
  compact,
  onCancel,
}: {
  parent: StorageLocationResponse | null;
  onCreated: () => void;
  compact?: boolean;
  onCancel?: () => void;
}) {
  const kind: StorageLocationKind = parent ? KIND_META[parent.kind].child! : "SITE";
  const meta = KIND_META[kind];
  const [code, setCode] = useState("");
  const [label, setLabel] = useState("");

  const mutation = useMutation({
    mutationFn: () => createStorageLocation({
      kind, code, label: label || undefined, parentId: parent?.id ?? null,
    }),
    onSuccess: () => { setCode(""); setLabel(""); onCreated(); },
  });

  if (compact) {
    return (
      <form onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}>
        <div style={{ fontSize: 11, fontWeight: 600, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 6 }}>
          Nouveau site
        </div>
        <div style={{ display: "flex", gap: 6 }}>
          <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Code"
            required aria-label="Code du site" style={{ flex: "0 0 96px", padding: "6px 10px", fontSize: 13 }} />
          <Input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Libellé"
            aria-label="Libellé du site" style={{ flex: 1, minWidth: 0, padding: "6px 10px", fontSize: 13 }} />
          <Button type="submit" size="sm" icon={<Icons.add size={14} weight="bold" />}
            loading={mutation.isPending} disabled={!code} aria-label="Créer le site" />
        </div>
        {mutation.isError && <div style={{ marginTop: 8 }}><Alert tone="danger">{(mutation.error as Error).message}</Alert></div>}
      </form>
    );
  }

  return (
    <form onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}>
      <div style={{ display: "grid", gridTemplateColumns: "180px 1fr", gap: 12 }}>
        <Field label={`Code ${meta.label.toLowerCase()}`} required hint="Lettres, chiffres, - et _">
          <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="ex. A, 01, MAG-02" required autoFocus />
        </Field>
        <Field label="Libellé" hint="Optionnel">
          <Input value={label} onChange={(e) => setLabel(e.target.value)} placeholder={`Nom lisible du/de la ${meta.label.toLowerCase()}`} />
        </Field>
      </div>
      <div style={{ display: "flex", gap: 8 }}>
        <Button type="submit" icon={<Icons.add size={14} weight="bold" />} loading={mutation.isPending} disabled={!code}>
          Créer {meta.label.toLowerCase()}
        </Button>
        {onCancel && <Button type="button" variant="ghost" onClick={onCancel}>Annuler</Button>}
      </div>
      {mutation.isError && <div style={{ marginTop: 12 }}><Alert tone="danger">{(mutation.error as Error).message}</Alert></div>}
    </form>
  );
}
