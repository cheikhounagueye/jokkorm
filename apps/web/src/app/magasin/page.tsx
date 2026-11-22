"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  createContainer,
  createStorageLocation,
  deleteStorageLocation,
  listContainersAtPosition,
  listStorageTree,
  placeContainer,
  storeRecord,
  updateStorageLocation,
  type ContainerState,
  type PhysicalContainerResponse,
  type StorageLocationKind,
  type StorageLocationResponse,
  type StorageLocationTreeResponse,
} from "@/lib/api";

const KIND_LABELS: Record<StorageLocationKind, string> = {
  SITE: "Site",
  STOREHOUSE: "Magasin",
  SHELF: "Travée",
  RACK: "Tablette",
  POSITION: "Position",
};

const KIND_COLORS: Record<StorageLocationKind, string> = {
  SITE: "#1e2d5e",
  STOREHOUSE: "#2e7d32",
  SHELF: "#5b7cb3",
  RACK: "#a07b00",
  POSITION: "#b71c1c",
};

const CHILD_OF: Record<StorageLocationKind, StorageLocationKind | null> = {
  SITE: "STOREHOUSE",
  STOREHOUSE: "SHELF",
  SHELF: "RACK",
  RACK: "POSITION",
  POSITION: null,
};

export default function MagasinPage() {
  const [selected, setSelected] = useState<StorageLocationResponse | null>(null);
  const treeQuery = useQuery({ queryKey: ["storage-tree"], queryFn: listStorageTree });
  const qc = useQueryClient();

  const refreshTreeAndClear = () => {
    qc.invalidateQueries({ queryKey: ["storage-tree"] });
    setSelected(null);
  };

  const refreshTreeAndKeep = (updated?: StorageLocationResponse) => {
    qc.invalidateQueries({ queryKey: ["storage-tree"] });
    if (updated) setSelected(updated);
  };

  return (
    <section>
      <header style={{ marginBottom: "1rem" }}>
        <h1 style={{ margin: "0 0 0.25rem" }}>Magasin</h1>
        <p style={{ margin: 0, color: "#666", fontSize: "0.9rem" }}>
          Topographie physique sur 5 niveaux (SITE → MAGASIN → TRAVÉE → TABLETTE → POSITION) et
          les boîtes qui y sont rangées. Pattern <strong>2 gardiens</strong> : tout ici est détenu
          par JokkoRM.
        </p>
      </header>

      <div style={{ display: "grid", gridTemplateColumns: "360px 1fr", gap: "1rem" }}>
        <section style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "1rem", maxHeight: "72vh", overflow: "auto" }}>
          <h2 style={{ marginTop: 0, fontSize: "0.9rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555" }}>
            Topographie
          </h2>
          {treeQuery.isLoading && <p>Chargement…</p>}
          {treeQuery.isError && <ErrorBanner error={treeQuery.error as Error} />}
          {treeQuery.data && (
            <>
              {treeQuery.data.length === 0 && (
                <p style={{ color: "#999", fontSize: "0.85rem" }}>
                  Aucun site. Créer un premier site ci-dessous.
                </p>
              )}
              {treeQuery.data.map((n) => (
                <TreeNode key={n.node.id} node={n} depth={0} selected={selected} onSelect={setSelected} />
              ))}
              <CreateChildForm parent={null} onCreated={() => treeQuery.refetch()} />
            </>
          )}
        </section>

        <section>
          {!selected && (
            <EmptyRightPane />
          )}
          {selected && (
            <>
              <SelectedHeader location={selected} />
              <AdminActions
                location={selected}
                onRenamed={refreshTreeAndKeep}
                onDeleted={refreshTreeAndClear}
              />
              {CHILD_OF[selected.kind] && (
                <CreateChildForm parent={selected} onCreated={() => treeQuery.refetch()} />
              )}
              {selected.kind === "POSITION" && (
                <PositionContents position={selected} />
              )}
            </>
          )}

          <ContainerCreationCard />
        </section>
      </div>
    </section>
  );
}

function TreeNode({
  node, depth, selected, onSelect,
}: {
  node: StorageLocationTreeResponse;
  depth: number;
  selected: StorageLocationResponse | null;
  onSelect: (l: StorageLocationResponse) => void;
}) {
  const isSelected = selected?.id === node.node.id;
  return (
    <div style={{ marginLeft: depth * 12 }}>
      <button
        onClick={() => onSelect(node.node)}
        style={{
          display: "block",
          width: "100%",
          textAlign: "left",
          padding: "0.3rem 0.5rem",
          border: "none",
          background: isSelected ? "#e8ecf7" : "transparent",
          borderLeft: `3px solid ${KIND_COLORS[node.node.kind]}`,
          borderRadius: 3,
          cursor: "pointer",
          fontSize: "0.9rem",
          marginBottom: 2,
        }}
      >
        <span style={{ color: KIND_COLORS[node.node.kind], fontSize: "0.7rem", fontWeight: 600, textTransform: "uppercase" }}>
          {KIND_LABELS[node.node.kind]}
        </span>
        {" "}
        <code>{node.node.code}</code>
        {node.node.label && <span style={{ color: "#666", marginLeft: 6 }}>· {node.node.label}</span>}
      </button>
      {node.children.map((c) => (
        <TreeNode key={c.node.id} node={c} depth={depth + 1} selected={selected} onSelect={onSelect} />
      ))}
    </div>
  );
}

function AdminActions({
  location, onRenamed, onDeleted,
}: {
  location: StorageLocationResponse;
  onRenamed: (updated: StorageLocationResponse) => void;
  onDeleted: () => void;
}) {
  const [renaming, setRenaming] = useState(false);
  const [newLabel, setNewLabel] = useState(location.label ?? "");
  const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
  const [typedCode, setTypedCode] = useState("");

  const renameMutation = useMutation({
    mutationFn: () => updateStorageLocation(location.id, newLabel || null),
    onSuccess: (updated) => { setRenaming(false); onRenamed(updated); },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteStorageLocation(location.id),
    onSuccess: () => { setConfirmDeleteOpen(false); setTypedCode(""); onDeleted(); },
  });

  const canConfirmDelete = typedCode.toUpperCase() === location.code;

  return (
    <div style={{ background: "#fafbfc", border: "1px dashed #d8dae0", borderRadius: 4, padding: "0.6rem 0.75rem", marginBottom: "0.75rem" }}>
      <div style={{ fontSize: "0.7rem", textTransform: "uppercase", fontWeight: 700, color: "#555", letterSpacing: "0.03em", marginBottom: 6 }}>
        Actions administrateur
      </div>
      {!renaming && !confirmDeleteOpen && (
        <div style={{ display: "flex", gap: 6 }}>
          <button onClick={() => { setNewLabel(location.label ?? ""); setRenaming(true); }} style={buttonSecondaryStyle}>
            Renommer
          </button>
          <button onClick={() => setConfirmDeleteOpen(true)} style={buttonDangerStyle}>
            Supprimer
          </button>
        </div>
      )}
      {renaming && (
        <form onSubmit={(e) => { e.preventDefault(); renameMutation.mutate(); }} style={{ display: "flex", gap: 6 }}>
          <input
            value={newLabel}
            onChange={(e) => setNewLabel(e.target.value)}
            placeholder="nouveau libellé (vide pour effacer)"
            autoFocus
            style={{ ...inputStyle, flex: 1 }}
          />
          <button type="submit" disabled={renameMutation.isPending} style={buttonStyle}>
            {renameMutation.isPending ? "…" : "Enregistrer"}
          </button>
          <button type="button" onClick={() => setRenaming(false)} style={buttonSecondaryStyle}>
            Annuler
          </button>
          {renameMutation.isError && <ErrorInline error={renameMutation.error as Error} />}
        </form>
      )}
      {confirmDeleteOpen && (
        <div style={{ background: "#fff3f3", border: "1px solid #ffbaba", borderRadius: 4, padding: "0.75rem" }}>
          <div style={{ fontSize: "0.85rem", color: "#a00", fontWeight: 600, marginBottom: 6 }}>
            Suppression destructive
          </div>
          <p style={{ margin: "0 0 0.5rem", fontSize: "0.85rem", color: "#555" }}>
            Tape le code <code>{location.code}</code> pour confirmer la suppression de <code>{location.path}</code>.
            L&apos;opération échoue si le nœud contient des enfants ou des boîtes.
          </p>
          <div style={{ display: "flex", gap: 6 }}>
            <input
              value={typedCode}
              onChange={(e) => setTypedCode(e.target.value)}
              placeholder={`retape « ${location.code} »`}
              autoFocus
              style={{ ...inputStyle, flex: 1 }}
            />
            <button
              onClick={() => deleteMutation.mutate()}
              disabled={!canConfirmDelete || deleteMutation.isPending}
              style={buttonDangerStyle}>
              {deleteMutation.isPending ? "…" : "Supprimer définitivement"}
            </button>
            <button onClick={() => { setConfirmDeleteOpen(false); setTypedCode(""); }} style={buttonSecondaryStyle}>
              Annuler
            </button>
          </div>
          {deleteMutation.isError && <div style={{ color: "#a00", marginTop: 6, fontSize: "0.85rem" }}>{(deleteMutation.error as Error).message}</div>}
        </div>
      )}
    </div>
  );
}

function SelectedHeader({ location }: { location: StorageLocationResponse }) {
  return (
    <div style={{ background: "white", border: "1px solid #e3e5ea", borderTop: `4px solid ${KIND_COLORS[location.kind]}`, borderRadius: 6, padding: "1rem", marginBottom: "0.75rem" }}>
      <div style={{ fontSize: "0.7rem", textTransform: "uppercase", color: KIND_COLORS[location.kind], fontWeight: 700, letterSpacing: "0.03em" }}>
        {KIND_LABELS[location.kind]} sélectionné
      </div>
      <h2 style={{ margin: "0.25rem 0 0" }}>
        <code>{location.path}</code>
      </h2>
      {location.label && <p style={{ margin: "0.25rem 0 0", color: "#666" }}>{location.label}</p>}
    </div>
  );
}

function CreateChildForm({
  parent,
  onCreated,
}: {
  parent: StorageLocationResponse | null;
  onCreated: () => void;
}) {
  const kind: StorageLocationKind = parent ? CHILD_OF[parent.kind]! : "SITE";
  const [code, setCode] = useState("");
  const [label, setLabel] = useState("");

  const mutation = useMutation({
    mutationFn: () => createStorageLocation({
      kind, code, label: label || undefined, parentId: parent?.id ?? null,
    }),
    onSuccess: () => { setCode(""); setLabel(""); onCreated(); },
  });

  return (
    <form
      onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}
      style={{ marginTop: "1rem", padding: "0.75rem", background: "#fafbfc", border: "1px dashed #d8dae0", borderRadius: 4 }}
    >
      <div style={{ fontSize: "0.75rem", fontWeight: 600, color: "#555", textTransform: "uppercase", marginBottom: 6 }}>
        Créer un(e) {KIND_LABELS[kind]}
        {parent && <span style={{ color: "#888", textTransform: "none", fontWeight: 400 }}> sous <code>{parent.path}</code></span>}
      </div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        <input
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="code (A-Z0-9_-)"
          required
          style={inputStyle}
        />
        <input
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          placeholder="libellé (optionnel)"
          style={{ ...inputStyle, flex: 1 }}
        />
        <button type="submit" disabled={mutation.isPending || !code} style={buttonStyle}>
          {mutation.isPending ? "…" : "Créer"}
        </button>
      </div>
      {mutation.isError && <ErrorInline error={mutation.error as Error} />}
    </form>
  );
}

function PositionContents({ position }: { position: StorageLocationResponse }) {
  const qc = useQueryClient();
  const query = useQuery({
    queryKey: ["containers-at", position.id],
    queryFn: () => listContainersAtPosition(position.id),
  });

  return (
    <section style={{ background: "white", border: "1px solid #e3e5ea", borderRadius: 6, padding: "1rem", marginTop: "0.75rem" }}>
      <h2 style={{ marginTop: 0, fontSize: "0.9rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555" }}>
        Boîtes à cette position
      </h2>

      {query.isLoading && <p>Chargement…</p>}
      {query.isError && <ErrorBanner error={query.error as Error} />}
      {query.data && query.data.length === 0 && (
        <p style={{ color: "#999", fontSize: "0.9rem" }}>
          Aucune boîte ici. Ranger une boîte existante avec le formulaire ci-dessous, ou créer
          une nouvelle boîte puis la ranger.
        </p>
      )}
      {query.data && query.data.map((c) => (
        <ContainerCard key={c.id} container={c} onRecordStored={() => qc.invalidateQueries({ queryKey: ["containers-at", position.id] })} />
      ))}

      <PlaceExistingContainerForm position={position} onPlaced={() => qc.invalidateQueries({ queryKey: ["containers-at", position.id] })} />
    </section>
  );
}

function ContainerCard({
  container, onRecordStored,
}: {
  container: PhysicalContainerResponse;
  onRecordStored: () => void;
}) {
  return (
    <article style={{ border: "1px solid #e3e5ea", borderLeft: `4px solid ${stateColor(container.state)}`, borderRadius: 4, padding: "0.75rem", marginBottom: "0.6rem" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <code style={{ fontSize: "0.95rem" }}>{container.barcode}</code>
          {container.label && <span style={{ color: "#666", marginLeft: 8 }}>· {container.label}</span>}
        </div>
        <span style={{ display: "inline-block", padding: "0.15rem 0.5rem", borderRadius: 3, background: stateColor(container.state) + "22", color: stateColor(container.state), fontSize: "0.75rem", fontWeight: 600 }}>
          {stateLabel(container.state)}
        </span>
      </div>
      <StoreRecordInline container={container} onDone={onRecordStored} />
    </article>
  );
}

function StoreRecordInline({ container, onDone }: { container: PhysicalContainerResponse; onDone: () => void }) {
  const [cote, setCote] = useState("");
  const mutation = useMutation({
    mutationFn: () => storeRecord(cote, container.barcode),
    onSuccess: () => { setCote(""); onDone(); },
  });
  const disabled = container.state === "SEALED" || container.state === "DESTROYED";

  return (
    <form
      onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}
      style={{ display: "flex", gap: 6, marginTop: 8, alignItems: "center", fontSize: "0.85rem" }}
    >
      <input
        value={cote}
        onChange={(e) => setCote(e.target.value)}
        placeholder="cote (ex. DMG/MP/2024/047)"
        disabled={disabled}
        style={{ ...inputStyle, flex: 1 }}
      />
      <button type="submit" disabled={disabled || mutation.isPending || !cote} style={buttonStyle}>
        {mutation.isPending ? "…" : "Ranger ici"}
      </button>
      {mutation.isSuccess && <span style={{ color: "#2e7d32", fontSize: "0.8rem" }}>✓</span>}
      {mutation.isError && <ErrorInline error={mutation.error as Error} />}
    </form>
  );
}

function PlaceExistingContainerForm({ position, onPlaced }: { position: StorageLocationResponse; onPlaced: () => void }) {
  const [containerId, setContainerId] = useState("");
  const mutation = useMutation({
    mutationFn: () => placeContainer(containerId, position.id),
    onSuccess: () => { setContainerId(""); onPlaced(); },
  });

  return (
    <form
      onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}
      style={{ marginTop: "0.75rem", padding: "0.6rem", background: "#fafbfc", border: "1px dashed #d8dae0", borderRadius: 4 }}
    >
      <div style={{ fontSize: "0.75rem", fontWeight: 600, color: "#555", textTransform: "uppercase", marginBottom: 6 }}>
        Ranger une boîte existante ici
      </div>
      <div style={{ display: "flex", gap: 6 }}>
        <input
          value={containerId}
          onChange={(e) => setContainerId(e.target.value)}
          placeholder="UUID de la boîte (voir ci-contre après création)"
          required
          style={{ ...inputStyle, flex: 1, fontFamily: "monospace", fontSize: "0.75rem" }}
        />
        <button type="submit" disabled={mutation.isPending || !containerId} style={buttonStyle}>
          {mutation.isPending ? "…" : "Ranger"}
        </button>
      </div>
      {mutation.isError && <ErrorInline error={mutation.error as Error} />}
      {mutation.isSuccess && <span style={{ color: "#2e7d32", fontSize: "0.8rem" }}>Boîte rangée ici.</span>}
    </form>
  );
}

function ContainerCreationCard() {
  const [barcode, setBarcode] = useState("");
  const [label, setLabel] = useState("");
  const [capacity, setCapacity] = useState("");
  const [created, setCreated] = useState<PhysicalContainerResponse | null>(null);

  const mutation = useMutation({
    mutationFn: () => createContainer({
      barcode, label: label || undefined,
      capacity: capacity ? parseInt(capacity, 10) : undefined,
    }),
    onSuccess: (c) => { setCreated(c); setBarcode(""); setLabel(""); setCapacity(""); },
  });

  return (
    <aside style={{ background: "white", border: "1px solid #e3e5ea", borderTop: "4px solid #5b7cb3", borderRadius: 6, padding: "1rem", marginTop: "0.75rem" }}>
      <h2 style={{ marginTop: 0, fontSize: "0.9rem", textTransform: "uppercase", letterSpacing: "0.03em", color: "#555" }}>
        Créer une nouvelle boîte
      </h2>
      <p style={{ margin: "0 0 0.5rem", color: "#666", fontSize: "0.85rem" }}>
        La boîte est créée vide, sans position. Son UUID apparaît ci-dessous après création,
        à copier dans le formulaire « Ranger une boîte » d&apos;une position.
      </p>
      <form
        onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}
        style={{ display: "flex", gap: 6, flexWrap: "wrap" }}
      >
        <input
          value={barcode}
          onChange={(e) => setBarcode(e.target.value)}
          placeholder="code-barres (ex. DMG-2024-112)"
          required
          style={{ ...inputStyle, flex: "1 1 180px" }}
        />
        <input
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          placeholder="libellé"
          style={{ ...inputStyle, flex: "1 1 180px" }}
        />
        <input
          value={capacity}
          onChange={(e) => setCapacity(e.target.value)}
          placeholder="capacité"
          type="number"
          min={1}
          style={{ ...inputStyle, width: 100 }}
        />
        <button type="submit" disabled={mutation.isPending || !barcode} style={buttonStyle}>
          {mutation.isPending ? "…" : "Créer boîte"}
        </button>
      </form>
      {mutation.isError && <ErrorInline error={mutation.error as Error} />}
      {created && (
        <div style={{ marginTop: "0.6rem", padding: "0.5rem 0.75rem", background: "#f0f6ef", border: "1px solid #c8e3c0", borderRadius: 4, fontSize: "0.85rem" }}>
          Boîte créée : <code>{created.barcode}</code> · UUID <code style={{ fontSize: "0.7rem" }}>{created.id}</code>
          <button
            type="button"
            onClick={() => navigator.clipboard.writeText(created.id)}
            style={{ ...buttonStyle, marginLeft: 8, fontSize: "0.7rem", padding: "0.2rem 0.5rem" }}
          >
            Copier UUID
          </button>
        </div>
      )}
    </aside>
  );
}

function EmptyRightPane() {
  return (
    <div style={{ background: "white", border: "1px dashed #d8dae0", borderRadius: 6, padding: "2rem", textAlign: "center", color: "#888" }}>
      <p style={{ margin: 0 }}>Sélectionne un nœud dans l&apos;arbre à gauche pour voir ses détails et y créer des enfants.</p>
      <p style={{ margin: "0.5rem 0 0", fontSize: "0.85rem" }}>
        Commence par créer un <strong>SITE</strong> depuis le formulaire tout en bas à gauche,
        puis descend vers MAGASIN → TRAVÉE → TABLETTE → POSITION.
      </p>
    </div>
  );
}

function ErrorBanner({ error }: { error: Error }) {
  return (
    <div style={{ background: "#fff3f3", border: "1px solid #ffbaba", padding: "0.5rem 0.75rem", borderRadius: 4, color: "#a00", marginTop: 4, fontSize: "0.85rem" }}>
      {error.message}
    </div>
  );
}

function ErrorInline({ error }: { error: Error }) {
  return <span style={{ color: "#a00", fontSize: "0.8rem", marginLeft: 8 }}>{error.message}</span>;
}

const inputStyle: React.CSSProperties = {
  padding: "0.35rem 0.55rem",
  border: "1px solid #c8cbd4",
  borderRadius: 3,
  fontSize: "0.85rem",
  fontFamily: "inherit",
};

const buttonStyle: React.CSSProperties = {
  padding: "0.35rem 0.85rem",
  background: "#1e2d5e",
  color: "white",
  border: "none",
  borderRadius: 3,
  fontSize: "0.85rem",
  fontWeight: 500,
};

const buttonSecondaryStyle: React.CSSProperties = {
  ...buttonStyle,
  background: "white",
  color: "#1e2d5e",
  border: "1px solid #1e2d5e",
};

const buttonDangerStyle: React.CSSProperties = {
  ...buttonStyle,
  background: "#b71c1c",
};

function stateLabel(s: ContainerState): string {
  return s === "EMPTY" ? "Vide" : s === "OPEN" ? "Ouverte" : s === "SEALED" ? "Scellée" : "Détruite";
}
function stateColor(s: ContainerState): string {
  return s === "EMPTY" ? "#5b7cb3" : s === "OPEN" ? "#2e7d32" : s === "SEALED" ? "#a07b00" : "#b71c1c";
}
