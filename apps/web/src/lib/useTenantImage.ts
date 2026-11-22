"use client";

import { useEffect, useRef, useState } from "react";
import { fetchTenantBlob } from "./api";

/**
 * Charge une image du backend avec l'en-tête tenant et renvoie une URL blob.
 * Garde l'image précédente affichée pendant le rechargement.
 */
export function useTenantImage(url: string): { src: string | null; error: string | null } {
  const [src, setSrc] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const current = useRef<string | null>(null);

  useEffect(() => {
    if (!url) return;
    const ctrl = new AbortController();
    fetchTenantBlob(url, ctrl.signal)
      .then((blob) => {
        const next = URL.createObjectURL(blob);
        const prev = current.current;
        current.current = next;
        setSrc(next);
        setError(null);
        if (prev) URL.revokeObjectURL(prev);
      })
      .catch((e: Error) => {
        if (e.name !== "AbortError") setError(`Rendu de l'image impossible (${e.message})`);
      });
    return () => ctrl.abort();
  }, [url]);

  useEffect(() => () => {
    if (current.current) URL.revokeObjectURL(current.current);
  }, []);

  return { src, error };
}
