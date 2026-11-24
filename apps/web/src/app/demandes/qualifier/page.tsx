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
  listToQualify,
  qualifyAccessRequest,
  rejectAccessRequest,
  type AccessRequestResponse,
  type QualificationCheck,
} from "@/lib/api";
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

export default function QualifierPage() {
  const query = useQuery({ queryKey: ["toQualify"], queryFn: listToQualify });
  const queue = useQueueSelection(query.data);
  const [done, setDone] = useState<string | null>(null);
  const qc = useQueryClient();

  function onDone(id: string, message: string) {
    queue.markHandled(id);
    setDone(message);
    qc.invalidateQueries({ queryKey: ["toQualify"] });
  }

  return (
    <section className="fade-up">
      <PageHeader
        icon="qualify"
        tone="warning"
        title="À qualifier"
        subtitle="Vérifie la disponibilité de la boîte avant de transmettre au responsable des archives."
        crumbs={[{ label: "Accueil", href: "/" }, { label: "Demandes", href: "/demandes" }, { label: "Qualification" }]}
      />

      {done && <div style={{ marginBottom: 16 }}><Alert tone="success" title={done} /></div>}

      <MasterDetail
        master={
          <QueueCard title="File d'attente" isLoading={query.isLoading} error={query.error}
            pending={queue.pending} selectedId={queue.selected?.id} onSelect={queue.select}
            emptyTitle="Rien à qualifier" />
        }
        detail={
          queue.selected ? (
            <QualifyPanel key={queue.selected.id} r={queue.selected} onDone={onDone} />
          ) : (
            <Card>
              <EmptyState icon={<Icons.qualify size={28} />} title="Aucune demande à qualifier"
                description="Les nouvelles demandes soumises apparaîtront dans la file." />
            </Card>
          )
        }
      />
    </section>
  );
}

function QualifyPanel({ r, onDone }: { r: AccessRequestResponse; onDone: (id: string, message: string) => void }) {
  const [notes, setNotes] = useState("");
  const [checks, setChecks] = useState<QualificationCheck[] | null>(null);
  const [rejecting, setRejecting] = useState(false);

  const qualifyMut = useMutation({
    mutationFn: () => qualifyAccessRequest(r.id, notes),
    onSuccess: (res) => {
      setChecks(res.checks);
      if (res.qualified) onDone(r.id, `Demande de ${r.requesterName} qualifiée — transmise au responsable des archives.`);
    },
  });

  const rejectMut = useMutation({
    mutationFn: (reason: string) => rejectAccessRequest(r.id, reason),
    onSuccess: () => onDone(r.id, `Demande de ${r.requesterName} rejetée.`),
  });

  const blocked = checks?.some((c) => !c.passed && c.severity === "BLOCKING");

  return (
    <Card>
      <DetailHeader r={r} />
      <DetailGrid rows={requestDetailRows(r)} />

      {checks && (
        <section style={{ marginTop: 20 }}>
          <h3 style={{ marginBottom: 10 }}>Contrôles de disponibilité</h3>
          {blocked && (
            <div style={{ marginBottom: 10 }}>
              <Alert tone="danger" title="Au moins un contrôle bloquant">
                La demande ne peut pas être qualifiée en l’état. Rejette-la avec un motif.
              </Alert>
            </div>
          )}
          <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "grid", gap: 6 }}>
            {checks.map((c, i) => <CheckItem key={i} c={c} />)}
          </ul>
        </section>
      )}

      <div style={{ marginTop: 20 }}>
        <Field label="Notes de qualification" hint="Visibles par le responsable des archives.">
          <Textarea value={notes} onChange={(e) => setNotes(e.target.value)}
            placeholder="ex. Boîte vérifiée en magasin, OK pour validation hiérarchique" />
        </Field>
      </div>

      {qualifyMut.isError && (
        <div style={{ marginBottom: 12 }}><Alert tone="danger">{(qualifyMut.error as Error).message}</Alert></div>
      )}

      <div style={{ display: "flex", gap: 8 }}>
        <Button onClick={() => qualifyMut.mutate()} loading={qualifyMut.isPending} icon={<Icons.qualify size={16} />}>
          Contrôler et qualifier
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

/** Contrôles de qualification en clair (le code technique reste au survol). */
const CHECK_LABEL: Record<string, string> = {
  CONTAINER_EXISTS: "Boîte trouvée",
  NOT_DESTROYED: "Boîte non détruite",
  NO_ACTIVE_LOAN: "Pas de prêt en cours",
  NO_ACTIVE_REQUEST: "Pas d'autre prêt approuvé",
  NO_LEGAL_HOLD: "Aucun gel contentieux",
  BUFFER_ZONE: "Boîte rangée en magasin",
  DUA_STATUS: "Durée de conservation",
  COMMUNICABILITY: "Communicabilité",
};

function CheckItem({ c }: { c: QualificationCheck }) {
  const kind = c.passed ? "ok" : c.severity === "BLOCKING" ? "blocked" : c.severity === "WARNING" ? "warning" : "info";
  const style = {
    ok:      { Icon: Icons.success, fg: "var(--success-500)", bg: "var(--success-50)" },
    blocked: { Icon: Icons.blocked, fg: "var(--danger-500)",  bg: "var(--danger-50)" },
    warning: { Icon: Icons.warning, fg: "var(--warning-500)", bg: "var(--warning-50)" },
    info:    { Icon: Icons.info,    fg: "var(--info-500)",    bg: "var(--info-50)" },
  }[kind];
  const ItemIcon = style.Icon;
  return (
    <li style={{
      display: "flex", alignItems: "center", gap: 10,
      padding: "8px 12px", borderRadius: "var(--radius-md)",
      background: style.bg, border: `1px solid color-mix(in srgb, ${style.fg} 25%, transparent)`,
      fontSize: 13,
    }}>
      <ItemIcon size={18} weight="fill" style={{ color: style.fg, flexShrink: 0 }} />
      <span title={c.code} style={{ fontWeight: 600, color: "var(--text-primary)", whiteSpace: "nowrap" }}>{CHECK_LABEL[c.code] ?? c.code}</span>
      {c.message && c.message !== "OK" && <span style={{ color: "var(--text-secondary)" }}>{c.message}</span>}
    </li>
  );
}
