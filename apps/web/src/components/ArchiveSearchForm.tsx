"use client";

import { useEffect, useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  IconTile,
  Input,
  Select,
} from "@/components/ui";
import { getCurrentRole, masterSearch, type MasterSearchResult } from "@/lib/api";
import { Icons } from "@/lib/icons";

const TYPOLOGIES = [
  { value: "", label: "Toutes typologies" },
  { value: "RH", label: "Dossier RH / Contrat RH" },
  { value: "DMG", label: "Marché public / Moyens généraux" },
  { value: "FIN", label: "Dossier financier" },
  { value: "FACT", label: "Facture fournisseur" },
  { value: "EC", label: "Acte d'état civil" },
  { value: "JUR", label: "Dossier juridique" },
  { value: "URBA", label: "Dossier urbanisme" },
];

/**
 * Formulaire de recherche métier (pattern 08).
 * Le demandeur cherche par critères ; le code-barres n'est jamais affiché côté REQUESTER.
 */
export default function ArchiveSearchForm({
  onSelect,
}: {
  onSelect: (result: MasterSearchResult) => void;
}) {
  const [freeText, setFreeText] = useState("");
  const [service, setService] = useState("");
  const [typologie, setTypologie] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [results, setResults] = useState<MasterSearchResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [isArchivist, setIsArchivist] = useState(false);

  useEffect(() => {
    const role = getCurrentRole();
    setIsArchivist(role === "ARCHIVIST" || role === "ARCHIVES_HEAD");
  }, []);

  async function handleSearch(e: React.FormEvent) {
    e.preventDefault();
    if (!freeText && !service && !typologie && !dateFrom && !dateTo) {
      setError("Renseigne au moins un critère de recherche.");
      return;
    }
    setLoading(true); setError(null); setResults(null);
    try {
      const r = await masterSearch({
        freeText: freeText || undefined,
        service: service || undefined,
        typologie: typologie || undefined,
        dateFrom: dateFrom || undefined,
        dateTo: dateTo || undefined,
      });
      setResults(r);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <form onSubmit={handleSearch} style={{
        padding: "18px 18px 2px",
        background: "var(--surface-sunken)",
        border: "1px solid var(--border-subtle)",
        borderRadius: "var(--radius-md)",
      }}>
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 3fr) minmax(0, 2fr)", gap: 12 }}>
          <Field label="Recherche libre">
            <div style={{ position: "relative" }}>
              <Icons.search size={16} style={{ position: "absolute", left: 11, top: "50%", transform: "translateY(-50%)", color: "var(--text-tertiary)" }} />
              <Input value={freeText} onChange={(e) => setFreeText(e.target.value)}
                placeholder="Nom, mots-clés, numéro…" style={{ paddingLeft: 34 }} />
            </div>
          </Field>
          <Field label="Service producteur">
            <Input value={service} onChange={(e) => setService(e.target.value)} placeholder="DRH, Juridique, Finances…" />
          </Field>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1fr) auto", gap: 12, alignItems: "end" }}>
          <Field label="Typologie">
            <Select value={typologie} onChange={(e) => setTypologie(e.target.value)}>
              {TYPOLOGIES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </Select>
          </Field>
          <Field label="Période du">
            <Input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} />
          </Field>
          <Field label="au">
            <Input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} />
          </Field>
          <div style={{ marginBottom: 16 }}>
            <Button type="submit" loading={loading} icon={<Icons.search size={16} weight="bold" />} style={{ height: 38 }}>
              Rechercher
            </Button>
          </div>
        </div>
      </form>

      {error && <div style={{ marginTop: 16 }}><Alert tone="danger" title="Recherche impossible">{error}</Alert></div>}

      {results && (
        <>
          <div style={{ display: "flex", alignItems: "center", gap: 8, margin: "20px 0 10px" }}>
            <h3>Résultats</h3>
            <Badge tone={results.length > 0 ? "brand" : "neutral"}>{results.length}</Badge>
          </div>
          {results.length === 0 ? (
            <EmptyState icon={<Icons.search size={28} />} title="Aucun dossier trouvé"
              description="Élargis la période ou retire un critère." />
          ) : (
            <Card padding={0} style={{ overflow: "hidden" }}>
              <table>
                <thead>
                  <tr><th>Dossier</th><th>Service</th><th>Période</th><th>Disponibilité</th><th /></tr>
                </thead>
                <tbody>
                  {results.map((r) => (
                    <tr key={r.cote}>
                      <td>
                        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                          <IconTile name="record" tone="kraft" size={32} variant="soft" />
                          <div style={{ minWidth: 0 }}>
                            <div style={{ fontWeight: 600 }}>{r.title}</div>
                            <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>
                              {r.classificationPath ?? r.cote}
                              {isArchivist && r.containerBarcode && <> · <code>{r.containerBarcode}</code></>}
                            </div>
                          </div>
                        </div>
                      </td>
                      <td style={{ color: "var(--text-secondary)" }}>{r.service ?? "—"}</td>
                      <td style={{ color: "var(--text-secondary)", whiteSpace: "nowrap" }}>{formatPeriod(r)}</td>
                      <td>
                        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                          {r.availableInPhysical
                            ? <Badge tone="success" icon={<Icons.box size={12} weight="bold" />}>En magasin</Badge>
                            : <Badge tone="neutral" icon={<Icons.document size={12} weight="bold" />}>Numérique seul</Badge>}
                          {r.communicable === false && (
                            <Badge tone="danger" icon={<Icons.locked size={12} weight="bold" />}>Restreint</Badge>
                          )}
                        </div>
                      </td>
                      <td style={{ textAlign: "right" }}>
                        <Button size="sm" variant={r.availableInPhysical ? "primary" : "secondary"}
                          disabled={!r.availableInPhysical}
                          title={!r.availableInPhysical ? "Pas de boîte physique associée" : undefined}
                          onClick={() => onSelect(r)}
                          iconRight={<Icons.next size={12} weight="bold" />}>
                          Demander
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          )}
        </>
      )}
    </div>
  );
}

function formatPeriod(r: MasterSearchResult): string {
  const start = r.dateStart?.substring(0, 4);
  const end = r.dateEnd?.substring(0, 4);
  if (start && end) return start === end ? start : `${start} – ${end}`;
  return start ?? "—";
}
