"use client";

import { useQuery } from "@tanstack/react-query";
import { getMasterInfo, type MasterKind } from "@/lib/api";

/** Le maître de l'espace courant (change seulement quand on change d'espace, page rechargée). */
export function useMasterInfo() {
  return useQuery({ queryKey: ["master-info"], queryFn: () => getMasterInfo(), staleTime: Infinity, retry: false });
}

/** Ce qu'on dit des données du maître : réelles, fictives, absentes. */
export const MASTER_LOOK: Record<string, { tone: "success" | "warning" | "neutral" | "info"; source: string; dot: string }> = {
  maarchrm: { tone: "success", source: "Lu en direct dans Maarch RM", dot: "var(--success-500)" },
  maarch:   { tone: "info",    source: "Lu dans Maarch Courrier",     dot: "var(--info-500)" },
  demo:     { tone: "warning", source: "Données fictives de démonstration", dot: "var(--warning-500)" },
  noop:     { tone: "neutral", source: "Aucun maître : JokkoRM autonome", dot: "var(--text-tertiary)" },
};

export function masterLook(kind: MasterKind | undefined) {
  return MASTER_LOOK[kind ?? "noop"] ?? MASTER_LOOK.noop;
}
