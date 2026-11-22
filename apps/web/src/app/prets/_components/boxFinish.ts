import type { BoxFinish } from "@/components/illustrations/ArchiveBoxIso";
import type { PhysicalContainerResponse } from "@/lib/api";

/** Rendu visuel d'une boîte selon son état et sa position. */
export function boxFinish(c: PhysicalContainerResponse): BoxFinish {
  if (c.state === "DESTROYED") return "destroyed";
  if (c.state === "SEALED") return "sealed";
  if (!c.positionId) return "stale";
  if (c.state === "EMPTY") return "empty";
  return "kraft";
}
