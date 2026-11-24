import type { PhysicalArchiveStatus } from "@/lib/api";
import type { IconName } from "@/lib/icons";

type BadgeTone = "neutral" | "brand" | "success" | "warning" | "danger" | "info";

/** Statuts du cycle de vie d'une enveloppe physique (seule map locale autorisée). */
export const ARCHIVE_STATUS: Record<PhysicalArchiveStatus, { label: string; tone: BadgeTone; icon: IconName }> = {
  REGISTERED:  { label: "Enregistrée",  tone: "info",    icon: "record" },
  // En boîte, pas forcément en rayon : la boîte peut attendre en zone tampon (D1). L'adresse se lit sur la boîte.
  STORED:      { label: "En boîte",     tone: "success", icon: "box" },
  FROZEN:      { label: "Gelée",        tone: "warning", icon: "locked" },
  DESTROYED:   { label: "Détruite",     tone: "danger",  icon: "destroy" },
  TRANSFERRED: { label: "Versée (AN)",  tone: "brand",   icon: "archives" },
};

export const ARCHIVE_STATUSES = Object.keys(ARCHIVE_STATUS) as PhysicalArchiveStatus[];
