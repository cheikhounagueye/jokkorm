"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Button,
  Card,
  EmptyState,
  Field,
  IconTile,
  PageHeader,
  Textarea,
} from "@/components/ui";
import {
  downloadGhostCard,
  fulfillAccessRequest,
  listToFulfill,
  type AccessRequestResponse,
  type RequestType,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons } from "@/lib/icons";
import {
  DetailGrid,
  DetailHeader,
  MasterDetail,
  QueueCard,
  requestDetailRows,
  useQueueSelection,
} from "../_components/RequestBits";

const INSTRUCTIONS: Record<RequestType, { title: string; text: string }> = {
  BORROW_BOX: {
    title: "Sortir la boîte entière",
    text: "Scanne la boîte, sors-la du magasin et remets-la à l'emprunteur. Le fantôme PDF+QR se télécharge automatiquement : imprime-le et glisse-le à la place de la boîte.",
  },
  EXTRACT_PIECES: {
    title: "Extraire les pièces listées",
    text: "Sors temporairement la boîte, extrais les pièces demandées, remets la boîte en place avec un fantôme interne, puis remets les pièces à l'emprunteur.",
  },
  DIGITIZE_EXTRACT: {
    title: "Numériser les pièces listées",
    text: "Sors la boîte, numérise les pièces demandées et remets-la en place. Dépose le PDF ci-dessous : JokkoRM le transmet à Maarch RM et ne garde que le lien.",
  },
};

/** Base64 par blocs (évite le dépassement de pile sur les gros PDF). */
async function fileToBase64(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer());
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

export default function ExecuterPage() {
  const query = useQuery({ queryKey: ["toFulfill"], queryFn: listToFulfill });
  const queue = useQueueSelection(query.data);
  const [done, setDone] = useState<string | null>(null);
  const qc = useQueryClient();

  function onDone(id: string, message: string) {
    queue.markHandled(id);
    setDone(message);
    qc.invalidateQueries({ queryKey: ["toFulfill"] });
  }

  return (
    <section className="fade-up">
      <PageHeader
        icon="fulfill"
        tone="brand"
        title="À exécuter"
        subtitle="Demandes approuvées : sortie de boîte, extraction ou numérisation."
        crumbs={[{ label: "Accueil", href: "/" }, { label: "Demandes", href: "/demandes" }, { label: "Exécution" }]}
      />

      {done && <div style={{ marginBottom: 16 }}><Alert tone="success" title={done} /></div>}

      <MasterDetail
        master={
          <QueueCard title="Demandes approuvées" isLoading={query.isLoading} error={query.error}
            pending={queue.pending} selectedId={queue.selected?.id} onSelect={queue.select}
            emptyTitle="Rien à exécuter" />
        }
        detail={
          queue.selected ? (
            <FulfillPanel key={queue.selected.id} r={queue.selected} onDone={onDone} />
          ) : (
            <Card>
              <EmptyState icon={<Icons.fulfill size={28} />} title="Aucune demande à exécuter"
                description="Les demandes approuvées par le responsable des archives apparaîtront ici." />
            </Card>
          )
        }
      />
    </section>
  );
}

function FulfillPanel({ r, onDone }: { r: AccessRequestResponse; onDone: (id: string, message: string) => void }) {
  const [notes, setNotes] = useState("");
  const [pdfFile, setPdfFile] = useState<File | null>(null);

  const fulfillMut = useMutation({
    mutationFn: async () => {
      let base64: string | undefined;
      if (r.type === "DIGITIZE_EXTRACT") {
        if (!pdfFile) throw new Error("Dépose le PDF numérisé avant d'exécuter.");
        base64 = await fileToBase64(pdfFile);
      }
      return fulfillAccessRequest(r.id, notes || undefined, base64);
    },
    onSuccess: async (res) => {
      if (r.type === "BORROW_BOX" && res.createdLoanId) {
        await downloadGhostCard(res.createdLoanId);
      }
      onDone(r.id, r.type === "BORROW_BOX"
        ? `Prêt de ${r.requesterName} exécuté — fantôme téléchargé, à imprimer.`
        : `Demande de ${r.requesterName} exécutée.`);
    },
  });

  return (
    <Card>
      <DetailHeader r={r} />
      <DetailGrid rows={[
        ...requestDetailRows(r),
        {
          label: "Approuvée par", icon: "approve",
          value: <><code>{r.approvedBy}</code>{r.approvedAt && ` · ${formatInstant(r.approvedAt)}`}</>,
        },
      ]} />

      <div style={{ marginTop: 16 }}>
        <Alert tone="info" title={INSTRUCTIONS[r.type].title}>{INSTRUCTIONS[r.type].text}</Alert>
      </div>

      {r.type === "DIGITIZE_EXTRACT" && (
        <div style={{ marginTop: 16 }}>
          <PdfDropzone file={pdfFile} onChange={setPdfFile} />
        </div>
      )}

      <div style={{ marginTop: 16 }}>
        <Field label="Notes d'exécution">
          <Textarea value={notes} onChange={(e) => setNotes(e.target.value)}
            placeholder="ex. 4 pièces numérisées, boîte remise en place" style={{ minHeight: 64 }} />
        </Field>
      </div>

      {fulfillMut.isError && (
        <div style={{ marginBottom: 12 }}>
          <Alert tone="danger" title="Exécution impossible">{(fulfillMut.error as Error).message}</Alert>
        </div>
      )}

      <Button size="lg" onClick={() => fulfillMut.mutate()} loading={fulfillMut.isPending}
        disabled={r.type === "DIGITIZE_EXTRACT" && !pdfFile} icon={<Icons.fulfill size={16} />}>
        {r.type === "BORROW_BOX" ? "Exécuter et télécharger le fantôme" : "Exécuter"}
      </Button>
    </Card>
  );
}

function PdfDropzone({ file, onChange }: { file: File | null; onChange: (f: File | null) => void }) {
  const [over, setOver] = useState(false);

  if (file) {
    return (
      <div style={{
        display: "flex", alignItems: "center", gap: 12, padding: "12px 14px",
        background: "var(--success-50)", borderRadius: "var(--radius-md)",
        border: "1px solid color-mix(in srgb, var(--success-500) 30%, transparent)",
      }}>
        <IconTile name="pdf" tone="danger" size={40} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 13, fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{file.name}</div>
          <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>{Math.max(1, Math.round(file.size / 1024))} Ko · prêt à transmettre</div>
        </div>
        <Button variant="ghost" size="sm" onClick={() => onChange(null)} icon={<Icons.close size={14} weight="bold" />}>
          Retirer
        </Button>
      </div>
    );
  }

  return (
    <label
      onDragOver={(e) => { e.preventDefault(); setOver(true); }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault(); setOver(false);
        const f = e.dataTransfer.files?.[0];
        if (f && f.type === "application/pdf") onChange(f);
      }}
      style={{
        display: "flex", flexDirection: "column", alignItems: "center", gap: 10,
        padding: "28px 20px", cursor: "pointer", textAlign: "center",
        background: over ? "var(--brand-50)" : "var(--surface-sunken)",
        border: `2px dashed ${over ? "var(--brand-500)" : "var(--border-strong)"}`,
        borderRadius: "var(--radius-lg)",
        transition: "all 150ms ease",
      }}>
      <IconTile name="cloudUpload" tone="violet" size={48} />
      <div style={{ fontSize: 14, fontWeight: 600 }}>Dépose le PDF numérisé ici</div>
      <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>ou clique pour choisir un fichier · PDF uniquement</div>
      <input type="file" accept="application/pdf" style={{ display: "none" }}
        onChange={(e) => onChange(e.target.files?.[0] ?? null)} />
    </label>
  );
}
