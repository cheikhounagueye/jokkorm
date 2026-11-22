"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import { Suspense, useCallback, useMemo, useRef, useState } from "react";
import { Button, Card, EmptyState, IconTile, PageHeader } from "@/components/ui";
import { listStorageTree } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { BarcodeScanner } from "./_components/BarcodeScanner";
import { ContainerCreationCard } from "./_components/ContainerCreationCard";
import { useTopographyImport } from "./_components/ImportTopography";
import { countByKind, findPath, KIND_META, KIND_ORDER } from "./_components/kinds";
import { NodePanel } from "./_components/NodePanel";
import { TopologyTree } from "./_components/TopologyTree";

export default function MagasinPage() {
  return (
    <Suspense fallback={null}>
      <MagasinContent />
    </Suspense>
  );
}

function MagasinContent() {
  const params = useSearchParams();
  const [selectedId, setSelectedId] = useState<string | null>(() => params.get("node"));
  const treeQuery = useQuery({ queryKey: ["storage-tree"], queryFn: listStorageTree });
  const qc = useQueryClient();
  const scannerRef = useRef<HTMLDivElement>(null);

  const refreshTree = useCallback(() => qc.invalidateQueries({ queryKey: ["storage-tree"] }), [qc]);
  const { actions: importActions, feedback: importFeedback } = useTopographyImport(() => treeQuery.refetch());

  /** Sélection + lien partageable ?node=<id> (sans navigation). */
  const select = useCallback((id: string | null) => {
    setSelectedId(id);
    const url = new URL(window.location.href);
    if (id) url.searchParams.set("node", id); else url.searchParams.delete("node");
    window.history.replaceState(null, "", url.toString());
  }, []);

  const roots = treeQuery.data;
  const path = useMemo(
    () => (roots && selectedId ? findPath(roots, selectedId) : null),
    [roots, selectedId],
  );
  const ancestorIds = useMemo(
    () => new Set((path ?? []).slice(0, -1).map((p) => p.node.id)),
    [path],
  );
  const counts = useMemo(() => (roots ? countByKind(roots) : null), [roots]);

  const focusScanner = () => {
    scannerRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    scannerRef.current?.querySelector("input")?.focus();
  };

  return (
    <section className="fade-up">
      <PageHeader
        icon="archiveRoom"
        tone="kraft"
        title="Salle d'archives"
        subtitle="Topographie physique et boîtes rangées : site, magasin, travée, tablette, position."
        crumbs={[{ label: "Gestion" }, { label: "Salle d'archives" }]}
        actions={
          <>
            {importActions}
            <Button icon={<Icons.scan size={15} />} onClick={focusScanner}>Scanner</Button>
          </>
        }
      />

      {importFeedback}

      {counts && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 20 }}>
          {KIND_ORDER.map((k) => {
            const m = KIND_META[k];
            return (
              <div key={k} style={{
                display: "inline-flex", alignItems: "center", gap: 8,
                padding: "6px 12px 6px 6px",
                background: "var(--surface-card)", border: "1px solid var(--border-subtle)",
                borderRadius: "var(--radius-full)", boxShadow: "var(--shadow-sm)",
              }}>
                <IconTile name={m.icon} tone={m.tone} size={24} variant="soft" />
                <span style={{ fontSize: 15, fontWeight: 700 }}>{counts[k]}</span>
                <span style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                  {counts[k] > 1 ? m.plural : m.label.toLowerCase()}
                </span>
              </div>
            );
          })}
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "320px minmax(0, 1fr)", gap: 20, alignItems: "start" }}>
        <TopologyTree
          roots={roots}
          loading={treeQuery.isLoading}
          error={treeQuery.isError ? (treeQuery.error as Error) : null}
          selectedId={selectedId}
          ancestorIds={ancestorIds}
          onSelect={select}
          onCreated={() => treeQuery.refetch()}
        />

        <div style={{ display: "flex", flexDirection: "column", gap: 20, minWidth: 0 }}>
          {path ? (
            <NodePanel
              key={selectedId}
              path={path}
              onSelect={select}
              onRenamed={refreshTree}
              onDeleted={() => { refreshTree(); select(null); }}
              onCreated={() => treeQuery.refetch()}
            />
          ) : (
            <Card>
              <EmptyState
                icon={<Icons.archiveRoom size={30} />}
                title="Choisis un emplacement"
                description="Sélectionne un nœud dans l'arbre pour voir son contenu. Sur une position, les boîtes apparaissent rangées sur la tablette."
              />
            </Card>
          )}

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))", gap: 20 }}>
            <ContainerCreationCard />
            <BarcodeScanner ref={scannerRef} onLocate={(positionId) => { select(positionId); window.scrollTo({ top: 0, behavior: "smooth" }); }} />
          </div>
        </div>
      </div>
    </section>
  );
}
