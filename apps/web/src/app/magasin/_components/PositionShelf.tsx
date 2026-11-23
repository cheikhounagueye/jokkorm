"use client";

import { CopySimple } from "@phosphor-icons/react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import {
  Alert,
  Button,
  Card,
  CardHeader,
  CONTAINER_STATE,
  EmptyState,
  Field,
  IconTile,
  Input,
  Select,
  StatusBadge,
} from "@/components/ui";
import {
  changeContainerFormat,
  cm,
  findContainerByBarcode,
  getStorageSpace,
  listContainersAtPosition,
  placeContainer,
  storeRecord,
  type PhysicalContainerResponse,
  type StorageLocationResponse,
} from "@/lib/api";
import { Icons } from "@/lib/icons";
import { BOX_FINISH, etiquetteHref } from "./kinds";
import { LinkButton } from "./LinkButton";
import { SpaceGauge } from "./SpaceGauge";
import { spineLabel, useBoxFormats } from "./useBoxFormats";

const BOX = 104;       // taille du dessin de boîte
const PLANK = 16;      // épaisseur de la planche

export function PositionShelf({ position }: { position: StorageLocationResponse }) {
  const qc = useQueryClient();
  const query = useQuery({
    queryKey: ["containers-at", position.id],
    queryFn: () => listContainersAtPosition(position.id),
  });
  const space = useQuery({
    queryKey: ["space", position.id],
    queryFn: () => getStorageSpace(position.id),
  });
  const formats = useBoxFormats();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const placeRef = useRef<HTMLDivElement>(null);
  // Un rangement ou un déplacement change deux emplacements (source et destination) et la salle.
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["containers-at"] });
    qc.invalidateQueries({ queryKey: ["space"] });
    qc.invalidateQueries({ queryKey: ["room"] });
  };

  const boxes = query.data ?? [];
  const selected = boxes.find((b) => b.id === selectedId) ?? boxes[0] ?? null;
  const sp = space.data;

  const focusPlaceForm = () => {
    placeRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    placeRef.current?.querySelector("input")?.focus();
  };

  return (
    <>
      <Card>
        <CardHeader
          title="Tablette"
          subtitle={query.data
            ? `${boxes.length} boîte${boxes.length > 1 ? "s" : ""}${sp ? ` · ${cm(sp.usedMm)} sur ${cm(sp.lengthMm)}` : ""}`
            : "Chargement des boîtes…"}
        />
        {query.isError && <Alert tone="danger" title="Boîtes indisponibles">{(query.error as Error).message}</Alert>}

        {sp && (
          <div style={{ marginBottom: 16 }}>
            <SpaceGauge
              lengthMm={sp.lengthMm} usedMm={sp.usedMm} placeableMm={sp.placeableMm} marginPercent={sp.marginPercent}
              fits={sp.fits}
              segments={boxes.map((b) => ({
                key: b.id, spineMm: formats.spineOf(b.boxFormatId), title: b.label || b.barcode, active: selected?.id === b.id,
              }))}
              onSegmentClick={setSelectedId}
            />
          </div>
        )}

        {query.data && (
          <div style={{
            background: "linear-gradient(180deg, var(--surface-sunken) 0%, var(--surface-card) 100%)",
            border: "1px solid var(--border-subtle)",
            borderRadius: "var(--radius-md)",
            padding: "24px 20px 8px",
            overflowX: "auto",
          }}>
            <div style={{ position: "relative", display: "inline-flex", gap: 18, minWidth: "100%", padding: "0 12px" }}>
              {/* planche en bois/kraft derrière les boîtes */}
              <div aria-hidden style={{
                position: "absolute", left: 0, right: 0, top: BOX * 0.86, height: PLANK,
                background: "linear-gradient(180deg, var(--kraft-300) 0%, var(--kraft-500) 70%, var(--kraft-700) 100%)",
                borderRadius: 3,
                boxShadow: "0 6px 10px -4px rgba(15, 23, 42, 0.35)",
              }} />
              {boxes.map((b) => (
                <ShelfBox key={b.id} box={b} active={selected?.id === b.id} onClick={() => setSelectedId(b.id)} />
              ))}
              <FreeSlot onClick={focusPlaceForm} placeableMm={sp?.placeableMm} />
            </div>
          </div>
        )}
      </Card>

      {selected ? (
        <BoxDetails key={selected.id} box={selected} onStored={refresh} />
      ) : query.data && (
        <Card>
          <EmptyState icon={<Icons.box size={28} />} title="Position vide"
            description="Range une boîte existante avec le formulaire ci-dessous, ou crée une nouvelle boîte." />
        </Card>
      )}

      <div ref={placeRef}>
        <PlaceExistingContainer position={position} onPlaced={refresh} />
      </div>
    </>
  );
}

function ShelfBox({ box, active, onClick }: { box: PhysicalContainerResponse; active: boolean; onClick: () => void }) {
  const meta = CONTAINER_STATE[box.state];
  return (
    <button type="button" onClick={onClick} title={`${box.barcode}${box.label ? ` — ${box.label}` : ""}`}
      style={{
        position: "relative", zIndex: 1,
        width: BOX + 20, padding: "0 10px 10px",
        display: "flex", flexDirection: "column", alignItems: "center",
        border: "none", background: "transparent",
        borderRadius: "var(--radius-md)",
        outline: active ? "2px solid var(--brand-500)" : "none",
        outlineOffset: 4,
        transition: "transform 150ms ease",
      }}
      onMouseEnter={(e) => (e.currentTarget.style.transform = "translateY(-4px)")}
      onMouseLeave={(e) => (e.currentTarget.style.transform = "translateY(0)")}
    >
      <ArchiveBoxIso size={BOX} finish={BOX_FINISH[box.state]} title={box.label ?? box.barcode} />
      <div style={{ height: PLANK + 10 }} />
      <div style={{
        width: "100%", fontSize: 12, fontWeight: 600, color: "var(--text-primary)", lineHeight: 1.3,
        textAlign: "center", minHeight: 31,
        display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden",
      }}>
        {box.label || "Sans libellé"}
      </div>
      <div style={{ fontSize: 10, fontFamily: "var(--font-mono)", color: "var(--text-tertiary)", margin: "2px 0 6px" }}>
        {box.barcode}
      </div>
      <StatusBadge meta={meta} size="sm" />
    </button>
  );
}

function FreeSlot({ onClick, placeableMm }: { onClick: () => void; placeableMm?: number }) {
  const full = placeableMm !== undefined && placeableMm < 80;
  return (
    <button type="button" onClick={onClick}
      style={{
        position: "relative", zIndex: 1,
        width: BOX + 20, padding: "0 10px 10px",
        display: "flex", flexDirection: "column", alignItems: "center",
        border: "none", background: "transparent", color: "var(--text-tertiary)",
      }}>
      <div style={{
        width: BOX * 0.8, height: BOX * 0.74, marginTop: BOX * 0.12,
        border: "2px dashed var(--border-strong)", borderRadius: "var(--radius-md)",
        display: "flex", alignItems: "center", justifyContent: "center",
        background: "color-mix(in srgb, var(--surface-card) 60%, transparent)",
      }}>
        <Icons.add size={22} weight="bold" />
      </div>
      <div style={{ height: PLANK + 10 }} />
      <div style={{ fontSize: 12, fontWeight: 500 }}>{full ? "Plus de place" : "Emplacement libre"}</div>
      <div style={{ fontSize: 10, marginTop: 2 }}>
        {placeableMm === undefined ? "Ranger une boîte" : full ? `${cm(placeableMm)} restants` : `${cm(placeableMm)} rangeables`}
      </div>
    </button>
  );
}

function BoxDetails({ box, onStored }: { box: PhysicalContainerResponse; onStored: () => void }) {
  const [cote, setCote] = useState("");
  const mutation = useMutation({
    mutationFn: () => storeRecord(cote, box.barcode),
    onSuccess: () => { setCote(""); onStored(); },
  });
  const locked = box.state === "SEALED" || box.state === "DESTROYED";
  const formats = useBoxFormats();
  const current = formats.formatOf(box.boxFormatId);
  const reformat = useMutation({
    mutationFn: (formatId: string) => changeContainerFormat(box.id, formatId),
    onSuccess: onStored,
  });

  return (
    <Card>
      <div style={{ display: "flex", gap: 20, alignItems: "flex-start", flexWrap: "wrap" }}>
        <ArchiveBoxIso size={88} finish={BOX_FINISH[box.state]} />
        <div style={{ flex: 1, minWidth: 260 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap", marginBottom: 4 }}>
            <h2>{box.label || "Boîte sans libellé"}</h2>
            <StatusBadge meta={CONTAINER_STATE[box.state]} />
          </div>
          <div style={{ display: "flex", gap: 16, flexWrap: "wrap", fontSize: 13, color: "var(--text-secondary)", marginBottom: 16 }}>
            <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
              <Icons.barcode size={16} /> <code>{box.barcode}</code>
            </span>
            {box.capacity != null && (
              <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                <Icons.documents size={16} /> Capacité {box.capacity} dossiers
              </span>
            )}
            {current && (
              <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                <Icons.box size={16} /> {spineLabel(current.spineMm)} · {current.heightMm / 10} cm de haut
              </span>
            )}
            {box.externalReference && (
              <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
                <Icons.label size={16} /> Réf. {box.externalReference}
              </span>
            )}
          </div>

          <form onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}>
            <Field label="Ranger un dossier dans cette boîte" hint={locked ? "Boîte scellée ou détruite : rangement impossible." : "Cote du dossier, ex. DMG/MP/2024/047"}>
              <div style={{ display: "flex", gap: 8 }}>
                <Input value={cote} onChange={(e) => setCote(e.target.value)} disabled={locked}
                  placeholder="Cote du dossier" style={{ flex: 1 }} />
                <Button type="submit" icon={<Icons.record size={14} />} loading={mutation.isPending} disabled={locked || !cote}>
                  Ranger ici
                </Button>
              </div>
            </Field>
          </form>
          {mutation.isSuccess && <Alert tone="success" title="Dossier rangé dans la boîte." />}
          {mutation.isError && <Alert tone="danger">{(mutation.error as Error).message}</Alert>}

          {formats.active.length > 0 && box.state !== "DESTROYED" && (
            <div style={{ marginTop: 12, maxWidth: 360 }}>
              <Field label="Format de la boîte" hint="Corrige le dos si la boîte n'est pas celle enregistrée : la place est recontrôlée.">
                <Select value={current?.id ?? ""} disabled={reformat.isPending}
                  onChange={(e) => e.target.value && e.target.value !== current?.id && reformat.mutate(e.target.value)}>
                  {formats.active.map((f) => <option key={f.id} value={f.id}>{f.label}</option>)}
                </Select>
              </Field>
              {reformat.isError && <Alert tone="danger">{(reformat.error as Error).message}</Alert>}
            </div>
          )}

          <div style={{ display: "flex", gap: 8, marginTop: 12, flexWrap: "wrap" }}>
            <LinkButton href={etiquetteHref(box.barcode, box.label)} newTab icon={<Icons.print size={14} />}>
              Imprimer l&apos;étiquette
            </LinkButton>
            <Button variant="ghost" icon={<CopySimple size={14} />} onClick={() => navigator.clipboard.writeText(box.barcode)}>
              Copier le code-barres
            </Button>
          </div>
        </div>
      </div>
    </Card>
  );
}

function PlaceExistingContainer({
  position, onPlaced,
}: {
  position: StorageLocationResponse;
  onPlaced: () => void;
}) {
  const [barcode, setBarcode] = useState("");
  const mutation = useMutation({
    mutationFn: async () => {
      const container = await findContainerByBarcode(barcode.trim());
      const placed = await placeContainer(container.id, position.id);
      const moved = container.positionId !== null && container.positionId !== position.id;
      const stayed = container.positionId === position.id;
      return { placed, moved, stayed };
    },
    onSuccess: () => { setBarcode(""); onPlaced(); },
  });

  return (
    <Card>
      <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
        <IconTile name="scan" tone="success" size={36} variant="soft" />
        <form style={{ flex: 1 }} onSubmit={(e) => { e.preventDefault(); mutation.mutate(); }}>
          <Field label="Ranger une boîte à cette position" required
            hint="Scanne l'étiquette avec la douchette ou saisis le code-barres (Entrée pour valider).">
            <div style={{ display: "flex", gap: 8 }}>
              <Input value={barcode} onChange={(e) => setBarcode(e.target.value.toUpperCase())} required
                placeholder="BOX-261122-XXXXXX" autoComplete="off"
                style={{ flex: 1, fontFamily: "var(--font-mono)", letterSpacing: "0.04em" }} />
              <Button type="submit" icon={<Icons.box size={14} />} loading={mutation.isPending} disabled={!barcode.trim()}>
                Ranger
              </Button>
            </div>
          </Field>
          {mutation.isSuccess && (
            <Alert tone="success" title={mutation.data.stayed
              ? `${mutation.data.placed.barcode} est déjà à cette position.`
              : mutation.data.moved
                ? `${mutation.data.placed.barcode} déplacée ici : son ancien emplacement est libéré.`
                : `${mutation.data.placed.barcode} rangée à cette position.`} />
          )}
          {mutation.isError && <Alert tone="danger">{(mutation.error as Error).message}</Alert>}
        </form>
      </div>
    </Card>
  );
}
