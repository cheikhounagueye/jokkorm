import type { PhysicalArchiveStatus } from "./api";

export function formatInstant(iso: string): string {
  try {
    const date = new Date(iso);
    return date.toLocaleString("fr-FR", {
      year: "numeric", month: "2-digit", day: "2-digit",
      hour: "2-digit", minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

const STATUS_LABELS: Record<PhysicalArchiveStatus, string> = {
  REGISTERED: "Enregistrée",
  STORED: "En magasin",
  FROZEN: "Gelée (contentieux)",
  DESTROYED: "Détruite",
  TRANSFERRED: "Versée (AN)",
};

const STATUS_COLORS: Record<PhysicalArchiveStatus, string> = {
  REGISTERED: "#5b7cb3",
  STORED: "#2e7d32",
  FROZEN: "#e65100",
  DESTROYED: "#b71c1c",
  TRANSFERRED: "#4a148c",
};

export function statusLabel(status: PhysicalArchiveStatus): string {
  return STATUS_LABELS[status] ?? status;
}

export function statusColor(status: PhysicalArchiveStatus): string {
  return STATUS_COLORS[status] ?? "#666";
}
