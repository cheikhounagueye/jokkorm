"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Button,
  Card,
  EmptyState,
  Field,
  Input,
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
    text: "Sors la boîte, numérise les pièces demandées et remets-la en place. Dépose le scan dans Maarch RM, puis indique ci-dessous sa référence : JokkoRM ne garde jamais le fichier, seulement de quoi le retrouver dans Maarch RM.",
  },
};

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
  const [masterReference, setMasterReference] = useState("");
  const [masterUrl, setMasterUrl] = useState("");
  const isDigitize = r.type === "DIGITIZE_EXTRACT";

  const fulfillMut = useMutation({
    mutationFn: async () => fulfillAccessRequest(r.id, notes || undefined,
      isDigitize ? masterReference.trim() : undefined,
      isDigitize && masterUrl.trim() ? masterUrl.trim() : undefined),
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

      {isDigitize && (
        <div style={{ marginTop: 16, display: "grid", gap: 12 }}>
          <Field label="Référence du scan dans Maarch RM" required
            hint="N° d'archive ou cote de l'extrait, tel qu'affiché par Maarch RM.">
            <Input value={masterReference} onChange={(e) => setMasterReference(e.target.value)}
              maxLength={200} placeholder="ex. FR094080_2026_AE0002_001" autoComplete="off" />
          </Field>
          <Field label="Lien vers le scan dans Maarch RM"
            hint="Facultatif. Adresse copiée depuis Maarch RM ; le demandeur l'ouvrira avec ses propres droits Maarch.">
            <Input type="url" value={masterUrl} onChange={(e) => setMasterUrl(e.target.value)}
              maxLength={2000} placeholder="https://…" autoComplete="off" />
          </Field>
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
        disabled={isDigitize && !masterReference.trim()} icon={<Icons.fulfill size={16} />}>
        {r.type === "BORROW_BOX" ? "Exécuter et télécharger le fantôme" : "Exécuter"}
      </Button>
    </Card>
  );
}
