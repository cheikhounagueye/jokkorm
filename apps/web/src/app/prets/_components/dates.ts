const DAY = 86_400_000;

/** Nombre de jours entiers entre maintenant et l'échéance (négatif = dépassée). */
export function daysUntil(iso: string): number {
  return Math.round((new Date(iso).getTime() - Date.now()) / DAY);
}

export function relativeDue(iso: string): string {
  const d = daysUntil(iso);
  if (d === 0) return "aujourd'hui";
  if (d > 0) return `dans ${d} j`;
  return `retard de ${-d} j`;
}

export function shortDate(iso: string): string {
  return new Date(iso).toLocaleDateString("fr-FR", { day: "2-digit", month: "short", year: "numeric" });
}
