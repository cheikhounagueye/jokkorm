import type { IconName } from "@/lib/icons";
import type { Tone } from "@/components/ui";

/**
 * Les gestes du mode magasin (pattern 12). Le nombre de scans fait le geste :
 * 1 scan = arrivée, 2 scans = placer, 3 scans = retirer (le 3e confirme la sortie).
 */
export type Gesture = "identify" | "arrival" | "place" | "remove";

/** Ce que chaque scan attend : une boîte, un emplacement, ou la même boîte pour confirmer. */
export type Expect = "any" | "box" | "position" | "sameBox";

export interface GestureDef {
  id: Gesture;
  label: string;
  icon: IconName;
  tone: Tone;
  steps: Expect[];
  /** Phrase sous le titre, dans le sélecteur. */
  hint: string;
}

export const GESTURES: GestureDef[] = [
  { id: "identify", label: "Identifier", icon: "scan", tone: "slate", steps: ["any"],
    hint: "Qu'est-ce que c'est ?" },
  { id: "arrival", label: "Arrivée", icon: "arrival", tone: "info", steps: ["box"],
    hint: "1 scan : la boîte" },
  { id: "place", label: "Placer", icon: "position", tone: "success", steps: ["box", "position"],
    hint: "2 scans : boîte, emplacement" },
  { id: "remove", label: "Retirer", icon: "takeOff", tone: "warning", steps: ["box", "position", "sameBox"],
    hint: "3 scans : boîte, emplacement, boîte" },
];

export const gestureOf = (id: Gesture) => GESTURES.find((g) => g.id === id)!;

export const INSTRUCTION: Record<Expect, string> = {
  any: "Vise une étiquette de boîte ou d'emplacement",
  box: "Scanne l'étiquette de la boîte",
  position: "Scanne l'étiquette de l'emplacement",
  sameBox: "Rescanne la même boîte pour confirmer",
};

export const STEP_LABEL: Record<Expect, string> = {
  any: "Étiquette",
  box: "Boîte",
  position: "Emplacement",
  sameBox: "Confirmation",
};
