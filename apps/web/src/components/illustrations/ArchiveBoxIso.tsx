/**
 * Boîte d'archives en carton, vue isométrique (SVG pur, sans dépendance).
 * Réutilisée par la bannière d'accueil et la salle d'archives virtuelle.
 */
export type BoxFinish = "kraft" | "sealed" | "loaned" | "empty" | "destroyed" | "stale";

/** Couleurs des faces, partagées avec la salle 3D pour que les boîtes 2D et 3D se ressemblent. */
export const BOX_FINISHES: Record<BoxFinish, { top: string; left: string; right: string; label: string }> = {
  kraft:     { top: "#e9cb9d", left: "#c99b62", right: "#a77942", label: "#fbf6ec" },
  sealed:    { top: "#f5d27a", left: "#d9a531", right: "#b5841c", label: "#fff8e1" },
  loaned:    { top: "#9fb4ff", left: "#6f88e6", right: "#4d66c4", label: "#eef2ff" },
  empty:     { top: "#e7e3da", left: "#cfc8b8", right: "#b3ab99", label: "#f7f5f0" },
  destroyed: { top: "#f3b3b3", left: "#d97a7a", right: "#b85555", label: "#fef2f2" },
  stale:     { top: "#ffd08a", left: "#f0a245", right: "#cc7d22", label: "#fff4e5" },
};

export function ArchiveBoxIso({
  size = 64,
  finish = "kraft",
  title,
}: {
  size?: number;
  finish?: BoxFinish;
  title?: string;
}) {
  const f = BOX_FINISHES[finish];
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" role="img" aria-label={title ?? "Boîte d'archives"}>
      {title && <title>{title}</title>}
      {/* ombre portée */}
      <ellipse cx="50" cy="92" rx="38" ry="6" fill="rgba(15,23,42,0.18)" />
      {/* faces */}
      <polygon points="50,12 90,32 50,52 10,32" fill={f.top} />
      <polygon points="10,32 50,52 50,90 10,70" fill={f.left} />
      <polygon points="50,52 90,32 90,70 50,90" fill={f.right} />
      {/* rabat du couvercle */}
      <polygon points="10,32 50,52 50,57 10,37" fill="rgba(0,0,0,0.08)" />
      <polygon points="50,52 90,32 90,37 50,57" fill="rgba(0,0,0,0.12)" />
      {/* étiquette face avant */}
      <polygon points="17,47 43,60 43,74 17,61" fill={f.label} opacity="0.95" />
      <line x1="21" y1="54" x2="39" y2="63" stroke="rgba(15,23,42,0.35)" strokeWidth="1.6" />
      <line x1="21" y1="59" x2="33" y2="65" stroke="rgba(15,23,42,0.25)" strokeWidth="1.6" />
      {/* poignée face latérale */}
      <polygon points="62,58 78,50 78,55 62,63" fill="rgba(15,23,42,0.35)" />
      {finish === "destroyed" && (
        <line x1="14" y1="36" x2="86" y2="78" stroke="#991b1b" strokeWidth="4" strokeLinecap="round" opacity="0.7" />
      )}
    </svg>
  );
}
