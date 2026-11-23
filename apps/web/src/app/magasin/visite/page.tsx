"use client";

import { useQuery } from "@tanstack/react-query";
import dynamic from "next/dynamic";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import { Alert, Card, EmptyState, IconTile, PageHeader, Segmented } from "@/components/ui";
import { getRoom, listStorageTree, ml, type RoomResponse, type StorageLocationTreeResponse } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { Legend } from "./_components/Legend";
import { computeRoomLayout, percent, type Waypoint } from "./_components/roomLayout";
import { SelectionCard } from "./_components/SelectionCard";
import type { ColorMode, ScenePick } from "./_components/sceneShared";
import { OccupancyBar, TourPanel } from "./_components/TourPanel";

const RoomScene = dynamic(() => import("./_components/RoomScene"), {
  ssr: false,
  loading: () => <SceneMessage text="Chargement de la salle 3D…" />,
});

const CRUMBS_BASE = [{ label: "Salle d'archives", href: "/magasin" }];

export default function VisitePage() {
  return (
    <Suspense fallback={<SceneMessage text="Chargement…" />}>
      <Visite />
    </Suspense>
  );
}

function Visite() {
  const params = useSearchParams();
  const roomId = params.get("room");
  if (!roomId) return <RoomPicker />;
  const step = Number.parseInt(params.get("step") ?? "0", 10);
  return (
    <RoomTour
      key={roomId}
      roomId={roomId}
      initialStep={Number.isFinite(step) && step >= 0 ? step : 0}
      initialColor={params.get("color") === "occupancy" ? "occupancy" : "real"}
      initialMode={params.get("mode") === "free" ? "free" : "guided"}
    />
  );
}

// ─── Choix de la salle ──────────────────────────────────────────────────────

function RoomPicker() {
  const tree = useQuery({ queryKey: ["storage-tree"], queryFn: listStorageTree });
  const rooms = (tree.data ?? []).flatMap((site) =>
    site.children
      .filter((c) => c.node.kind === "STOREHOUSE")
      .map((c) => ({ site: site.node, room: c })));

  return (
    <section className="fade-up">
      <PageHeader icon="virtualTour" tone="violet" title="Visite 3D"
        subtitle="Choisis une salle pour la parcourir comme si tu y étais."
        crumbs={[...CRUMBS_BASE, { label: "Visite 3D" }]} />
      {tree.isError && <Alert tone="danger">{(tree.error as Error).message}</Alert>}
      {tree.data && rooms.length === 0 && (
        <EmptyState icon={<Icons.storehouse size={28} />} title="Aucune salle"
          description="Crée un magasin dans la topographie pour pouvoir le visiter." />
      )}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 16 }}>
        {rooms.map(({ site, room }) => <RoomCard key={room.node.id} siteLabel={site.label ?? site.code} room={room} />)}
      </div>
    </section>
  );
}

function RoomCard({ siteLabel, room }: { siteLabel: string; room: StorageLocationTreeResponse }) {
  const shelves = room.children.length;
  const positions = room.children.reduce((n, s) =>
    n + s.children.reduce((m, r) => m + r.children.length, 0), 0);
  return (
    <Link href={`/magasin/visite?room=${room.node.id}`} style={{ color: "inherit" }}>
      <Card interactive>
        <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12 }}>
          <IconTile name="storehouse" tone="kraft" size={44} />
          <div style={{ minWidth: 0 }}>
            <div style={{ fontWeight: 600 }}>{room.node.label ?? room.node.code}</div>
            <div style={{ fontSize: 12, color: "var(--text-tertiary)" }}>{siteLabel} · {room.node.code}</div>
          </div>
        </div>
        <div style={{ display: "flex", gap: 14, fontSize: 12, color: "var(--text-secondary)" }}>
          <span><strong style={{ color: "var(--text-primary)" }}>{shelves}</strong> travée{shelves > 1 ? "s" : ""}</span>
          <span><strong style={{ color: "var(--text-primary)" }}>{positions}</strong> emplacements</span>
          <span style={{ marginLeft: "auto", color: "var(--brand-500)", fontWeight: 600, display: "inline-flex", alignItems: "center", gap: 4 }}>
            Visiter <Icons.next size={12} weight="bold" />
          </span>
        </div>
      </Card>
    </Link>
  );
}

// ─── Visite ─────────────────────────────────────────────────────────────────

function RoomTour({ roomId, initialStep, initialColor, initialMode }: {
  roomId: string; initialStep: number; initialColor: ColorMode; initialMode: "guided" | "free";
}) {
  const query = useQuery({ queryKey: ["room", roomId], queryFn: () => getRoom(roomId) });
  const layout = useMemo(() => (query.data ? computeRoomLayout(query.data) : null), [query.data]);
  const [mode, setMode] = useState<"guided" | "free">(initialMode);
  const [color, setColor] = useState<ColorMode>(initialColor);
  const [step, setStep] = useState(initialStep);
  const [playing, setPlaying] = useState(false);
  const [pick, setPick] = useState<ScenePick | null>(null);
  const [freeTarget, setFreeTarget] = useState<Waypoint | null>(null);
  const dark = useDarkMode();

  const waypoints = layout?.waypoints ?? [];
  const safeStep = Math.min(step, Math.max(0, waypoints.length - 1));
  const current = waypoints[safeStep] ?? null;
  const target = mode === "guided" ? current : freeTarget ?? waypoints[0] ?? null;

  useEffect(() => {
    const url = new URL(window.location.href);
    url.searchParams.set("step", String(safeStep));
    url.searchParams.set("color", color);
    url.searchParams.set("mode", mode);
    window.history.replaceState(null, "", url);
  }, [safeStep, color, mode]);

  useEffect(() => {
    if (!playing || mode !== "guided") return;
    if (safeStep >= waypoints.length - 1) { setPlaying(false); return; }
    const t = window.setTimeout(() => setStep(safeStep + 1), 5500);
    return () => window.clearTimeout(t);
  }, [playing, mode, safeStep, waypoints.length]);

  if (query.isError) {
    return (
      <section>
        <PageHeader icon="virtualTour" tone="violet" title="Visite 3D" crumbs={[...CRUMBS_BASE, { label: "Visite 3D", href: "/magasin/visite" }]} />
        <Alert tone="danger" title="Salle introuvable">{(query.error as Error).message}</Alert>
      </section>
    );
  }
  if (!query.data || !layout) return <SceneMessage text="Chargement de la salle…" />;

  const room = query.data;
  const overdue = room.equipment.filter((e) => e.inspectionOverdue).length;

  return (
    <section>
      <PageHeader
        icon="virtualTour"
        tone="violet"
        title={`Visite 3D — ${room.room.label ?? room.room.code}`}
        subtitle={room.room.path}
        crumbs={[...CRUMBS_BASE, { label: "Visite 3D", href: "/magasin/visite" }, { label: room.room.code }]}
        actions={
          <>
            <Segmented value={mode} onChange={(m) => { setMode(m); setPlaying(false); }} options={[
              { value: "guided", label: "Visite guidée", icon: <Icons.walk size={14} /> },
              { value: "free", label: "Exploration libre", icon: <Icons.compass size={14} /> },
            ]} />
            <Segmented value={color} onChange={setColor} options={[
              { value: "real", label: "Réaliste", icon: <Icons.box size={14} /> },
              { value: "occupancy", label: "Occupation", icon: <Icons.gauge size={14} /> },
            ]} />
          </>
        }
      />

      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>
        <Chip icon="gauge" label="Occupation" value={`${percent(room.occupancy)} %`} />
        <Chip icon="box" label="Boîtes" value={String(room.occupancy.boxes)} />
        <Chip icon="shelf" label="Métrage occupé" value={`${ml(room.occupancy.usedMm)} / ${ml(room.occupancy.lengthMm)}`} />
        <Chip icon="position" label="Emplacements" value={String(room.positions)} />
        <Chip icon="loans" label="En prêt" value={String(room.boxesOnLoan)} />
        <Chip icon="extinguisher" label="Contrôles en retard" value={String(overdue)} alert={overdue > 0} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 360px", gap: 16, alignItems: "start" }}>
        <div style={{
          position: "relative", height: "calc(100vh - 290px)", minHeight: 520,
          borderRadius: "var(--radius-lg)", overflow: "hidden",
          border: "1px solid var(--border-strong)", boxShadow: "var(--shadow-md)",
          background: "var(--surface-sunken)",
        }}>
          <RoomScene
            layout={layout}
            colorMode={color}
            waypoint={target}
            showAllEquipmentLabels={mode === "free" || current?.kind === "safety"}
            selectedBoxId={pick?.kind === "box" ? pick.item.box.id : null}
            dark={dark}
            onPick={setPick}
          />
          <Legend mode={color} />
          {mode === "free" && (
            <div style={{
              position: "absolute", right: 12, top: 12, zIndex: 2, fontSize: 11,
              padding: "6px 10px", borderRadius: "var(--radius-md)", color: "var(--text-secondary)",
              background: "color-mix(in srgb, var(--surface-card) 92%, transparent)", border: "1px solid var(--border-subtle)",
            }}>
              Glisser : tourner · Molette : zoomer · Clic droit : se déplacer · Clic : détails
            </div>
          )}
        </div>

        <div style={{ display: "grid", gap: 16 }}>
          {pick && <SelectionCard pick={pick} revealed={room.detailsRevealed} marginPercent={room.marginPercent} onClose={() => setPick(null)} />}
          {mode === "guided" ? (
            <TourPanel room={room} layout={layout} step={safeStep} playing={playing}
              onStep={(n) => { setPlaying(false); setStep(n); }}
              onTogglePlay={() => setPlaying((p) => !p)} />
          ) : (
            <FreePanel room={room} waypoints={waypoints} onGo={setFreeTarget} />
          )}
          {!room.detailsRevealed && (
            <Alert tone="info" title="Vue demandeur">Les libellés et codes-barres des boîtes sont masqués.</Alert>
          )}
        </div>
      </div>
    </section>
  );
}

function FreePanel({ room, waypoints, onGo }: {
  room: RoomResponse; waypoints: Waypoint[]; onGo: (wp: Waypoint) => void;
}) {
  const overview = waypoints.find((w) => w.kind === "overview");
  const safety = waypoints.find((w) => w.kind === "safety");
  return (
    <Card>
      <h2 style={{ fontSize: 15, marginBottom: 4 }}>Aller à…</h2>
      <p style={{ fontSize: 12, color: "var(--text-tertiary)", marginBottom: 12 }}>Clique une travée pour y voler.</p>
      <div style={{ display: "grid", gap: 6 }}>
        {overview && <JumpButton icon="compass" label="Vue d'ensemble" onClick={() => onGo(overview)} />}
        {waypoints.filter((w) => w.kind === "shelf").map((w) => {
          const shelf = room.shelves.find((s) => s.node.id === w.shelfId);
          return (
            <button key={w.shelfId} type="button" onClick={() => onGo(w)}
              style={{ textAlign: "left", background: "none", border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)", padding: "8px 10px" }}>
              {shelf && <OccupancyBar occupancy={shelf.occupancy} label={`${shelf.node.code}${shelf.node.label ? ` · ${shelf.node.label}` : ""}`} />}
            </button>
          );
        })}
        {safety && <JumpButton icon="extinguisher" label="Sécurité incendie" onClick={() => onGo(safety)} />}
      </div>
    </Card>
  );
}

function JumpButton({ icon, label, onClick }: { icon: "compass" | "extinguisher"; label: string; onClick: () => void }) {
  const IconCmp = Icons[icon];
  return (
    <button type="button" onClick={onClick} style={{
      display: "flex", alignItems: "center", gap: 8, padding: "8px 10px", fontSize: 13, fontWeight: 600,
      background: "none", border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)", color: "var(--text-primary)",
    }}>
      <IconCmp size={16} /> {label}
    </button>
  );
}

function Chip({ icon, label, value, alert }: {
  icon: "gauge" | "box" | "shelf" | "position" | "loans" | "extinguisher"; label: string; value: string; alert?: boolean;
}) {
  const IconCmp = Icons[icon];
  return (
    <div style={{
      display: "inline-flex", alignItems: "center", gap: 8, padding: "7px 12px",
      background: alert ? "var(--danger-50)" : "var(--surface-card)",
      border: `1px solid ${alert ? "color-mix(in srgb, var(--danger-500) 35%, transparent)" : "var(--border-subtle)"}`,
      borderRadius: "var(--radius-full)", boxShadow: "var(--shadow-sm)", fontSize: 12,
    }}>
      <IconCmp size={16} style={{ color: alert ? "var(--danger-500)" : "var(--brand-500)" }} />
      <span style={{ color: "var(--text-secondary)" }}>{label}</span>
      <strong style={{ color: alert ? "var(--danger-700)" : "var(--text-primary)" }}>{value}</strong>
    </div>
  );
}

function SceneMessage({ text }: { text: string }) {
  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: 320, color: "var(--text-tertiary)", fontSize: 13, gap: 8 }}>
      <Icons.spinner size={18} className="spin" /> {text}
    </div>
  );
}

function useDarkMode(): boolean {
  const [dark, setDark] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const read = () => {
      const t = document.documentElement.getAttribute("data-theme");
      setDark(t === "dark" || (t !== "light" && mq.matches));
    };
    read();
    mq.addEventListener("change", read);
    const mo = new MutationObserver(read);
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
    return () => {
      mq.removeEventListener("change", read);
      mo.disconnect();
    };
  }, []);
  return dark;
}
