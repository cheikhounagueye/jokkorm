/**
 * Disposition 3D d'une salle, calculée à partir de la structure renvoyée par l'API.
 * Fonction pure, sans dépendance à three.js : testable et déterministe.
 *
 * Avec des épis (pattern 10, 5T.B/D) : une rangée par épi, face A tournée vers l'entrée,
 * face B dos à dos derrière elle, travées alignées par rang, profondeur et hauteur libre
 * mesurées, salle et plafond à leurs dimensions réelles quand elles sont saisies.
 * Sans épi : les travées (triées par code) sont regroupées en rangées comme avant.
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
import { ml } from "@/lib/api";

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
  /** Profondeur du meuble (m). */
  depth: number;
  /** 1 : ouvert vers l'entrée (face A) ; -1 : ouvert vers le fond (face B, dos à dos). */
  facing: 1 | -1;
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
  depth: number;
}

export interface LayoutBox {
  box: RoomBox;
  center: Vec3;
  /** Largeur visible en m (dos de la boîte moins le jeu entre boîtes). */
  width: number;
  slot: LayoutSlot;
  facing: 1 | -1;
}

export interface LayoutAisle {
  index: number;
  /** « Épi E1 » ou « Allée 2 ». */
  label: string;
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
  /** Profondeur de la zone d'entrée et largeur des allées (m) : mesurées ou par défaut. */
  entranceGap: number;
  aisleGap: number;
}

/** Échelle réelle (pattern 10) : 1 unité de scène = 1 m, les longueurs arrivent en mm. */
const MM = 0.001;

/** Un emplacement est aussi large que sa longueur utile (ou que ses boîtes, s'il est surchargé). */
function positionWidth(p: RoomPosition): number {
  return Math.max(p.occupancy.lengthMm, p.occupancy.usedMm, 50) * MM;
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
  const sum = (k: keyof Occupancy) => items.reduce((s, i) => s + i.occupancy[k], 0);
  const usedMm = sum("usedMm");
  const lengthMm = sum("lengthMm");
  return {
    boxes: sum("boxes"),
    capacity: sum("capacity"),
    usedMm,
    lengthMm,
    placeableMm: sum("placeableMm"),
    rate: lengthMm === 0 ? 0 : Math.min(1, usedMm / lengthMm),
  };
}

/** « 12,8 ml sur 14,4 ml » */
export function linearText(o: Occupancy): string {
  return `${ml(o.usedMm)} sur ${ml(o.lengthMm)}`;
}

export function percent(o: Occupancy): number {
  return Math.round(o.rate * 100);
}

/** Une rangée à poser : colonnes alignées par rang, face A devant, face B derrière. */
interface PlannedRow {
  label: string;
  front: (RoomShelf | null)[];
  back: (RoomShelf | null)[];
}

function unitDepth(s: RoomShelf | null): number {
  return s?.furniture?.depthMm ? s.furniture.depthMm * MM : DIM.unitDepth;
}

/** E2 avant E10. */
function aisleOrderKey(code: string): string {
  const d = code.replace(/\D/g, "");
  return d ? code.replace(/\d/g, "") + d.padStart(6, "0") : code;
}

function planRows(room: RoomResponse): PlannedRow[] {
  const withAisle = room.shelves.filter((s) => s.furniture?.aisleCode);
  const without = room.shelves.filter((s) => !s.furniture?.aisleCode);
  const rows: PlannedRow[] = [];
  const groups = new Map<string, RoomShelf[]>();
  for (const s of withAisle) {
    const k = s.furniture.aisleCode!;
    (groups.get(k) ?? groups.set(k, []).get(k)!).push(s);
  }
  for (const code of [...groups.keys()].sort((a, b) => aisleOrderKey(a).localeCompare(aisleOrderKey(b)))) {
    const list = groups.get(code)!;
    const rankOf = (s: RoomShelf, i: number) => s.furniture.aisleOrder ?? i + 1;
    const a = list.filter((s) => s.furniture.aisleSide !== "B").sort((x, y) => (x.furniture.aisleOrder ?? 0) - (y.furniture.aisleOrder ?? 0) || x.node.code.localeCompare(y.node.code));
    const b = list.filter((s) => s.furniture.aisleSide === "B").sort((x, y) => (x.furniture.aisleOrder ?? 0) - (y.furniture.aisleOrder ?? 0) || x.node.code.localeCompare(y.node.code));
    const ranks = [...new Set([...a.map(rankOf), ...b.map(rankOf)])].sort((x, y) => x - y);
    const at = (side: RoomShelf[], r: number) => side.find((s, i) => rankOf(s, i) === r) ?? null;
    rows.push({ label: `Épi ${code}`, front: ranks.map((r) => at(a, r)), back: b.length ? ranks.map((r) => at(b, r)) : [] });
  }
  const perRow = shelvesPerRow(without.length);
  for (let i = 0; i < without.length; i += perRow) {
    rows.push({ label: rows.length ? "Sans épi" : `Allée ${rows.length + 1}`, front: without.slice(i, i + perRow), back: [] });
  }
  if (!withAisle.length) rows.forEach((r, i) => { r.label = `Allée ${i + 1}`; });
  return rows;
}

export function computeRoomLayout(room: RoomResponse): RoomLayout {
  const rows = planRows(room);
  const colWidths = rows.map((row) => row.front.map((f, k) =>
    Math.max(f ? shelfWidth(f) : 0, row.back[k] ? shelfWidth(row.back[k]!) : 0, 0.3)));
  const rowWidths = colWidths.map((cols) => cols.reduce((w, c) => w + c, 0) + DIM.shelfGap * Math.max(0, cols.length - 1));
  // Salle mesurée : ses vraies dimensions, telles quelles (un mobilier trop grand déborde des
  // murs, comme le signale l'alerte de salle). Non mesurée : taille déduite du mobilier.
  const dims = room.dims;
  const width = dims?.widthMm ? dims.widthMm * MM : Math.max(6, ...rowWidths.map((w) => w + 2 * DIM.sideMargin));
  const height = dims?.ceilingHeightMm ? dims.ceilingHeightMm * MM : DIM.roomH;
  const rowDepths = rows.map((row) => Math.max(...row.front.map(unitDepth)) + (row.back.length ? Math.max(...row.back.map(unitDepth)) : 0));
  // Longueur mesurée : les allées se partagent la place restante (entrée, entre rangées, fond), 60 cm au moins.
  const measuredGap = dims?.lengthMm
    ? Math.max(0.6, (dims.lengthMm * MM - rowDepths.reduce((a, b) => a + b, 0)) / (rows.length + 1))
    : null;
  const entranceGap = measuredGap ?? DIM.entrance;
  const aisleGap = measuredGap ?? DIM.aisle;

  const shelves: LayoutShelf[] = [];
  const slots: LayoutSlot[] = [];
  const boxes: LayoutBox[] = [];
  const aisles: LayoutAisle[] = [];

  const place = (shelf: RoomShelf, row: number, x: number, zFront: number, depth: number, facing: 1 | -1) => {
    const w = shelfWidth(shelf);
    const levels = Math.max(shelf.racks.length, 1);
    const clearanceOf = (rack: RoomRack | undefined) =>
      (rack?.clearanceMm ?? shelf.furniture?.clearanceMm) ? (rack?.clearanceMm ?? shelf.furniture.clearanceMm)! * MM : DIM.levelH - DIM.boardT;
    const boardYs: number[] = [];
    let y = DIM.firstLevelY;
    for (let i = 0; i < levels; i++) {
      boardYs.push(y);
      y += clearanceOf(shelf.racks[i]) + DIM.boardT;
    }
    const measured = shelf.furniture?.heightMm ? shelf.furniture.heightMm * MM : 0;
    const shelfHeight = Math.max(y + 0.06, measured);
    shelves.push({ shelf, row, depth, facing, x0: x, x1: x + w, zFront, height: shelfHeight, boardYs });

    shelf.racks.forEach((rack, level) => {
      const by = boardYs[level];
      let px = x + DIM.uprightW;
      for (const position of rack.positions) {
        const pw = positionWidth(position);
        const slot: LayoutSlot = { position, shelf, rack, x0: px, x1: px + pw, y: by, zFront, depth };
        slots.push(slot);
        // Les boîtes se suivent sur la tablette, chacune aussi large que son dos, côté allée.
        let bx = px;
        for (const box of position.boxes) {
          const bw = box.spineMm * MM;
          const visible = Math.max(0.02, bw - DIM.boxGap);
          const cy = by + DIM.boardT / 2 + DIM.boxH / 2;
          const cz = facing === 1 ? zFront + 0.03 + DIM.boxD / 2 : zFront + depth - 0.03 - DIM.boxD / 2;
          boxes.push({ box, center: [bx + DIM.boxGap / 2 + visible / 2, cy, cz], width: visible, slot, facing });
          bx += bw;
        }
        px += pw;
      }
    });
  };

  let z = entranceGap;
  rows.forEach((row, r) => {
    const depthA = Math.max(...row.front.map(unitDepth));
    const depthB = row.back.length ? Math.max(...row.back.map(unitDepth)) : 0;
    let x = (width - rowWidths[r]) / 2;
    row.front.forEach((f, k) => {
      if (f) place(f, r, x, z, unitDepth(f), 1);
      const b = row.back[k];
      if (b) place(b, r, x, z + depthA, unitDepth(b), -1);
      x += colWidths[r][k] + DIM.shelfGap;
    });
    const rowShelves = [...row.front, ...row.back].filter((s): s is RoomShelf => s !== null);
    aisles.push({
      index: r,
      label: row.label,
      z: r === 0 ? z - entranceGap * 0.45 : z - aisleGap / 2,
      x0: DIM.sideMargin * 0.4,
      x1: width - DIM.sideMargin * 0.4,
      shelves: rowShelves,
      occupancy: sumOccupancy(rowShelves),
    });
    z += depthA + depthB + aisleGap;
  });
  const depth = dims?.lengthMm ? dims.lengthMm * MM : z - aisleGap + DIM.backMargin;

  const equipment = room.equipment.map((e) => placeEquipment(e, width, depth, height));
  const entrance = room.equipment.find((e) => e.kind === "EMERGENCY_EXIT" && e.z <= 0.02);
  const doorX = (entrance ? entrance.x : 0.5) * width;

  const layout: RoomLayout = {
    width, depth, height, shelves, slots, boxes, aisles, equipment, doorX, waypoints: [], entranceGap, aisleGap,
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
      title: aisle.label,
      subtitle: `Travées ${codes} · ${percent(aisle.occupancy)} % occupé`,
      camera: [W - 0.55, 1.7, aisle.z],
      target: [0.6, 0.95, aisle.z + 0.9],
      aisleIndex: aisle.index,
    });
    for (const ls of l.shelves.filter((s) => s.row === aisle.index)) {
      const cx = (ls.x0 + ls.x1) / 2;
      const f = ls.shelf.furniture;
      const where = f?.aisleCode ? ` · épi ${f.aisleCode}${f.aisleOrder ? `, rang ${f.aisleOrder}` : ""}${f.aisleSide ? `, face ${f.aisleSide}` : ""}` : "";
      // Face B : on se place dans l'allée de derrière (jusqu'à la rangée suivante ou au mur du fond).
      const back = ls.zFront + ls.depth;
      const next = l.shelves.filter((o) => o.row === aisle.index + 1 && o.facing === 1).map((o) => o.zFront);
      const behind = Math.max(0.6, (next.length ? Math.min(...next) : l.depth) - back - 0.25);
      const front = Math.max(0.5, (aisle.index === 0 ? l.entranceGap : l.aisleGap) - 0.25);
      wps.push({
        kind: "shelf",
        title: `Travée ${ls.shelf.node.code}${ls.shelf.node.label ? ` — ${ls.shelf.node.label}` : ""}`,
        subtitle: `${ls.shelf.racks.length} tablettes · ${ls.shelf.occupancy.boxes} boîtes · ${linearText(ls.shelf.occupancy)} · ${percent(ls.shelf.occupancy)} %${where}`,
        camera: ls.facing === 1 ? [cx - 0.35, 1.45, ls.zFront - front] : [cx + 0.35, 1.45, back + behind],
        target: ls.facing === 1 ? [cx, ls.height * 0.48, ls.zFront + 0.1] : [cx, ls.height * 0.48, back - 0.1],
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
    subtitle: `${room.occupancy.boxes} boîtes · ${linearText(room.occupancy)} · encore ${ml(room.occupancy.placeableMm)} rangeables · ${room.boxesOnLoan} en prêt`,
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
