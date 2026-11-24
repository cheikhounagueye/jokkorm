"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  Alert,
  Button,
  Card,
  EmptyState,
  Field,
  PageHeader,
  Textarea,
} from "@/components/ui";
import {
  approveAccessRequest,
  listToApprove,
  rejectAccessRequest,
  type AccessRequestResponse,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons } from "@/lib/icons";
import {
  DetailGrid,
  DetailHeader,
  MasterDetail,
  QueueCard,
  RejectForm,
  requestDetailRows,
  useQueueSelection,
} from "../_components/RequestBits";

export default function ApprouverPage() {
  const query = useQuery({ queryKey: ["toApprove"], queryFn: listToApprove });
  const queue = useQueueSelection(query.data);
  const [done, setDone] = useState<string | null>(null);
  const qc = useQueryClient();

  function onDone(id: string, message: string) {
    queue.markHandled(id);
    setDone(message);
    qc.invalidateQueries({ queryKey: ["toApprove"] });
  }

  return (
    <section className="fade-up">
      <PageHeader
        icon="approve"
        tone="success"
        title="À approuver"
        subtitle="Validation hiérarchique des demandes qualifiées par l'archiviste."
        crumbs={[{ label: "Accueil", href: "/" }, { label: "Demandes", href: "/demandes" }, { label: "Approbation" }]}
      />

      {done && <div style={{ marginBottom: 16 }}><Alert tone="success" title={done} /></div>}

      <MasterDetail
        master={
          <QueueCard title="File d'attente" isLoading={query.isLoading} error={query.error}
            pending={queue.pending} selectedId={queue.selected?.id} onSelect={queue.select}
            emptyTitle="Rien à approuver" />
        }
        detail={
          queue.selected ? (
            <ApprovePanel key={queue.selected.id} r={queue.selected} onDone={onDone} />
          ) : (
            <Card>
              <EmptyState icon={<Icons.approve size={28} />} title="Aucune demande à approuver"
                description="Les demandes qualifiées par l'archiviste apparaîtront ici." />
            </Card>
          )
        }
      />
    </section>
  );
}

function ApprovePanel({ r, onDone }: { r: AccessRequestResponse; onDone: (id: string, message: string) => void }) {
  const [notes, setNotes] = useState("");
  const [rejecting, setRejecting] = useState(false);

  const approveMut = useMutation({
    mutationFn: () => approveAccessRequest(r.id, notes),
    onSuccess: () => onDone(r.id, `Demande de ${r.requesterName} approuvée — prête à exécuter.`),
  });
  const rejectMut = useMutation({
    mutationFn: (reason: string) => rejectAccessRequest(r.id, reason),
    onSuccess: () => onDone(r.id, `Demande de ${r.requesterName} rejetée.`),
  });

  return (
    <Card>
      <DetailHeader r={r} />
      <DetailGrid rows={requestDetailRows(r)} />

      <section style={{
        marginTop: 16, padding: "12px 14px",
        background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)",
        borderLeft: "3px solid var(--warning-500)", borderRadius: "var(--radius-md)",
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, flexWrap: "wrap" }}>
          <Icons.shield size={18} style={{ color: "var(--warning-500)" }} />
          <span>Qualifiée par <code>{r.qualifiedBy}</code></span>
          {r.qualifiedAt && <span style={{ color: "var(--text-tertiary)" }}>le {formatInstant(r.qualifiedAt)}</span>}
          <span style={{ marginLeft: "auto", fontSize: 12, color: "var(--text-tertiary)" }}>
            Contrôle 4 yeux : l’approbateur doit être différent du qualificateur.
          </span>
        </div>
        {r.qualificationNotes && (
          <p style={{ marginTop: 8, fontSize: 13, color: "var(--text-secondary)", fontStyle: "italic" }}>
            « {r.qualificationNotes} »
          </p>
        )}
      </section>

      <div style={{ marginTop: 20 }}>
        <Field label="Notes d'approbation">
          <Textarea value={notes} onChange={(e) => setNotes(e.target.value)}
            placeholder="ex. Accord, retour impératif avant la clôture comptable" />
        </Field>
      </div>

      {approveMut.isError && (
        <div style={{ marginBottom: 12 }}>
          <Alert tone="danger" title="Approbation refusée">{(approveMut.error as Error).message}</Alert>
        </div>
      )}

      <div style={{ display: "flex", gap: 8 }}>
        <Button onClick={() => approveMut.mutate()} loading={approveMut.isPending} icon={<Icons.approve size={16} />}>
          Approuver
        </Button>
        <Button variant="secondary" onClick={() => setRejecting(true)} disabled={rejecting}
          icon={<Icons.error size={16} style={{ color: "var(--danger-500)" }} />}>
          Rejeter
        </Button>
      </div>

      {rejecting && (
        <RejectForm pending={rejectMut.isPending} onSubmit={(reason) => rejectMut.mutate(reason)}
          onCancel={() => setRejecting(false)} />
      )}
      {rejectMut.isError && (
        <div style={{ marginTop: 12 }}><Alert tone="danger">{(rejectMut.error as Error).message}</Alert></div>
      )}
    </Card>
  );
}
