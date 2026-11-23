"use client";

import { useState, type ReactNode } from "react";
import {
  ACCESS_STATUS,
  Badge,
  Button,
  Field,
  IconTile,
  REQUEST_TYPE,
  StatusBadge,
  Textarea,
} from "@/components/ui";
import type { AccessRequestResponse } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons, type IconName } from "@/lib/icons";
import { RequestProgress } from "./FourEyes";

/** Ligne d'une demande dans une liste (type, demandeur, motif, à droite un contenu libre). */
export function RequestRow({
  r,
  right,
  selected,
  onClick,
}: {
  r: AccessRequestResponse;
  right?: ReactNode;
  selected?: boolean;
  onClick?: () => void;
}) {
  const meta = REQUEST_TYPE[r.type];
  const content = (
    <>
      <IconTile name={meta.icon} tone={meta.tone} size={36} variant={selected ? "solid" : "soft"} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text-primary)" }}>{r.requesterName}</span>
          {r.requesterDept && <span style={{ fontSize: 12, color: "var(--text-tertiary)" }}>{r.requesterDept}</span>}
        </div>
        <div style={{ fontSize: 12, color: "var(--text-secondary)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
          {r.purpose}
        </div>
        <div style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 2 }}>
          {meta.short} · {formatInstant(r.createdAt)}
        </div>
      </div>
      {right}
    </>
  );

  const style: React.CSSProperties = {
    display: "flex", alignItems: "center", gap: 12, width: "100%",
    padding: "12px 16px", textAlign: "left",
    background: selected ? "var(--brand-50)" : "transparent",
    border: "none",
    borderLeft: `3px solid ${selected ? "var(--brand-500)" : "transparent"}`,
    borderBottom: "1px solid var(--border-subtle)",
    transition: "background 150ms ease",
  };

  if (!onClick) return <div style={style}>{content}</div>;
  return (
    <button type="button" onClick={onClick} style={style}
      onMouseEnter={(e) => { if (!selected) e.currentTarget.style.background = "var(--surface-hover)"; }}
      onMouseLeave={(e) => { if (!selected) e.currentTarget.style.background = "transparent"; }}>
      {content}
    </button>
  );
}

/** En-tête de carte de file : tuile, titre, compteur, action. */
export function QueueHeader({
  icon,
  title,
  count,
  action,
}: {
  icon: IconName;
  title: string;
  count?: number;
  action?: ReactNode;
}) {
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 10,
      padding: "14px 16px", borderBottom: "1px solid var(--border-subtle)",
    }}>
      <IconTile name={icon} tone="slate" size={28} variant="soft" />
      <h3 style={{ flex: 1 }}>{title}</h3>
      {count !== undefined && <Badge tone={count > 0 ? "brand" : "neutral"}>{count}</Badge>}
      {action}
    </div>
  );
}

/** En-tête du panneau de détail : type, demandeur, service, statut. */
export function DetailHeader({ r }: { r: AccessRequestResponse }) {
  const meta = REQUEST_TYPE[r.type];
  return (
    <div style={{ marginBottom: 18 }}>
    <div style={{ display: "flex", alignItems: "center", gap: 14, marginBottom: 14 }}>
      <IconTile name={meta.icon} tone={meta.tone} size={48} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 12, color: "var(--text-tertiary)", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em" }}>
          {meta.label}
        </div>
        <h2 style={{ fontSize: 18 }}>{r.requesterName}</h2>
        <div style={{ fontSize: 13, color: "var(--text-secondary)" }}>{r.requesterDept ?? "Service non précisé"}</div>
      </div>
      <StatusBadge meta={ACCESS_STATUS[r.status]} />
    </div>
    <div style={{
      display: "flex", justifyContent: "center", padding: "12px 8px 10px",
      background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)",
    }}>
      <RequestProgress r={r} labels />
    </div>
    </div>
  );
}

/** Grille libellé / valeur pour le détail d'une demande. */
export function DetailGrid({ rows }: { rows: { label: string; value: ReactNode; icon: IconName }[] }) {
  return (
    <dl style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, margin: 0 }}>
      {rows.map((row) => {
        const RowIcon = Icons[row.icon];
        return (
          <div key={row.label} style={{
            padding: "10px 12px", background: "var(--surface-sunken)",
            border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)",
          }}>
            <dt style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--text-tertiary)", marginBottom: 4 }}>
              <RowIcon size={14} /> {row.label}
            </dt>
            <dd style={{ margin: 0, fontSize: 13, color: "var(--text-primary)", whiteSpace: "pre-wrap" }}>{row.value}</dd>
          </div>
        );
      })}
    </dl>
  );
}

/** Lignes communes de détail d'une demande. */
export function requestDetailRows(r: AccessRequestResponse) {
  const rows: { label: string; value: ReactNode; icon: IconName }[] = [
    { label: "Motif", value: r.purpose, icon: "clipboard" },
    { label: "Créée le", value: formatInstant(r.createdAt), icon: "calendar" },
  ];
  if (r.piecesDescription) rows.push({ label: "Pièces demandées", value: r.piecesDescription, icon: "documents" });
  if (r.expectedReturnAt) rows.push({ label: "Retour prévu", value: formatInstant(r.expectedReturnAt), icon: "returned" });
  return rows;
}

/** Formulaire de rejet inline (remplace prompt()). */
export function RejectForm({
  pending,
  onSubmit,
  onCancel,
}: {
  pending: boolean;
  onSubmit: (reason: string) => void;
  onCancel: () => void;
}) {
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        const reason = String(new FormData(e.currentTarget).get("reason") ?? "").trim();
        if (reason) onSubmit(reason);
      }}
      style={{
        marginTop: 16, padding: 16,
        background: "var(--danger-50)",
        border: "1px solid color-mix(in srgb, var(--danger-500) 30%, transparent)",
        borderRadius: "var(--radius-md)",
      }}>
      <Field label="Motif du rejet" required hint="Communiqué au demandeur.">
        <Textarea name="reason" required autoFocus placeholder="ex. Boîte en cours de récolement, demande à reformuler…" />
      </Field>
      <div style={{ display: "flex", gap: 8 }}>
        <Button type="submit" variant="danger" loading={pending} icon={<Icons.error size={16} />}>
          Confirmer le rejet
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>Annuler</Button>
      </div>
    </form>
  );
}

/**
 * Sélection dans une file : la première demande non traitée est sélectionnée d'office,
 * et une demande traitée disparaît aussitôt de la file (sans attendre le rechargement).
 */
export function useQueueSelection(items: AccessRequestResponse[] | undefined) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [handled, setHandled] = useState<ReadonlySet<string>>(new Set());
  const pending = (items ?? []).filter((r) => !handled.has(r.id));
  const selected = pending.find((r) => r.id === selectedId) ?? pending[0] ?? null;
  return {
    pending,
    selected,
    select: (r: AccessRequestResponse) => setSelectedId(r.id),
    markHandled: (id: string) => setHandled((prev) => new Set(prev).add(id)),
  };
}

/** Carte « file d'attente » du panneau maître. */
export function QueueCard({
  title,
  isLoading,
  error,
  pending,
  selectedId,
  onSelect,
  emptyTitle,
}: {
  title: string;
  isLoading: boolean;
  error: unknown;
  pending: AccessRequestResponse[];
  selectedId?: string;
  onSelect: (r: AccessRequestResponse) => void;
  emptyTitle: string;
}) {
  return (
    <div style={{
      background: "var(--surface-card)", border: "1px solid var(--border-strong)",
      borderRadius: "var(--radius-lg)", boxShadow: "var(--shadow-md)", overflow: "hidden",
    }}>
      <QueueHeader icon="inbox" title={title} count={isLoading ? undefined : pending.length} />
      {isLoading && <div style={{ padding: 20, fontSize: 13, color: "var(--text-tertiary)" }}>Chargement…</div>}
      {!!error && (
        <div style={{ padding: 16, fontSize: 13, color: "var(--danger-500)" }}>{(error as Error).message}</div>
      )}
      {!isLoading && !error && pending.length === 0 && (
        <div style={{ padding: "28px 16px", textAlign: "center" }}>
          <IconTile name="success" tone="success" size={40} variant="soft" />
          <div style={{ fontSize: 13, fontWeight: 600, marginTop: 10 }}>{emptyTitle}</div>
          <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>La file est vide.</div>
        </div>
      )}
      {pending.map((r) => (
        <RequestRow key={r.id} r={r} selected={selectedId === r.id} onClick={() => onSelect(r)} />
      ))}
    </div>
  );
}

/** Mise en page maître-détail des files de traitement. */
export function MasterDetail({ master, detail }: { master: ReactNode; detail: ReactNode }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "minmax(300px, 380px) minmax(0, 1fr)", gap: 16, alignItems: "start" }}>
      <div style={{ position: "sticky", top: 72 }}>{master}</div>
      <div>{detail}</div>
    </div>
  );
}
