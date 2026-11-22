import type { Tone } from "@/components/ui";
import type { BoxFinish } from "@/components/illustrations/ArchiveBoxIso";
import type {
  ContainerState,
  StorageLocationKind,
  StorageLocationTreeResponse,
} from "@/lib/api";
import type { IconName } from "@/lib/icons";

export const KIND_META: Record<StorageLocationKind, {
  label: string;
  plural: string;
  icon: IconName;
  tone: Tone;
  child: StorageLocationKind | null;
}> = {
  SITE:       { label: "Site",     plural: "sites",     icon: "site",       tone: "brand",   child: "STOREHOUSE" },
  STOREHOUSE: { label: "Magasin",  plural: "magasins",  icon: "storehouse", tone: "kraft",   child: "SHELF" },
  SHELF:      { label: "Travée",   plural: "travées",   icon: "shelf",      tone: "info",    child: "RACK" },
  RACK:       { label: "Tablette", plural: "tablettes", icon: "rack",       tone: "violet",  child: "POSITION" },
  POSITION:   { label: "Position", plural: "positions", icon: "position",   tone: "success", child: null },
};

export const KIND_ORDER: StorageLocationKind[] = ["SITE", "STOREHOUSE", "SHELF", "RACK", "POSITION"];

/** Rendu de la boîte en carton selon son état. */
export const BOX_FINISH: Record<ContainerState, BoxFinish> = {
  EMPTY: "empty",
  OPEN: "kraft",
  SEALED: "sealed",
  DESTROYED: "destroyed",
};

/** Chemin racine → nœud (inclus), ou null si absent. */
export function findPath(
  roots: StorageLocationTreeResponse[],
  id: string,
): StorageLocationTreeResponse[] | null {
  for (const n of roots) {
    if (n.node.id === id) return [n];
    const sub = findPath(n.children, id);
    if (sub) return [n, ...sub];
  }
  return null;
}

export function countByKind(roots: StorageLocationTreeResponse[]): Record<StorageLocationKind, number> {
  const counts: Record<StorageLocationKind, number> = { SITE: 0, STOREHOUSE: 0, SHELF: 0, RACK: 0, POSITION: 0 };
  const walk = (nodes: StorageLocationTreeResponse[]) => {
    for (const n of nodes) {
      counts[n.node.kind] += 1;
      walk(n.children);
    }
  };
  walk(roots);
  return counts;
}

export function etiquetteHref(barcode: string, label: string | null): string {
  return `/magasin/etiquette?barcode=${encodeURIComponent(barcode)}&label=${encodeURIComponent(label ?? "")}`;
}
