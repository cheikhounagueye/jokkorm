/**
 * Disposition 3D d'une salle, calculée à partir de la structure renvoyée par l'API.
 * Fonction pure, sans dépendance à three.js : testable et déterministe.
 *
 * Le modèle ne stocke ni rangées ni allées : on regroupe les travées (triées par code)
 * en rangées, chaque rangée fait face à l'entrée et une allée court devant elle.
 * Repère : x de l'ouest (0) à l'est, z de l'entrée (0) vers le fond, y vers le haut, en mètres.
 */
import type {
  Occupancy,
  RoomBox,
  RoomEquipment,
  RoomPosition,
  RoomRack,
  RoomResponse,
  RoomShelf,
} from "@/lib/api";

export type Vec3 = [number, number, number];

export const DIM = {
  boxW: 0.09,
  boxH: 0.27,
  boxD: 0.32,
  boxGap: 0.008,
  posPad: 0.035,
  uprightW: 0.05,
  firstLevelY: 0.14,
  levelH: 0.42,
  boardT: 0.025,
  unitDepth: 0.42,
  shelfGap: 0.3,
  aisle: 2.4,
  entrance: 3.4,
  backMargin: 1.4,
  sideMargin: 1.9,
  roomH: 3.1,
} as const;

export interface LayoutShelf {
  shelf: RoomShelf;
  row: number;
  x0: number;
  x1: number;
  zFront: number;
  height: number;
  boardYs: number[];
}

export interface LayoutSlot {
  position: RoomPosition;
  shelf: RoomShelf;
  rack: RoomRack;
  x0: number;
  x1: number;
  y: number;
  zFront: number;
}

export interface LayoutBox {
  box: RoomBox;
  center: Vec3;
  slot: LayoutSlot;
}

export interface LayoutAisle {
  index: number;
  z: number;
  x0: number;
  x1: number;
  shelves: RoomShelf[];
  occupancy: Occupancy;
}

export type Wall = "west" | "east" | "front" | "back" | null;

export interface LayoutEquipment {
  equipment: RoomEquipment;
  position: Vec3;
  wall: Wall;
  /** Rotation autour de y pour faire face à l'intérieur de la salle. */
  rotationY: number;
}

export type WaypointKind = "overview" | "entrance" | "aisle" | "shelf" | "safety" | "summary";

export interface Waypoint {
  kind: WaypointKind;
  title: string;
  subtitle: string;
  camera: Vec3;
  target: Vec3;
  shelfId?: string;
  aisleIndex?: number;
}

export interface RoomLayout {
  width: number;
  depth: number;
  height: number;
  shelves: LayoutShelf[];
  slots: LayoutSlot[];
  boxes: LayoutBox[];
  aisles: LayoutAisle[];
  equipment: LayoutEquipment[];
  doorX: number;
  waypoints: Waypoint[];
}

function positionWidth(p: RoomPosition): number {
  const cap = Math.max(p.occupancy.capacity, p.boxes.length, 1);
  return cap * DIM.boxW + (cap - 1) * DIM.boxGap + 2 * DIM.posPad;
}

function rackWidth(r: RoomRack): number {
  return r.positions.reduce((w, p) => w + positionWidth(p), 0);
}

function shelfWidth(s: RoomShelf): number {
  const inner = Math.max(1, ...s.racks.map(rackWidth));
  return inner + 2 * DIM.uprightW;
}

function shelvesPerRow(n: number): number {
  if (n <= 2) return Math.max(n, 1);
  if (n <= 6) return 2;
  return 3;
}

function sumOccupancy(items: { occupancy: Occupancy }[]): Occupancy {
  const boxes = items.reduce((s, i) => s + i.occupancy.boxes, 0);
  const capacity = items.reduce((s, i) => s + i.occupancy.capacity, 0);
  return { boxes, capacity, rate: capacity === 0 ? 0 : Math.min(1, boxes / capacity) };
}

export function percent(o: Occupancy): number {
  return Math.round(o.rate * 100);
}

export function computeRoomLayout(room: RoomResponse): RoomLayout {
  const perRow = shelvesPerRow(room.shelves.length);
  const rows: RoomShelf[][] = [];
  room.shelves.forEach((s, i) => {
    const r = Math.floor(i / perRow);
    (rows[r] ??= []).push(s);
  });

  const rowWidths = rows.map((row) =>
    row.reduce((w, s) => w + shelfWidth(s), 0) + DIM.shelfGap * Math.max(0, row.length - 1));
  const width = Math.max(6, ...rowWidths.map((w) => w + 2 * DIM.sideMargin));
  const rowCount = Math.max(rows.length, 1);
  const depth = DIM.entrance + (rowCount - 1) * (DIM.unitDepth + DIM.aisle) + DIM.unitDepth + DIM.backMargin;
  const height = DIM.roomH;

  const shelves: LayoutShelf[] = [];
  const slots: LayoutSlot[] = [];
  const boxes: LayoutBox[] = [];
  const aisles: LayoutAisle[] = [];

  rows.forEach((row, r) => {
    const zFront = DIM.entrance + r * (DIM.unitDepth + DIM.aisle);
    let x = (width - rowWidths[r]) / 2;

    for (const shelf of row) {
      const w = shelfWidth(shelf);
      const levels = Math.max(shelf.racks.length, 1);
      const boardYs = Array.from({ length: levels }, (_, i) => DIM.firstLevelY + i * DIM.levelH);
      const shelfHeight = DIM.firstLevelY + levels * DIM.levelH + 0.08;
      shelves.push({ shelf, row: r, x0: x, x1: x + w, zFront, height: shelfHeight, boardYs });

      shelf.racks.forEach((rack, level) => {
        const y = boardYs[level];
        let px = x + DIM.uprightW;
        for (const position of rack.positions) {
          const pw = positionWidth(position);
          const slot: LayoutSlot = { position, shelf, rack, x0: px, x1: px + pw, y, zFront };
          slots.push(slot);
          position.boxes.forEach((box, i) => {
            const cx = px + DIM.posPad + DIM.boxW / 2 + i * (DIM.boxW + DIM.boxGap);
            const cy = y + DIM.boardT / 2 + DIM.boxH / 2;
            const cz = zFront + 0.03 + DIM.boxD / 2;
            boxes.push({ box, center: [cx, cy, cz], slot });
          });
          px += pw;
        }
      });
      x += w + DIM.shelfGap;
    }

    const aisleZ = r === 0 ? zFront - DIM.entrance * 0.45 : zFront - DIM.aisle / 2;
    aisles.push({
      index: r,
      z: aisleZ,
      x0: DIM.sideMargin * 0.4,
      x1: width - DIM.sideMargin * 0.4,
      shelves: row,
      occupancy: sumOccupancy(row),
    });
  });

  const equipment = room.equipment.map((e) => placeEquipment(e, width, depth, height));
  const entrance = room.equipment.find((e) => e.kind === "EMERGENCY_EXIT" && e.z <= 0.02);
  const doorX = (entrance ? entrance.x : 0.5) * width;

  const layout: RoomLayout = {
    width, depth, height, shelves, slots, boxes, aisles, equipment, doorX, waypoints: [],
  };
  mirrorForEntranceView(layout);
  layout.waypoints = buildWaypoints(room, layout);
  return layout;
}

/**
 * Depuis l'entrée, la caméra regarde vers +z : l'axe x monde apparaît alors de droite à
 * gauche. On retourne les x pour que l'ouest, T01 et l'emplacement 01 soient à gauche
 * à l'écran, dans l'ordre de lecture.
 */
function mirrorForEntranceView(l: RoomLayout) {
  const mx = (x: number) => l.width - x;
  for (const s of l.shelves) [s.x0, s.x1] = [mx(s.x1), mx(s.x0)];
  for (const s of l.slots) [s.x0, s.x1] = [mx(s.x1), mx(s.x0)];
  for (const b of l.boxes) b.center = [mx(b.center[0]), b.center[1], b.center[2]];
  for (const a of l.aisles) [a.x0, a.x1] = [mx(a.x1), mx(a.x0)];
  for (const e of l.equipment) {
    e.position = [mx(e.position[0]), e.position[1], e.position[2]];
    e.rotationY = -e.rotationY;
  }
  l.doorX = mx(l.doorX);
}

function placeEquipment(e: RoomEquipment, width: number, depth: number, height: number): LayoutEquipment {
  const inset = 0.08;
  let wall: Wall = null;
  let x = e.x * width;
  let z = e.z * depth;
  let rotationY = 0;
  if (e.x <= 0.02) { wall = "west"; x = inset; rotationY = Math.PI / 2; }
  else if (e.x >= 0.98) { wall = "east"; x = width - inset; rotationY = -Math.PI / 2; }
  else if (e.z <= 0.02) { wall = "front"; z = inset; rotationY = 0; }
  else if (e.z >= 0.98) { wall = "back"; z = depth - inset; rotationY = Math.PI; }
  const y = e.ceilingMounted ? height - 0.03 : e.kind === "THERMO_HYGROMETER" ? 1.5 : 0;
  return { equipment: e, position: [x, y, z], wall, rotationY };
}

function buildWaypoints(room: RoomResponse, l: RoomLayout): Waypoint[] {
  const W = l.width;
  const D = l.depth;
  const span = Math.max(W, D);
  const rateText = `${percent(room.occupancy)} % occupé`;
  const wps: Waypoint[] = [];

  wps.push({
    kind: "overview",
    title: "Vue d'ensemble",
    subtitle: `${room.shelves.length} travées · ${room.positions} emplacements · ${rateText}`,
    camera: [W * 0.5, span * 0.85, -D * 0.38],
    target: [W * 0.5, 0, D * 0.52],
  });

  wps.push({
    kind: "entrance",
    title: "Entrée de la salle",
    subtitle: "Vue à hauteur d'homme depuis la porte principale.",
    camera: [l.doorX, 1.65, 0.6],
    target: [W * 0.5, 1.1, D * 0.62],
  });

  for (const aisle of l.aisles) {
    const codes = aisle.shelves.map((s) => s.node.code).join(", ");
    wps.push({
      kind: "aisle",
      title: `Allée ${aisle.index + 1}`,
      subtitle: `Travées ${codes} · ${percent(aisle.occupancy)} % occupé`,
      camera: [W - 0.55, 1.7, aisle.z],
      target: [0.6, 0.95, aisle.z + 0.9],
      aisleIndex: aisle.index,
    });
    for (const ls of l.shelves.filter((s) => s.row === aisle.index)) {
      const cx = (ls.x0 + ls.x1) / 2;
      const room_ = aisle.index === 0 ? DIM.entrance - 0.35 : DIM.aisle - 0.25;
      wps.push({
        kind: "shelf",
        title: `Travée ${ls.shelf.node.code}${ls.shelf.node.label ? ` — ${ls.shelf.node.label}` : ""}`,
        subtitle: `${ls.shelf.racks.length} tablettes · ${ls.shelf.occupancy.boxes}/${ls.shelf.occupancy.capacity} boîtes · ${percent(ls.shelf.occupancy)} %`,
        camera: [cx - 0.35, 1.45, ls.zFront - room_],
        target: [cx, ls.height * 0.48, ls.zFront + 0.1],
        shelfId: ls.shelf.node.id,
        aisleIndex: aisle.index,
      });
    }
  }

  const extinguishers = room.equipment.filter((e) => e.kind === "FIRE_EXTINGUISHER");
  const overdue = room.equipment.filter((e) => e.inspectionOverdue).length;
  const exits = room.equipment.filter((e) => e.kind === "EMERGENCY_EXIT").length;
  wps.push({
    kind: "safety",
    title: "Sécurité incendie et conservation",
    subtitle: `${extinguishers.length} extincteurs · ${exits} issues · ${overdue > 0 ? `${overdue} contrôle(s) en retard` : "contrôles à jour"}`,
    camera: [W * 0.5, span * 1.05, D * 0.5 - 0.6],
    target: [W * 0.5, 0, D * 0.5],
  });

  wps.push({
    kind: "summary",
    title: "Synthèse",
    subtitle: `${room.occupancy.boxes} boîtes sur ${room.occupancy.capacity} places · ${room.boxesOnLoan} en prêt`,
    camera: [W * 1.05, span * 0.7, -D * 0.2],
    target: [W * 0.5, 0.4, D * 0.5],
  });

  return wps;
}

/** Couleur d'occupation : vert sous 60 %, ambre jusqu'à 90 %, rouge au-delà, gris si vide. */
export function occupancyColor(rate: number, boxes: number): string {
  if (boxes === 0) return "#cbd5e1";
  if (rate < 0.6) return "#22c55e";
  if (rate < 0.9) return "#f59e0b";
  return "#ef4444";
}
