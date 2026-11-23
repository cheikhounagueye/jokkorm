"use client";

import { Badge, Button, Card, IconTile } from "@/components/ui";
import type { Occupancy, RoomResponse } from "@/lib/api";
import { Icons, type IconName } from "@/lib/icons";
import { occupancyColor, percent, type RoomLayout, type Waypoint, type WaypointKind } from "./roomLayout";
import { EQUIPMENT_ICON, EQUIPMENT_NAME } from "./sceneShared";

const STEP_ICON: Record<WaypointKind, IconName> = {
  overview: "compass",
  entrance: "exit",
  aisle: "walk",
  shelf: "shelf",
  safety: "extinguisher",
  summary: "gauge",
};

export function OccupancyBar({ occupancy, label }: { occupancy: Occupancy; label?: string }) {
  const pct = percent(occupancy);
  const c = occupancyColor(occupancy.rate, occupancy.boxes);
  return (
    <div>
      {label !== undefined && (
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 4 }}>
          <span style={{ color: "var(--text-secondary)" }}>{label}</span>
          <span style={{ fontWeight: 600 }}>{occupancy.boxes}/{occupancy.capacity} · {pct} %</span>
        </div>
      )}
      <div style={{ height: 7, borderRadius: 99, background: "var(--surface-sunken)", overflow: "hidden", border: "1px solid var(--border-subtle)" }}>
        <div style={{ width: `${Math.max(pct, occupancy.boxes > 0 ? 3 : 0)}%`, height: "100%", background: c, transition: "width 300ms ease" }} />
      </div>
    </div>
  );
}

export function TourPanel({
  room,
  layout,
  step,
  playing,
  onStep,
  onTogglePlay,
}: {
  room: RoomResponse;
  layout: RoomLayout;
  step: number;
  playing: boolean;
  onStep: (n: number) => void;
  onTogglePlay: () => void;
}) {
  const wps = layout.waypoints;
  const wp = wps[step];
  if (!wp) return null;

  return (
    <Card padding={0}>
      <div style={{ padding: "16px 18px 14px", borderBottom: "1px solid var(--border-subtle)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <IconTile name={STEP_ICON[wp.kind]} tone={wp.kind === "safety" ? "danger" : "violet"} size={40} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--text-tertiary)" }}>
              Étape {step + 1} / {wps.length}
            </div>
            <h2 style={{ fontSize: 15, marginTop: 2 }}>{wp.title}</h2>
          </div>
        </div>
        <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 10 }}>{wp.subtitle}</p>
      </div>

      <div style={{ padding: "14px 18px", maxHeight: 300, overflow: "auto" }}>
        <StepDetail room={room} layout={layout} wp={wp} onJump={onStep} />
      </div>

      <div style={{ padding: "12px 18px 16px", borderTop: "1px solid var(--border-subtle)" }}>
        <div style={{ display: "flex", gap: 8, justifyContent: "space-between", alignItems: "center" }}>
          <Button variant="secondary" size="sm" icon={<Icons.prev size={14} weight="fill" />}
            onClick={() => onStep(Math.max(0, step - 1))} disabled={step === 0}>
            Précédent
          </Button>
          <Button variant={playing ? "secondary" : "primary"} size="sm"
            icon={playing ? <Icons.pause size={14} weight="fill" /> : <Icons.play size={14} weight="fill" />}
            onClick={onTogglePlay}>
            {playing ? "Pause" : "Lecture"}
          </Button>
          <Button variant="secondary" size="sm" iconRight={<Icons.nextStep size={14} weight="fill" />}
            onClick={() => onStep(Math.min(wps.length - 1, step + 1))} disabled={step === wps.length - 1}>
            Suivant
          </Button>
        </div>
        <div style={{ display: "flex", gap: 4, justifyContent: "center", marginTop: 12, flexWrap: "wrap" }}>
          {wps.map((w, i) => (
            <button key={i} type="button" title={w.title} onClick={() => onStep(i)}
              style={{
                width: i === step ? 18 : 7, height: 7, borderRadius: 99, border: "none", padding: 0,
                background: i === step ? "var(--brand-500)" : i < step ? "var(--brand-200)" : "var(--border-strong)",
                transition: "width 200ms ease",
              }} />
          ))}
        </div>
      </div>
    </Card>
  );
}

function StepDetail({ room, layout, wp, onJump }: {
  room: RoomResponse; layout: RoomLayout; wp: Waypoint; onJump: (n: number) => void;
}) {
  const shelfStep = (shelfId: string) => layout.waypoints.findIndex((w) => w.shelfId === shelfId);

  if (wp.kind === "shelf" && wp.shelfId) {
    const shelf = room.shelves.find((s) => s.node.id === wp.shelfId);
    if (!shelf) return null;
    const boxes = shelf.racks.flatMap((r) => r.positions.flatMap((p) => p.boxes));
    const count = (pred: (b: (typeof boxes)[number]) => boolean) => boxes.filter(pred).length;
    return (
      <div style={{ display: "grid", gap: 10 }}>
        {[...shelf.racks].reverse().map((r) => (
          <OccupancyBar key={r.node.id} occupancy={r.occupancy} label={`Tablette ${r.node.code}`} />
        ))}
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 4 }}>
          <Badge tone="success">{count((b) => b.state === "OPEN" && !b.onLoan)} actives</Badge>
          <Badge tone="warning">{count((b) => b.state === "SEALED" && !b.onLoan)} scellées</Badge>
          <Badge tone="neutral">{count((b) => b.state === "EMPTY" && !b.onLoan)} vides</Badge>
          <Badge tone="info">{count((b) => b.onLoan)} en prêt</Badge>
        </div>
      </div>
    );
  }

  if (wp.kind === "safety") {
    return (
      <div style={{ display: "grid", gap: 8 }}>
        {room.equipment.map((e) => (
          <div key={e.id} style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <IconTile name={EQUIPMENT_ICON[e.kind]} tone={e.kind === "FIRE_EXTINGUISHER" ? "danger" : e.kind === "EMERGENCY_EXIT" ? "success" : "info"} size={28} variant="soft" />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 12, fontWeight: 600 }}>{e.label ?? EQUIPMENT_NAME[e.kind]}</div>
              {e.nextInspectionOn && (
                <div style={{ fontSize: 11, color: "var(--text-tertiary)" }}>
                  Prochain contrôle : {new Date(e.nextInspectionOn).toLocaleDateString("fr-FR")}
                </div>
              )}
            </div>
            {e.inspectionOverdue && <Badge tone="danger" size="sm">En retard</Badge>}
          </div>
        ))}
      </div>
    );
  }

  const shelves = wp.kind === "aisle"
    ? layout.aisles[wp.aisleIndex ?? 0]?.shelves ?? []
    : room.shelves;
  return (
    <div style={{ display: "grid", gap: 10 }}>
      {(wp.kind === "overview" || wp.kind === "summary") && <OccupancyBar occupancy={room.occupancy} label="Salle entière" />}
      {shelves.map((s) => (
        <button key={s.node.id} type="button" onClick={() => { const i = shelfStep(s.node.id); if (i >= 0) onJump(i); }}
          style={{ background: "none", border: "none", padding: 0, textAlign: "left", cursor: "pointer" }}>
          <OccupancyBar occupancy={s.occupancy} label={`${s.node.code}${s.node.label ? ` · ${s.node.label}` : ""}`} />
        </button>
      ))}
    </div>
  );
}
