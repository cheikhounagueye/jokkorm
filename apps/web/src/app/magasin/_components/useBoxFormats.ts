"use client";

import { useQuery } from "@tanstack/react-query";
import { listBoxFormats, type BoxFormatResponse } from "@/lib/api";

/** Dos utilisé si le catalogue n'est pas encore chargé (même repli que le serveur). */
export const FALLBACK_SPINE_MM = 100;

/** Catalogue de formats du tenant, avec le dos de chaque boîte (pattern 10). */
export function useBoxFormats() {
  const query = useQuery({ queryKey: ["box-formats"], queryFn: listBoxFormats, staleTime: 60_000 });
  const formats = query.data ?? [];
  const byId = new Map(formats.map((f) => [f.id, f]));
  const defaultFormat = formats.find((f) => f.isDefault) ?? null;

  const formatOf = (boxFormatId: string | null | undefined): BoxFormatResponse | null =>
    (boxFormatId ? byId.get(boxFormatId) : undefined) ?? defaultFormat;

  const spineOf = (boxFormatId: string | null | undefined): number =>
    formatOf(boxFormatId)?.spineMm ?? FALLBACK_SPINE_MM;

  /** Combien de boîtes de chaque format actif tiennent dans une longueur libre. */
  const fitsIn = (freeMm: number) =>
    formats.filter((f) => f.active).map((f) => ({
      formatId: f.id, code: f.code, label: f.label, spineMm: f.spineMm,
      count: Math.max(0, Math.floor(freeMm / f.spineMm)),
    }));

  return { query, formats, active: formats.filter((f) => f.active), defaultFormat, formatOf, spineOf, fitsIn };
}

/** « dos 10 », « dos 7,5 » */
export function spineLabel(spineMm: number): string {
  const v = spineMm / 10;
  return `dos ${Number.isInteger(v) ? v : v.toFixed(1).replace(".", ",")}`;
}
