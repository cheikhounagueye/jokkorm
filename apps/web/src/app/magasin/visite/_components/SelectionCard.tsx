"use client";

import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import { Alert, Badge, Card, CONTAINER_STATE, IconTile, StatusBadge } from "@/components/ui";
import { cm } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { LinkButton } from "../../_components/LinkButton";
import { SpaceGauge } from "../../_components/SpaceGauge";
import { spineLabel, useBoxFormats } from "../../_components/useBoxFormats";
import type { LayoutSlot } from "./roomLayout";
import { boxFinish, EQUIPMENT_ICON, EQUIPMENT_NAME, type ScenePick } from "./sceneShared";

const WALL_NAME = { west: "mur ouest", east: "mur est", front: "mur d'entrée", back: "mur du fond" } as const;

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", gap: 12, fontSize: 12, padding: "6px 0", borderBottom: "1px solid var(--border-subtle)" }}>
      <span style={{ color: "var(--text-tertiary)" }}>{label}</span>
      <span style={{ fontWeight: 500, textAlign: "right" }}>{children}</span>
    </div>
  );
}

/**
 * Tablette entière à l'échelle (pattern 10) : la marge de manipulation est celle de la
 * tablette, pas de l'emplacement. L'emplacement ou la boîte choisie ressort en surbrillance.
 * Hors archiviste, les boîtes restent anonymes même au survol (pattern 08).
 */
function TabletteGauge({ slot, marginPercent, revealed, isActive }: {
  slot: LayoutSlot; marginPercent: number; revealed: boolean;
  isActive: (boxId: string, positionId: string) => boolean;
}) {
  const { fitsIn } = useBoxFormats();
  const occ = slot.rack.occupancy;
  const segments = slot.rack.positions.flatMap((p) => p.boxes.map((b) => ({
    key: b.id,
    spineMm: b.spineMm,
    title: revealed ? (b.label ?? b.barcode ?? "Boîte sans libellé") : "Boîte d'archives",
    onLoan: b.onLoan,
    active: isActive(b.id, p.node.id),
  })));
  return (
    <div>
      <div style={{ fontSize: 11, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.05em", fontWeight: 600, marginBottom: 6 }}>
        Tablette {slot.rack.node.code}
      </div>
      <SpaceGauge lengthMm={occ.lengthMm} usedMm={occ.usedMm} placeableMm={occ.placeableMm}
        marginPercent={marginPercent} segments={segments} fits={fitsIn(occ.placeableMm)} />
    </div>
  );
}

export function SelectionCard({ pick, revealed, marginPercent, onClose }: {
  pick: ScenePick; revealed: boolean; marginPercent: number; onClose: () => void;
}) {
  const close = (
    <button type="button" onClick={onClose} title="Fermer"
      style={{ background: "none", border: "none", color: "var(--text-tertiary)", padding: 4 }}>
      <Icons.close size={16} weight="bold" />
    </button>
  );

  if (pick.kind === "equipment") {
    const e = pick.item.equipment;
    return (
      <Card accent={e.inspectionOverdue ? "danger" : undefined}>
        <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 10 }}>
          <IconTile name={EQUIPMENT_ICON[e.kind]} tone={e.kind === "FIRE_EXTINGUISHER" ? "danger" : e.kind === "EMERGENCY_EXIT" ? "success" : "info"} size={40} />
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 11, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.05em", fontWeight: 600 }}>{EQUIPMENT_NAME[e.kind]}</div>
            <div style={{ fontWeight: 600 }}>{e.label ?? EQUIPMENT_NAME[e.kind]}</div>
          </div>
          {close}
        </div>
        <Row label="Emplacement">{pick.item.wall ? WALL_NAME[pick.item.wall] : e.ceilingMounted ? "plafond" : "dans la salle"}</Row>
        <Row label="Dernier contrôle">{e.lastInspectionOn ? new Date(e.lastInspectionOn).toLocaleDateString("fr-FR") : "—"}</Row>
        <Row label="Prochain contrôle">{e.nextInspectionOn ? new Date(e.nextInspectionOn).toLocaleDateString("fr-FR") : "—"}</Row>
        {e.inspectionOverdue && (
          <div style={{ marginTop: 10 }}>
            <Alert tone="danger" title="Contrôle réglementaire dépassé">Planifie la vérification de cet équipement.</Alert>
          </div>
        )}
      </Card>
    );
  }

  const slot = pick.kind === "box" ? pick.item.slot : pick.item;
  const where = `${slot.shelf.node.code} · tablette ${slot.rack.node.code} · emplacement ${slot.position.node.code}`;
  const nodeLink = `/magasin?node=${slot.position.node.id}`;

  if (pick.kind === "slot") {
    return (
      <Card>
        <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 12 }}>
          <IconTile name="position" tone="success" size={40} />
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 11, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.05em", fontWeight: 600 }}>Emplacement</div>
            <div style={{ fontWeight: 600 }}>{where}</div>
          </div>
          {close}
        </div>
        <TabletteGauge slot={slot} marginPercent={marginPercent} revealed={revealed}
          isActive={(_, positionId) => positionId === slot.position.node.id} />
        <div style={{ marginTop: 10 }}>
          <Row label="Boîtes à cet emplacement">{slot.position.occupancy.boxes}</Row>
          {slot.rack.positions.length > 1 && (
            <Row label="Encore rangeable ici">{cm(slot.position.occupancy.placeableMm)}</Row>
          )}
        </div>
        <div style={{ marginTop: 12 }}>
          <LinkButton href={nodeLink} icon={<Icons.view size={14} />}>Ouvrir l&apos;emplacement</LinkButton>
        </div>
      </Card>
    );
  }

  const b = pick.item.box;
  return (
    <Card>
      <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 10 }}>
        <ArchiveBoxIso size={52} finish={boxFinish(b)} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 4 }}>
            <StatusBadge meta={CONTAINER_STATE[b.state]} size="sm" />
            {b.onLoan && <Badge tone={b.overdue ? "danger" : "info"} size="sm">{b.overdue ? "Prêt en retard" : "En prêt"}</Badge>}
          </div>
          <div style={{ fontWeight: 600, fontSize: 13, overflow: "hidden", textOverflow: "ellipsis" }}>
            {revealed ? (b.label ?? "Boîte sans libellé") : "Boîte d'archives"}
          </div>
        </div>
        {close}
      </div>
      {revealed && b.barcode && <Row label="Code-barres"><code>{b.barcode}</code></Row>}
      <Row label="Rangée à">{where}</Row>
      <Row label="Format">{spineLabel(b.spineMm)}</Row>
      {b.onLoan && b.expectedReturnAt && (
        <Row label="Retour prévu">{new Date(b.expectedReturnAt).toLocaleDateString("fr-FR")}</Row>
      )}
      <div style={{ marginTop: 12 }}>
        <TabletteGauge slot={slot} marginPercent={marginPercent} revealed={revealed}
          isActive={(boxId) => boxId === b.id} />
      </div>
      {!revealed && (
        <p style={{ fontSize: 11, color: "var(--text-tertiary)", marginTop: 8 }}>
          Libellé et code-barres réservés aux archivistes.
        </p>
      )}
      <div style={{ marginTop: 12 }}>
        <LinkButton href={nodeLink} icon={<Icons.view size={14} />}>Voir sur la tablette</LinkButton>
      </div>
    </Card>
  );
}
