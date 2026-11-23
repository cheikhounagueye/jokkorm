"use client";

import { useEffect, useRef, useState } from "react";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import { CSS2DObject, CSS2DRenderer } from "three/examples/jsm/renderers/CSS2DRenderer.js";
import { BOX_FINISHES } from "@/components/illustrations/ArchiveBoxIso";
import { Alert } from "@/components/ui";
import type { EquipmentKind } from "@/lib/api";
import {
  DIM,
  occupancyColor,
  percent,
  type LayoutBox,
  type LayoutEquipment,
  type RoomLayout,
  type Waypoint,
} from "./roomLayout";
import { boxFinish, EQUIPMENT_NAME, type ColorMode, type ScenePick } from "./sceneShared";

interface SceneApi {
  setColorMode(mode: ColorMode): void;
  flyTo(wp: Waypoint, instant: boolean): void;
  setSelectedBox(id: string | null): void;
  setEquipmentLabels(all: boolean): void;
  setDark(dark: boolean): void;
  dispose(): void;
}

export default function RoomScene({
  layout,
  colorMode,
  waypoint,
  showAllEquipmentLabels,
  selectedBoxId,
  dark,
  onPick,
}: {
  layout: RoomLayout;
  colorMode: ColorMode;
  waypoint: Waypoint | null;
  showAllEquipmentLabels: boolean;
  selectedBoxId: string | null;
  dark: boolean;
  onPick: (pick: ScenePick | null) => void;
}) {
  const hostRef = useRef<HTMLDivElement>(null);
  const apiRef = useRef<SceneApi | null>(null);
  const onPickRef = useRef(onPick);
  const firstFlight = useRef(true);
  const [error, setError] = useState<string | null>(null);

  onPickRef.current = onPick;

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    let api: SceneApi;
    try {
      api = buildScene(host, layout, (p) => onPickRef.current(p));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      return;
    }
    apiRef.current = api;
    firstFlight.current = true;
    return () => {
      api.dispose();
      apiRef.current = null;
    };
  }, [layout]);

  useEffect(() => { apiRef.current?.setColorMode(colorMode); }, [colorMode, layout]);
  useEffect(() => { apiRef.current?.setSelectedBox(selectedBoxId); }, [selectedBoxId, layout]);
  useEffect(() => { apiRef.current?.setEquipmentLabels(showAllEquipmentLabels); }, [showAllEquipmentLabels, layout]);
  useEffect(() => { apiRef.current?.setDark(dark); }, [dark, layout]);
  useEffect(() => {
    if (!waypoint || !apiRef.current) return;
    apiRef.current.flyTo(waypoint, firstFlight.current);
    firstFlight.current = false;
  }, [waypoint, layout]);

  if (error) {
    return (
      <div style={{ padding: 24 }}>
        <Alert tone="danger" title="Affichage 3D impossible">
          Ce navigateur n&apos;a pas pu créer de contexte WebGL ({error}). Active l&apos;accélération
          matérielle ou utilise un navigateur récent.
        </Alert>
      </div>
    );
  }
  return <div ref={hostRef} style={{ position: "relative", width: "100%", height: "100%", overflow: "hidden" }} />;
}

// ─── Construction de la scène ──────────────────────────────────────────────

function buildScene(host: HTMLDivElement, layout: RoomLayout, emitPick: (p: ScenePick | null) => void): SceneApi {
  const w0 = host.clientWidth || 800;
  const h0 = host.clientHeight || 560;

  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(w0, h0);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.domElement.style.display = "block";
  host.appendChild(renderer.domElement);

  const labelRenderer = new CSS2DRenderer();
  labelRenderer.setSize(w0, h0);
  Object.assign(labelRenderer.domElement.style, { position: "absolute", inset: "0", pointerEvents: "none" });
  host.appendChild(labelRenderer.domElement);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(55, w0 / h0, 0.05, 250);
  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.maxPolarAngle = Math.PI / 2 - 0.03;
  controls.minDistance = 0.6;
  controls.maxDistance = 70;
  controls.screenSpacePanning = true;

  const disposables: { dispose(): void }[] = [];
  function track<T extends { dispose(): void }>(x: T): T {
    disposables.push(x);
    return x;
  }

  const W = layout.width;
  const D = layout.depth;
  const H = layout.height;

  // Lumières : ciel + « soleil » portant les ombres, néons d'allée émissifs.
  const hemi = new THREE.HemisphereLight(0xffffff, 0xd8cdb8, 1.15);
  scene.add(hemi);
  const sun = new THREE.DirectionalLight(0xffffff, 1.35);
  sun.position.set(W * 0.25, H * 4.2, -D * 0.35);
  sun.target.position.set(W / 2, 0, D / 2);
  sun.castShadow = true;
  sun.shadow.mapSize.set(2048, 2048);
  const ext = Math.max(W, D) * 0.8;
  Object.assign(sun.shadow.camera, { left: -ext, right: ext, top: ext, bottom: -ext, near: 0.5, far: 80 });
  sun.shadow.bias = -0.0006;
  scene.add(sun, sun.target);

  const mat = {
    floor: track(new THREE.MeshStandardMaterial({ color: 0xdcd6ca, roughness: 0.95 })),
    ground: track(new THREE.MeshStandardMaterial({ color: 0xc4bdaf, roughness: 1 })),
    wall: track(new THREE.MeshStandardMaterial({ color: 0xefeae0, roughness: 0.9, transparent: true, opacity: 0.55, depthWrite: false, side: THREE.DoubleSide })),
    wallFront: track(new THREE.MeshStandardMaterial({ color: 0xefeae0, roughness: 0.9, transparent: true, opacity: 0.14, depthWrite: false, side: THREE.DoubleSide })),
    metal: track(new THREE.MeshStandardMaterial({ color: 0x7a8597, roughness: 0.42, metalness: 0.55 })),
    board: track(new THREE.MeshStandardMaterial({ color: 0xb3bac5, roughness: 0.5, metalness: 0.3 })),
    stripe: track(new THREE.MeshStandardMaterial({ color: 0xf2c230, roughness: 0.6 })),
    lamp: track(new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0xfff6df, emissiveIntensity: 1 })),
    box: track(new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.82 })),
    ghost: track(new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.6, transparent: true, opacity: 0.3, depthWrite: false })),
    sticker: track(new THREE.MeshStandardMaterial({ color: 0xf8f4ea, roughness: 0.9 })),
    slot: track(new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.6 })),
    red: track(new THREE.MeshStandardMaterial({ color: 0xd32f2f, roughness: 0.35, metalness: 0.2 })),
    black: track(new THREE.MeshStandardMaterial({ color: 0x1f2937, roughness: 0.5 })),
    white: track(new THREE.MeshStandardMaterial({ color: 0xf5f5f4, roughness: 0.6 })),
    door: track(new THREE.MeshStandardMaterial({ color: 0x8d9b8f, roughness: 0.6 })),
    exitSign: track(new THREE.MeshStandardMaterial({ color: 0x16a34a, emissive: 0x16a34a, emissiveIntensity: 0.9 })),
    led: track(new THREE.MeshStandardMaterial({ color: 0xef4444, emissive: 0xef4444, emissiveIntensity: 1.2 })),
    screen: track(new THREE.MeshStandardMaterial({ color: 0x0f172a, emissive: 0x38bdf8, emissiveIntensity: 0.25 })),
    redFloor: track(new THREE.MeshStandardMaterial({ color: 0xdc2626, roughness: 0.8 })),
    ring: track(new THREE.MeshBasicMaterial({ color: 0xef4444, transparent: true, opacity: 0.7, side: THREE.DoubleSide })),
  };

  const unitBox = track(new THREE.BoxGeometry(1, 1, 1));
  const plane = track(new THREE.PlaneGeometry(1, 1));

  function block(material: THREE.Material, size: [number, number, number], pos: [number, number, number], shadows = true) {
    const m = new THREE.Mesh(unitBox, material);
    m.scale.set(...size);
    m.position.set(...pos);
    m.castShadow = shadows;
    m.receiveShadow = shadows;
    return m;
  }

  function flat(material: THREE.Material, sx: number, sz: number, pos: [number, number, number]) {
    const m = new THREE.Mesh(plane, material);
    m.rotation.x = -Math.PI / 2;
    m.scale.set(sx, sz, 1);
    m.position.set(...pos);
    m.receiveShadow = true;
    return m;
  }

  // Sol, extérieur, murs (porte d'entrée découpée dans le mur avant).
  scene.add(flat(mat.ground, W * 5, D * 5, [W / 2, -0.02, D / 2]));
  scene.add(flat(mat.floor, W, D, [W / 2, 0, D / 2]));
  const wallT = 0.12;
  scene.add(block(mat.wall, [W + 2 * wallT, H, wallT], [W / 2, H / 2, D + wallT / 2], false));
  scene.add(block(mat.wall, [wallT, H, D], [-wallT / 2, H / 2, D / 2], false));
  scene.add(block(mat.wall, [wallT, H, D], [W + wallT / 2, H / 2, D / 2], false));
  const doorW = 1.5;
  const leftW = Math.max(0, layout.doorX - doorW / 2);
  const rightW = Math.max(0, W - (layout.doorX + doorW / 2));
  if (leftW > 0.01) scene.add(block(mat.wallFront, [leftW, H, wallT], [leftW / 2, H / 2, -wallT / 2], false));
  if (rightW > 0.01) scene.add(block(mat.wallFront, [rightW, H, wallT], [W - rightW / 2, H / 2, -wallT / 2], false));
  scene.add(block(mat.wallFront, [doorW, H - 2.25, wallT], [layout.doorX, 2.25 + (H - 2.25) / 2, -wallT / 2], false));

  // Allées : marquage jaune au sol devant chaque rangée, néon au plafond, nom d'allée.
  // Les néons sont masqués quand la caméra passe au-dessus du plafond (vues aériennes).
  const lamps = new THREE.Group();
  scene.add(lamps);
  for (const aisle of layout.aisles) {
    const row = layout.shelves.filter((s) => s.row === aisle.index);
    if (row.length === 0) continue;
    const x0 = Math.min(...row.map((s) => s.x0)) - 0.2;
    const x1 = Math.max(...row.map((s) => s.x1)) + 0.2;
    const zf = row[0].zFront;
    scene.add(flat(mat.stripe, x1 - x0, 0.07, [(x0 + x1) / 2, 0.004, zf - 0.22]));
    lamps.add(block(mat.lamp, [Math.min(W * 0.7, x1 - x0), 0.04, 0.12], [(x0 + x1) / 2, H - 0.06, aisle.z], false));
    const label = new CSS2DObject(aisleLabel(`Allée ${aisle.index + 1}`, `${percent(aisle.occupancy)} %`,
      occupancyColor(aisle.occupancy.rate, aisle.occupancy.boxes)));
    label.position.set(Math.max(aisle.x0, aisle.x1) - 0.15, 0.05, aisle.z);
    scene.add(label);
  }

  // Rayonnages : montants, tablettes, enseigne de travée.
  for (const ls of layout.shelves) {
    const w = ls.x1 - ls.x0;
    const cz = ls.zFront + DIM.unitDepth / 2;
    const n = Math.max(1, Math.round(w / 1.2));
    for (let i = 0; i <= n; i++) {
      const ux = ls.x0 + DIM.uprightW / 2 + ((w - DIM.uprightW) * i) / n;
      scene.add(block(mat.metal, [DIM.uprightW, ls.height, DIM.unitDepth], [ux, ls.height / 2, cz]));
    }
    for (const y of [...ls.boardYs, ls.height - 0.02]) {
      scene.add(block(mat.board, [w, DIM.boardT, DIM.unitDepth], [ls.x0 + w / 2, y, cz]));
    }
    scene.add(block(mat.metal, [w, 0.03, 0.02], [ls.x0 + w / 2, ls.height * 0.55, ls.zFront + DIM.unitDepth - 0.01]));
    const sign = new CSS2DObject(shelfSign(ls.shelf.node.code, ls.shelf.node.label,
      percent(ls.shelf.occupancy), occupancyColor(ls.shelf.occupancy.rate, ls.shelf.occupancy.boxes)));
    sign.position.set(ls.x0 + w / 2, ls.height + 0.3, ls.zFront);
    scene.add(sign);
  }

  // Plaques d'emplacement (mode occupation + sélection d'un emplacement vide).
  const slotMesh = new THREE.InstancedMesh(unitBox, mat.slot, Math.max(1, layout.slots.length));
  slotMesh.count = layout.slots.length;
  const m4 = new THREE.Matrix4();
  const q = new THREE.Quaternion();
  const s = new THREE.Vector3();
  const p = new THREE.Vector3();
  const color = new THREE.Color();
  layout.slots.forEach((slot, i) => {
    p.set((slot.x0 + slot.x1) / 2, slot.y + DIM.boardT / 2 + 0.005, slot.zFront + DIM.unitDepth / 2);
    s.set(slot.x1 - slot.x0 - 0.012, 0.01, DIM.unitDepth * 0.96);
    m4.compose(p, q, s);
    slotMesh.setMatrixAt(i, m4);
    const o = slot.position.occupancy;
    slotMesh.setColorAt(i, color.set(occupancyColor(o.rate, o.boxes)));
  });
  scene.add(slotMesh);

  // Boîtes : pleines et « fantômes » (en prêt), en instanciation.
  const solid = layout.boxes.filter((b) => !b.box.onLoan);
  const ghost = layout.boxes.filter((b) => b.box.onLoan);
  const boxGeo = track(new THREE.BoxGeometry(DIM.boxW, DIM.boxH, DIM.boxD));
  const solidMesh = new THREE.InstancedMesh(boxGeo, mat.box, Math.max(1, solid.length));
  const ghostMesh = new THREE.InstancedMesh(boxGeo, mat.ghost, Math.max(1, ghost.length));
  const stickerMesh = new THREE.InstancedMesh(plane, mat.sticker, Math.max(1, solid.length));
  solidMesh.count = solid.length;
  ghostMesh.count = ghost.length;
  stickerMesh.count = solid.length;
  solidMesh.castShadow = true;
  solidMesh.receiveShadow = true;
  // Chaque boîte est mise à l'échelle de son dos réel (dos 8, 10, 15, 20… : pattern 10).
  const sx = new THREE.Vector3();
  const widthScale = (b: LayoutBox) => sx.set(b.width / DIM.boxW, 1, 1);
  const stickerQ = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI);
  solid.forEach((b, i) => {
    solidMesh.setMatrixAt(i, m4.compose(p.set(...b.center), q.identity(), widthScale(b)));
    p.set(b.center[0], b.center[1] + 0.035, b.center[2] - DIM.boxD / 2 - 0.002);
    stickerMesh.setMatrixAt(i, m4.compose(p, stickerQ, s.set(Math.min(0.058, b.width * 0.64), 0.1, 1)));
  });
  ghost.forEach((b, i) => ghostMesh.setMatrixAt(i, m4.compose(p.set(...b.center), q.identity(), widthScale(b))));
  scene.add(solidMesh, ghostMesh, stickerMesh);

  const outline = new THREE.LineSegments(
    track(new THREE.EdgesGeometry(track(new THREE.BoxGeometry(DIM.boxW * 1.18, DIM.boxH * 1.08, DIM.boxD * 1.06)))),
    track(new THREE.LineBasicMaterial({ color: 0x2563eb, transparent: true })));
  outline.visible = false;
  scene.add(outline);

  function applyColors(mode: ColorMode) {
    const white = new THREE.Color(0xffffff);
    const paint = (mesh: THREE.InstancedMesh, list: LayoutBox[]) => {
      list.forEach((b, i) => {
        if (mode === "real") {
          color.set(BOX_FINISHES[boxFinish(b.box)].left);
        } else {
          const o = b.slot.position.occupancy;
          color.set(occupancyColor(o.rate, o.boxes)).lerp(white, 0.2);
        }
        mesh.setColorAt(i, color);
      });
      if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
    };
    paint(solidMesh, solid);
    paint(ghostMesh, ghost);
    slotMesh.visible = mode === "occupancy";
    stickerMesh.visible = mode === "real";
  }
  applyColors("real");

  // Équipements.
  const geo = {
    extBody: track(new THREE.CylinderGeometry(0.085, 0.085, 0.5, 24)),
    extHead: track(new THREE.CylinderGeometry(0.035, 0.05, 0.08, 16)),
    detector: track(new THREE.CylinderGeometry(0.075, 0.08, 0.035, 24)),
    led: track(new THREE.SphereGeometry(0.012, 8, 8)),
    ring: track(new THREE.RingGeometry(0.3, 0.38, 40)),
  };
  const equipmentRoot = new THREE.Group();
  const equipmentLabels: { obj: CSS2DObject; overdue: boolean }[] = [];
  const rings: THREE.Mesh[] = [];

  for (const le of layout.equipment) {
    const g = new THREE.Group();
    g.position.set(...le.position);
    g.rotation.y = le.rotationY;
    g.userData.pick = le;
    const kind = le.equipment.kind;
    let labelY = 1.5;

    if (kind === "FIRE_EXTINGUISHER") {
      const body = new THREE.Mesh(geo.extBody, mat.red);
      body.position.set(0, 0.42, 0.12);
      body.castShadow = true;
      const head = new THREE.Mesh(geo.extHead, mat.black);
      head.position.set(0, 0.71, 0.12);
      g.add(body, head, block(mat.black, [0.16, 0.025, 0.04], [0, 0.73, 0.12], false));
      g.add(block(mat.red, [0.22, 0.22, 0.01], [0, 1.25, 0.01], false));
      g.add(flat(mat.redFloor, 0.5, 0.5, [0, 0.003, 0.3]));
      labelY = 1.5;
    } else if (kind === "EMERGENCY_EXIT") {
      if (le.wall !== "front") {
        g.add(block(mat.door, [1.0, 2.1, 0.05], [0, 1.05, 0.03]));
      }
      g.add(block(mat.metal, [0.06, 2.15, 0.07], [-0.55, 1.075, 0.04], false));
      g.add(block(mat.metal, [0.06, 2.15, 0.07], [0.55, 1.075, 0.04], false));
      g.add(block(mat.exitSign, [0.46, 0.17, 0.04], [0, 2.35, 0.08], false));
      labelY = 2.7;
    } else if (kind === "SMOKE_DETECTOR") {
      const det = new THREE.Mesh(geo.detector, mat.white);
      const led = new THREE.Mesh(geo.led, mat.led);
      led.position.set(0.035, -0.02, 0);
      g.add(det, led);
      labelY = -0.25;
    } else {
      g.add(block(mat.white, [0.14, 0.2, 0.04], [0, 0, 0.03], false));
      g.add(block(mat.screen, [0.1, 0.07, 0.005], [0, 0.03, 0.052], false));
      labelY = 0.3;
    }

    if (le.equipment.inspectionOverdue) {
      const ring = new THREE.Mesh(geo.ring, mat.ring);
      ring.rotation.x = -Math.PI / 2;
      ring.position.set(le.position[0], 0.012, le.position[2]);
      scene.add(ring);
      rings.push(ring);
    }

    const label = new CSS2DObject(equipmentTag(le.equipment.label ?? EQUIPMENT_NAME[kind], kind, le.equipment.inspectionOverdue));
    label.position.set(0, labelY, 0.15);
    g.add(label);
    equipmentLabels.push({ obj: label, overdue: le.equipment.inspectionOverdue });
    equipmentRoot.add(g);
  }
  scene.add(equipmentRoot);

  // Interaction : clic (sans glisser) = sélection, survol = curseur main.
  const raycaster = new THREE.Raycaster();
  const ndc = new THREE.Vector2();
  function pickAt(clientX: number, clientY: number): ScenePick | null {
    const rect = renderer.domElement.getBoundingClientRect();
    ndc.set(((clientX - rect.left) / rect.width) * 2 - 1, -((clientY - rect.top) / rect.height) * 2 + 1);
    raycaster.setFromCamera(ndc, camera);
    const hits = raycaster.intersectObjects([solidMesh, ghostMesh, equipmentRoot, slotMesh], true);
    for (const h of hits) {
      if (h.object === solidMesh && h.instanceId != null) return { kind: "box", item: solid[h.instanceId] };
      if (h.object === ghostMesh && h.instanceId != null) return { kind: "box", item: ghost[h.instanceId] };
      if (h.object === slotMesh && h.instanceId != null) return { kind: "slot", item: layout.slots[h.instanceId] };
      let o: THREE.Object3D | null = h.object;
      while (o && !o.userData.pick) o = o.parent;
      if (o?.userData.pick) return { kind: "equipment", item: o.userData.pick as LayoutEquipment };
    }
    return null;
  }

  let downAt: [number, number] | null = null;
  const onDown = (e: PointerEvent) => { downAt = [e.clientX, e.clientY]; };
  const onUp = (e: PointerEvent) => {
    if (!downAt) return;
    const moved = Math.hypot(e.clientX - downAt[0], e.clientY - downAt[1]);
    downAt = null;
    if (moved < 5) emitPick(pickAt(e.clientX, e.clientY));
  };
  let hoverQueued = false;
  const onMove = (e: PointerEvent) => {
    if (hoverQueued || downAt) return;
    hoverQueued = true;
    requestAnimationFrame(() => {
      hoverQueued = false;
      renderer.domElement.style.cursor = pickAt(e.clientX, e.clientY) ? "pointer" : "grab";
    });
  };
  renderer.domElement.addEventListener("pointerdown", onDown);
  renderer.domElement.addEventListener("pointerup", onUp);
  renderer.domElement.addEventListener("pointermove", onMove);

  // Déplacements de caméra : arc au-dessus des rayonnages pour ne pas les traverser.
  let tween: { fromPos: THREE.Vector3; fromTgt: THREE.Vector3; toPos: THREE.Vector3; toTgt: THREE.Vector3; start: number; dur: number; lift: number } | null = null;

  function flyTo(wp: Waypoint, instant: boolean) {
    const toPos = new THREE.Vector3(...wp.camera);
    const toTgt = new THREE.Vector3(...wp.target);
    if (instant) {
      camera.position.copy(toPos);
      controls.target.copy(toTgt);
      controls.update();
      tween = null;
      return;
    }
    const dist = camera.position.distanceTo(toPos);
    tween = {
      fromPos: camera.position.clone(), fromTgt: controls.target.clone(), toPos, toTgt,
      start: performance.now(), dur: 1500, lift: dist > 3 ? Math.min(4, dist * 0.35) : 0,
    };
    controls.enabled = false;
  }

  const ro = new ResizeObserver(() => {
    const w = host.clientWidth;
    const h = host.clientHeight;
    if (w === 0 || h === 0) return;
    renderer.setSize(w, h);
    labelRenderer.setSize(w, h);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  });
  ro.observe(host);

  let raf = 0;
  const loop = (now: number) => {
    raf = requestAnimationFrame(loop);
    if (tween) {
      const t = Math.min(1, (now - tween.start) / tween.dur);
      const e = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
      camera.position.lerpVectors(tween.fromPos, tween.toPos, e);
      camera.position.y += Math.sin(Math.PI * e) * tween.lift;
      controls.target.lerpVectors(tween.fromTgt, tween.toTgt, e);
      if (t >= 1) {
        tween = null;
        controls.enabled = true;
      }
    }
    lamps.visible = camera.position.y < H - 0.15;
    const pulse = 1 + 0.18 * Math.sin(now / 260);
    for (const r of rings) r.scale.set(pulse, pulse, pulse);
    if (outline.visible) (outline.material as THREE.LineBasicMaterial).opacity = 0.6 + 0.4 * Math.sin(now / 200);
    controls.update();
    renderer.render(scene, camera);
    labelRenderer.render(scene, camera);
  };
  raf = requestAnimationFrame(loop);

  function setDark(dark: boolean) {
    const bg = new THREE.Color(dark ? 0x0f1420 : 0xf1ede5);
    scene.background = bg;
    scene.fog = new THREE.Fog(bg, 28, 75);
    mat.ground.color.set(dark ? 0x1b2130 : 0xc4bdaf);
    mat.floor.color.set(dark ? 0x3b4252 : 0xdcd6ca);
    mat.wall.color.set(dark ? 0x2a3142 : 0xefeae0);
    mat.wallFront.color.set(dark ? 0x2a3142 : 0xefeae0);
    hemi.intensity = dark ? 0.85 : 1.15;
  }

  return {
    setColorMode: applyColors,
    flyTo,
    setSelectedBox(id) {
      const hit = id ? layout.boxes.find((b) => b.box.id === id) : undefined;
      outline.visible = !!hit;
      if (hit) {
        outline.position.set(...hit.center);
        outline.scale.set(hit.width / DIM.boxW, 1, 1);
      }
    },
    setEquipmentLabels(all) {
      for (const l of equipmentLabels) l.obj.visible = all || l.overdue;
    },
    setDark,
    dispose() {
      cancelAnimationFrame(raf);
      ro.disconnect();
      renderer.domElement.removeEventListener("pointerdown", onDown);
      renderer.domElement.removeEventListener("pointerup", onUp);
      renderer.domElement.removeEventListener("pointermove", onMove);
      controls.dispose();
      scene.traverse((o) => { if (o instanceof CSS2DObject) o.element.remove(); });
      solidMesh.dispose();
      ghostMesh.dispose();
      stickerMesh.dispose();
      slotMesh.dispose();
      disposables.forEach((d) => d.dispose());
      renderer.dispose();
      renderer.forceContextLoss();
      renderer.domElement.remove();
      labelRenderer.domElement.remove();
    },
  };
}

// ─── Étiquettes HTML (texte uniquement : jamais d'innerHTML avec des données) ──

function el(tag: string, style: Partial<CSSStyleDeclaration>, text?: string): HTMLElement {
  const e = document.createElement(tag);
  Object.assign(e.style, style);
  if (text !== undefined) e.textContent = text;
  return e;
}

const PILL: Partial<CSSStyleDeclaration> = {
  display: "inline-flex", alignItems: "center", gap: "6px",
  padding: "4px 9px", borderRadius: "999px",
  background: "rgba(255,255,255,0.94)", color: "#1a1f2e",
  font: "600 11px Inter, system-ui, sans-serif",
  boxShadow: "0 2px 8px rgba(15,23,42,0.18)", whiteSpace: "nowrap",
  border: "1px solid rgba(15,23,42,0.08)",
};

/**
 * CSS2DRenderer réécrit `style.display` de l'élément racine à chaque image : on pose
 * donc la mise en forme (inline-flex, gap) sur un enfant, jamais sur la racine.
 */
function anchored(pill: HTMLElement): HTMLElement {
  const root = document.createElement("div");
  root.append(pill);
  return root;
}

function shelfSign(code: string, label: string | null, rate: number, rateColor: string): HTMLElement {
  const pill = el("div", { ...PILL, padding: "5px 6px 5px 10px", fontSize: "12px" });
  pill.append(el("span", { fontWeight: "700" }, code));
  if (label) pill.append(el("span", { fontWeight: "500", color: "#5c6475" }, label));
  pill.append(el("span", {
    background: rateColor, color: "white", borderRadius: "999px", padding: "1px 7px", fontWeight: "700",
  }, `${rate} %`));
  return anchored(pill);
}

function aisleLabel(name: string, rate: string, rateColor: string): HTMLElement {
  const pill = el("div", { ...PILL, background: "rgba(26,31,46,0.82)", color: "white", letterSpacing: "0.04em" });
  pill.append(el("span", {}, name.toUpperCase()));
  pill.append(el("span", { color: rateColor }, rate));
  return anchored(pill);
}

const KIND_COLOR: Record<EquipmentKind, string> = {
  FIRE_EXTINGUISHER: "#dc2626",
  SMOKE_DETECTOR: "#64748b",
  EMERGENCY_EXIT: "#16a34a",
  THERMO_HYGROMETER: "#0284c7",
};

function equipmentTag(text: string, kind: EquipmentKind, overdue: boolean): HTMLElement {
  const pill = el("div", { ...PILL, fontSize: "10px", padding: "3px 8px",
    border: overdue ? "1.5px solid #dc2626" : PILL.border });
  pill.append(el("span", { width: "8px", height: "8px", borderRadius: "999px", background: KIND_COLOR[kind] }));
  pill.append(el("span", {}, text));
  if (overdue) pill.append(el("span", { color: "#dc2626", fontWeight: "700" }, "contrôle en retard"));
  return anchored(pill);
}
