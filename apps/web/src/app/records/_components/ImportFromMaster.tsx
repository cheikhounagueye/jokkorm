"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Alert, Badge, Button, Card, IconTile, Input } from "@/components/ui";
import { importFromMaster, masterSearch, type ImportFromMasterResponse, type MasterSearchResult } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { masterLook, useMasterInfo } from "@/lib/useMasterInfo";

/** Une cote ressemble à « FR094080_2026_AE0002 » ou « DMG/MP/2024/047 ». */
const looksLikeCote = (q: string) => /^[A-Z0-9/_-]+$/.test(q) && /\d/.test(q);

/**
 * L'archiviste cherche dans la GED maître et fait entrer dans JokkoRM les archives qui ont
 * du papier à suivre. Rien n'est copié automatiquement (patterns 01 et 04).
 */
export function ImportFromMaster() {
  const info = useMasterInfo();
  const qc = useQueryClient();
  const [q, setQ] = useState("");
  const [imported, setImported] = useState<Record<string, ImportFromMasterResponse>>({});

  const search = useMutation({
    mutationFn: () => {
      const text = q.trim();
      return masterSearch(looksLikeCote(text) ? { cote: text, limit: 20 } : { freeText: text, limit: 20 });
    },
  });
  const doImport = useMutation({
    mutationFn: (cote: string) => importFromMaster(cote),
    onSuccess: (r) => {
      setImported((prev) => ({ ...prev, [r.cote]: r }));
      qc.invalidateQueries({ queryKey: ["records"] });
    },
  });

  if (!info.data || info.data.kind === "noop") return null;
  const name = info.data.displayName;
  const look = masterLook(info.data.kind);
  const results = search.data ?? [];

  return (
    <Card style={{ marginBottom: 16 }}>
      <div style={{ display: "flex", gap: 14, alignItems: "flex-start", marginBottom: 14 }}>
        <IconTile name="download" tone="brand" size={44} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <h3 style={{ margin: 0 }}>Importer depuis {name}</h3>
          <div style={{ fontSize: 13, color: "var(--text-secondary)" }}>
            Cherche une archive dans la GED maître, puis importe celles qui ont du papier à ranger.
          </div>
        </div>
        <Badge tone={look.tone}>{look.source}</Badge>
      </div>

      <form onSubmit={(e) => { e.preventDefault(); if (q.trim()) search.mutate(); }} style={{ display: "flex", gap: 8 }}>
        <div style={{ position: "relative", flex: 1 }}>
          <Icons.search size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "var(--text-tertiary)" }} />
          <Input value={q} onChange={(e) => setQ(e.target.value)} style={{ paddingLeft: 36 }}
            placeholder={`Intitulé ou cote dans ${name} (ex. CONNU, FR094080_2026_AE0002)`} aria-label={`Rechercher dans ${name}`} />
        </div>
        <Button type="submit" icon={<Icons.search size={15} />} loading={search.isPending} disabled={!q.trim()}>
          Chercher
        </Button>
      </form>

      {search.isError && (
        <div style={{ marginTop: 12 }}>
          <Alert tone="danger" title={`${name} ne répond pas`}>{(search.error as Error).message}</Alert>
        </div>
      )}
      {doImport.isError && (
        <div style={{ marginTop: 12 }}>
          <Alert tone="danger" title="Import impossible">{(doImport.error as Error).message}</Alert>
        </div>
      )}
      {search.isSuccess && results.length === 0 && (
        <div style={{ marginTop: 12, fontSize: 13, color: "var(--text-tertiary)" }}>
          Aucune archive visible dans {name} pour « {q.trim()} ».
        </div>
      )}

      {results.length > 0 && (
        <div style={{ marginTop: 14, border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)", overflow: "hidden" }}>
          {results.map((r) => (
            <ResultRow key={r.cote} r={r} done={imported[r.cote]}
              pending={doImport.isPending && doImport.variables === r.cote}
              onImport={() => doImport.mutate(r.cote)} />
          ))}
        </div>
      )}
    </Card>
  );
}

function ResultRow({ r, done, pending, onImport }: {
  r: MasterSearchResult; done?: ImportFromMasterResponse; pending: boolean; onImport: () => void;
}) {
  const detail = `/records/detail?cote=${encodeURIComponent(r.cote)}`;
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 14px", borderBottom: "1px solid var(--border-subtle)" }}>
      <IconTile name="record" tone="info" size={34} variant="soft" />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600 }}>{r.title || "Sans intitulé"}</div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", fontSize: 12, color: "var(--text-secondary)", marginTop: 2 }}>
          <code style={{ fontSize: 12 }}>{r.cote}</code>
          {r.service && <span>· service {r.service}</span>}
          {r.typologie && <span>· profil {r.typologie}</span>}
          {r.duaRemainingYears != null && <span>· encore {r.duaRemainingYears} an{r.duaRemainingYears > 1 ? "s" : ""} à conserver</span>}
        </div>
      </div>
      {done ? (
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Badge tone="success" icon={<Icons.success size={12} weight="bold" />}>{done.created ? "Importée" : "Déjà suivie"}</Badge>
          <Link href={detail} style={{ fontSize: 13, fontWeight: 600, display: "inline-flex", alignItems: "center", gap: 4 }}>
            Ouvrir la fiche <Icons.chevronRight size={13} weight="bold" />
          </Link>
        </div>
      ) : (
        <Button size="sm" icon={<Icons.download size={14} />} loading={pending} onClick={onImport}>
          Importer
        </Button>
      )}
    </div>
  );
}
