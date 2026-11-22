"use client";

import { forwardRef, useState } from "react";
import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import { Alert, Button, Card, CONTAINER_STATE, IconTile, StatusBadge } from "@/components/ui";
import { findContainerByBarcode, type PhysicalContainerResponse } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { BOX_FINISH, etiquetteHref } from "./kinds";
import { LinkButton } from "./LinkButton";

/** Lookup douchette : la plupart des lecteurs envoient Entrée en fin de lecture. */
export const BarcodeScanner = forwardRef<HTMLDivElement, { onLocate: (positionId: string) => void }>(
  function BarcodeScanner({ onLocate }, ref) {
    const [scanned, setScanned] = useState("");
    const [result, setResult] = useState<PhysicalContainerResponse | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    const scan = async (barcode: string) => {
      setResult(null); setError(null); setLoading(true);
      try {
        setResult(await findContainerByBarcode(barcode));
      } catch (e) {
        setError((e as Error).message);
      } finally {
        setLoading(false);
      }
    };

    return (
      <div ref={ref} style={{ height: "100%" }}>
        <Card style={{ height: "100%" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 16 }}>
            <IconTile name="scan" tone="violet" size={40} />
            <div>
              <h2>Scanner une boîte</h2>
              <p style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                Place le curseur dans le champ et lis l&apos;étiquette à la douchette.
              </p>
            </div>
          </div>

          <form onSubmit={(e) => { e.preventDefault(); scan(scanned); }} style={{ display: "flex", gap: 8 }}>
            <div style={{
              flex: 1, display: "flex", alignItems: "center", gap: 8,
              padding: "0 12px",
              border: "1px solid var(--border-strong)", borderRadius: "var(--radius-md)",
              background: "var(--surface-card)",
            }}>
              <Icons.barcode size={20} style={{ color: "var(--text-tertiary)", flexShrink: 0 }} />
              <input
                value={scanned}
                onChange={(e) => setScanned(e.target.value)}
                placeholder="BOX-261122-A3F2K9"
                aria-label="Code-barres scanné"
                style={{
                  flex: 1, minWidth: 0, border: "none", boxShadow: "none", padding: "10px 0",
                  fontFamily: "var(--font-mono)", fontSize: 15, letterSpacing: "0.06em", background: "transparent",
                }}
              />
            </div>
            <Button type="submit" icon={<Icons.search size={14} />} loading={loading} disabled={!scanned}>
              Chercher
            </Button>
          </form>

          {error && <div style={{ marginTop: 12 }}><Alert tone="danger" title="Boîte introuvable">{error}</Alert></div>}

          {result && (
            <div style={{
              marginTop: 14, padding: 14,
              background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)",
              borderRadius: "var(--radius-md)",
              display: "flex", gap: 14, alignItems: "center", flexWrap: "wrap",
            }}>
              <ArchiveBoxIso size={72} finish={BOX_FINISH[result.state]} />
              <div style={{ flex: 1, minWidth: 180 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                  <code style={{ fontSize: 14, fontWeight: 700 }}>{result.barcode}</code>
                  <StatusBadge meta={CONTAINER_STATE[result.state]} size="sm" />
                </div>
                {result.label && <div style={{ fontSize: 13, marginTop: 4 }}>{result.label}</div>}
                <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
                  {result.capacity != null && <>Capacité {result.capacity} · </>}
                  {result.positionId
                    ? "Rangée dans la topographie"
                    : <span style={{ color: "var(--warning-700)", fontWeight: 600 }}>En zone tampon (pas encore rangée)</span>}
                </div>
                <div style={{ display: "flex", gap: 6, marginTop: 10, flexWrap: "wrap" }}>
                  {result.positionId && (
                    <Button size="sm" icon={<Icons.position size={13} />} onClick={() => onLocate(result.positionId!)}>
                      Voir sur la tablette
                    </Button>
                  )}
                  <LinkButton href={etiquetteHref(result.barcode, result.label)} newTab icon={<Icons.print size={13} />}>
                    Réimprimer l&apos;étiquette
                  </LinkButton>
                </div>
              </div>
            </div>
          )}
        </Card>
      </div>
    );
  },
);
