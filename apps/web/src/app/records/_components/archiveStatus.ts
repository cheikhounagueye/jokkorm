import type { PhysicalArchiveStatus } from "@/lib/api";
import type { IconName } from "@/lib/icons";

type BadgeTone = "neutral" | "brand" | "success" | "warning" | "danger" | "info";

/** Statuts du cycle de vie d'une enveloppe physique (seule map locale autorisée). */
export const ARCHIVE_STATUS: Record<PhysicalArchiveStatus, { label: string; tone: BadgeTone; icon: IconName }> = {
  REGISTERED:  { label: "Enregistrée",  tone: "info",    icon: "record" },
  STORED:      { label: "En magasin",   tone: "success", icon: "storehouse" },
  FROZEN:      { label: "Gelée",        tone: "warning", icon: "locked" },
  DESTROYED:   { label: "Détruite",     tone: "danger",  icon: "destroy" },
  TRANSFERRED: { label: "Versée (AN)",  tone: "brand",   icon: "archives" },
};

export const ARCHIVE_STATUSES = Object.keys(ARCHIVE_STATUS) as PhysicalArchiveStatus[];
