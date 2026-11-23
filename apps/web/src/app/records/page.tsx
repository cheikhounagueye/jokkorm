"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  IconTile,
  Input,
  PageHeader,
  Segmented,
  StatusBadge,
} from "@/components/ui";
import { listRecords, type PhysicalArchiveStatus } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons } from "@/lib/icons";
import { useRole } from "../demandes/_components/useRole";
import { ARCHIVE_STATUS, ARCHIVE_STATUSES } from "./_components/archiveStatus";
import { ImportFromMaster } from "./_components/ImportFromMaster";

type Filter = PhysicalArchiveStatus | "ALL";

export default function RecordsPage() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [statusFilter, setStatusFilter] = useState<PhysicalArchiveStatus | undefined>();
  const [search, setSearch] = useState("");
  const role = useRole();
  const canImport = role === "ARCHIVIST" || role === "ARCHIVES_HEAD";

  const query = useQuery({
    queryKey: ["records", page, size, statusFilter ?? "all"],
    queryFn: () => listRecords({ page, size, status: statusFilter }),
  });

  const needle = search.trim().toLowerCase();
  const rows = (query.data?.content ?? []).filter((r) => !needle || r.cote.toLowerCase().includes(needle));

  function openCote(e: React.FormEvent) {
    e.preventDefault();
    const cote = search.trim();
    if (cote) router.push(`/records/detail?cote=${encodeURIComponent(cote)}`);
  }

  return (
    <section className="fade-up">
      <PageHeader
        icon="archives"
        tone="brand"
        title="Archives physiques"
        subtitle={query.data
          ? `${query.data.totalElements} enveloppe${query.data.totalElements > 1 ? "s" : ""} suivie${query.data.totalElements > 1 ? "s" : ""} par JokkoRM`
          : "Enveloppes physiques suivies par JokkoRM"}
        crumbs={[{ label: "Accueil", href: "/" }, { label: "Archives" }]}
      />

      <Card padding={14} style={{ marginBottom: 16 }}>
        <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
          <form onSubmit={openCote} style={{ display: "flex", gap: 8, flex: "1 1 320px", minWidth: 260 }}>
            <div style={{ position: "relative", flex: 1 }}>
              <Icons.search size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "var(--text-tertiary)" }} />
              <Input value={search} onChange={(e) => setSearch(e.target.value)}
                placeholder="Rechercher une cote… (Entrée pour ouvrir)"
                aria-label="Rechercher une cote"
                style={{ paddingLeft: 36, fontFamily: search ? "var(--font-mono)" : undefined }} />
            </div>
            <Button type="submit" variant="secondary" icon={<Icons.view size={16} />} disabled={!search.trim()}>
              Ouvrir
            </Button>
          </form>
          <Segmented<Filter>
            value={statusFilter ?? "ALL"}
            onChange={(v) => { setStatusFilter(v === "ALL" ? undefined : v); setPage(0); }}
            options={[
              { value: "ALL", label: "Toutes" },
              ...ARCHIVE_STATUSES.map((s) => {
                const I = Icons[ARCHIVE_STATUS[s].icon];
                return { value: s as Filter, label: ARCHIVE_STATUS[s].label, icon: <I size={14} /> };
              }),
            ]}
          />
        </div>
      </Card>

      {canImport && <ImportFromMaster />}

      {query.isError && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="danger" title="Impossible de charger les archives">{(query.error as Error).message}</Alert>
        </div>
      )}

      {query.isLoading && (
        <Card><div style={{ color: "var(--text-tertiary)", fontSize: 13 }}>Chargement des archives…</div></Card>
      )}

      {query.data && (
        <>
          {rows.length === 0 ? (
            <Card>
              <EmptyState
                icon={<Icons.archives size={32} />}
                title="Aucune enveloppe"
                description={needle ? "Aucune cote de cette page ne correspond à la recherche." : "Aucune enveloppe pour ce filtre."}
              />
            </Card>
          ) : (
            <Card padding={0} style={{ overflow: "hidden" }}>
              <table>
                <thead>
                  <tr>
                    <th>Cote</th>
                    <th>Statut</th>
                    <th>Rangement</th>
                    <th>Enregistrée le</th>
                    <th>Dernière transition</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => {
                    const meta = ARCHIVE_STATUS[r.status];
                    return (
                      <tr key={r.id} style={{ cursor: "pointer" }}
                        onClick={() => router.push(`/records/detail?cote=${encodeURIComponent(r.cote)}`)}>
                        <td>
                          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                            <IconTile name={meta.icon} tone={meta.tone === "neutral" ? "slate" : meta.tone} size={30} variant="soft" />
                            <code>{r.cote}</code>
                          </div>
                        </td>
                        <td><StatusBadge meta={meta} /></td>
                        <td>
                          {r.containerId
                            ? <Badge tone="brand" icon={<Icons.box size={12} weight="bold" />}>En boîte</Badge>
                            : <span style={{ fontSize: 12, color: "var(--text-tertiary)" }}>Non rangée</span>}
                        </td>
                        <td style={{ color: "var(--text-secondary)", whiteSpace: "nowrap" }}>{formatInstant(r.registeredAt)}</td>
                        <td style={{ color: "var(--text-secondary)", whiteSpace: "nowrap" }}>{formatInstant(r.lastTransitionAt)}</td>
                        <td style={{ textAlign: "right" }}>
                          <Link href={`/records/detail?cote=${encodeURIComponent(r.cote)}`}
                            onClick={(e) => e.stopPropagation()}
                            style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 13, fontWeight: 600 }}>
                            Détail <Icons.chevronRight size={14} weight="bold" />
                          </Link>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </Card>
          )}

          <Pagination
            page={query.data.page}
            totalPages={query.data.totalPages}
            totalElements={query.data.totalElements}
            onChange={setPage}
          />
        </>
      )}
    </section>
  );
}

function Pagination(props: {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (p: number) => void;
}) {
  if (props.totalElements === 0) return null;
  return (
    <footer style={{
      display: "flex", justifyContent: "space-between", alignItems: "center",
      marginTop: 14, color: "var(--text-secondary)", fontSize: 13,
    }}>
      <span>
        {props.totalElements} enveloppe{props.totalElements > 1 ? "s" : ""} · page {props.page + 1} / {Math.max(props.totalPages, 1)}
      </span>
      <div style={{ display: "flex", gap: 8 }}>
        <Button variant="secondary" size="sm" disabled={props.page === 0}
          onClick={() => props.onChange(props.page - 1)}>Précédent</Button>
        <Button variant="secondary" size="sm" disabled={props.page >= props.totalPages - 1}
          iconRight={<Icons.chevronRight size={12} weight="bold" />}
          onClick={() => props.onChange(props.page + 1)}>Suivant</Button>
      </div>
    </footer>
  );
}
