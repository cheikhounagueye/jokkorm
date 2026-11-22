"use client";

import type {
  AccessRequestStatus,
  BatchStatus,
  ContainerState,
  LoanStatus,
  RequestType,
} from "@/lib/api";
import { Icons, type IconName } from "@/lib/icons";
import { Badge } from "./Badge";
import type { Tone } from "./IconTile";

type BadgeTone = "neutral" | "brand" | "success" | "warning" | "danger" | "info";
type Meta = { label: string; tone: BadgeTone; icon: IconName };

/** Source unique des libellés/couleurs de statut — ne pas redéfinir dans les pages. */
export const ACCESS_STATUS: Record<AccessRequestStatus, Meta> = {
  DRAFT:     { label: "Brouillon", tone: "neutral", icon: "edit" },
  SUBMITTED: { label: "Soumise",   tone: "info",    icon: "inbox" },
  QUALIFIED: { label: "Qualifiée", tone: "warning", icon: "qualify" },
  APPROVED:  { label: "Approuvée", tone: "success", icon: "approve" },
  FULFILLED: { label: "Exécutée",  tone: "brand",   icon: "fulfill" },
  COMPLETED: { label: "Clôturée",  tone: "neutral", icon: "success" },
  REJECTED:  { label: "Rejetée",   tone: "danger",  icon: "error" },
  CANCELLED: { label: "Annulée",   tone: "neutral", icon: "blocked" },
};

export const CONTAINER_STATE: Record<ContainerState, Meta> = {
  EMPTY:     { label: "Vide",     tone: "neutral", icon: "box" },
  OPEN:      { label: "Active",   tone: "success", icon: "box" },
  SEALED:    { label: "Scellée",  tone: "warning", icon: "locked" },
  DESTROYED: { label: "Détruite", tone: "danger",  icon: "destroy" },
};

export const BATCH_STATUS: Record<BatchStatus, Meta> = {
  CREATED:        { label: "Créé",     tone: "info",    icon: "add" },
  PRINTED:        { label: "Imprimé",  tone: "warning", icon: "print" },
  FULLY_DEPLOYED: { label: "Déployé",  tone: "success", icon: "success" },
  ARCHIVED:       { label: "Archivé",  tone: "neutral", icon: "archives" },
};

export const LOAN_STATUS: Record<LoanStatus, Meta> = {
  REQUESTED: { label: "Demandé",   tone: "info",    icon: "pending" },
  OUT:       { label: "Sorti",     tone: "brand",   icon: "loans" },
  OVERDUE:   { label: "En retard", tone: "danger",  icon: "alert" },
  RETURNED:  { label: "Retourné",  tone: "success", icon: "returned" },
  CANCELLED: { label: "Annulé",    tone: "neutral", icon: "blocked" },
};

export const REQUEST_TYPE: Record<RequestType, { label: string; short: string; icon: IconName; tone: Tone }> = {
  BORROW_BOX:       { label: "Prêt de la boîte entière", short: "Prêt boîte",   icon: "borrow",   tone: "brand" },
  EXTRACT_PIECES:   { label: "Extraction de pièces",     short: "Extraction",   icon: "extract",  tone: "info" },
  DIGITIZE_EXTRACT: { label: "Numérisation d'extraits",  short: "Numérisation", icon: "digitize", tone: "violet" },
};

export function StatusBadge({ meta, size }: { meta: Meta; size?: "sm" | "md" }) {
  const IconCmp = Icons[meta.icon];
  return (
    <Badge tone={meta.tone} size={size} icon={<IconCmp size={size === "sm" ? 11 : 12} weight="bold" />}>
      {meta.label}
    </Badge>
  );
}
