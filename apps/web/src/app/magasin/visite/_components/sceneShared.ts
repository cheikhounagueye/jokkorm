/** Valeurs partagées entre la scène 3D (chargée à la demande) et le panneau latéral. */
import type { BoxFinish } from "@/components/illustrations/ArchiveBoxIso";
import type { EquipmentKind, RoomBox } from "@/lib/api";
import type { IconName } from "@/lib/icons";
import type { LayoutBox, LayoutEquipment, LayoutSlot } from "./roomLayout";

export type ColorMode = "real" | "occupancy";

export type ScenePick =
  | { kind: "box"; item: LayoutBox }
  | { kind: "slot"; item: LayoutSlot }
  | { kind: "equipment"; item: LayoutEquipment };

export const EQUIPMENT_NAME: Record<EquipmentKind, string> = {
  FIRE_EXTINGUISHER: "Extincteur",
  SMOKE_DETECTOR: "Détecteur de fumée",
  EMERGENCY_EXIT: "Issue",
  THERMO_HYGROMETER: "Thermo-hygromètre",
};

export const EQUIPMENT_ICON: Record<EquipmentKind, IconName> = {
  FIRE_EXTINGUISHER: "extinguisher",
  SMOKE_DETECTOR: "detector",
  EMERGENCY_EXIT: "exit",
  THERMO_HYGROMETER: "hygrometer",
};

export function boxFinish(b: RoomBox): BoxFinish {
  if (b.onLoan) return "loaned";
  switch (b.state) {
    case "OPEN": return "kraft";
    case "SEALED": return "sealed";
    case "DESTROYED": return "destroyed";
    default: return "empty";
  }
}
