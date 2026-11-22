"use client";

import { Icons, type IconName } from "@/lib/icons";

export type Tone = "brand" | "info" | "success" | "warning" | "danger" | "kraft" | "slate" | "violet";

/** Dégradés « app icon » : clair en haut, saturé en bas. */
const GRADIENTS: Record<Tone, [string, string, string]> = {
  brand:   ["#4c6ef5", "#1e2d5e", "rgba(30, 45, 94, 0.35)"],
  info:    ["#60a5fa", "#2563eb", "rgba(37, 99, 235, 0.35)"],
  success: ["#34d399", "#059669", "rgba(5, 150, 105, 0.35)"],
  warning: ["#fbbf24", "#d97706", "rgba(217, 119, 6, 0.35)"],
  danger:  ["#f87171", "#dc2626", "rgba(220, 38, 38, 0.35)"],
  kraft:   ["#dcb98a", "#9a6a3a", "rgba(140, 90, 44, 0.35)"],
  slate:   ["#94a3b8", "#475569", "rgba(71, 85, 105, 0.35)"],
  violet:  ["#a78bfa", "#7c3aed", "rgba(124, 58, 237, 0.35)"],
};

const SOFT: Record<Tone, { bg: string; fg: string }> = {
  brand:   { bg: "var(--brand-50)",   fg: "var(--brand-500)" },
  info:    { bg: "var(--info-50)",    fg: "var(--info-500)" },
  success: { bg: "var(--success-50)", fg: "var(--success-500)" },
  warning: { bg: "var(--warning-50)", fg: "var(--warning-500)" },
  danger:  { bg: "var(--danger-50)",  fg: "var(--danger-500)" },
  kraft:   { bg: "color-mix(in srgb, var(--kraft-500) 14%, transparent)", fg: "var(--kraft-700)" },
  slate:   { bg: "var(--surface-sunken)", fg: "var(--text-secondary)" },
  violet:  { bg: "color-mix(in srgb, #7c3aed 12%, transparent)", fg: "#7c3aed" },
};

/**
 * Tuile d'icône. `solid` = dégradé + reflet (rendu « app icon », pour les points
 * d'entrée visuels) ; `soft` = fond teinté (pour les listes et zones denses).
 */
export function IconTile({
  name,
  tone = "brand",
  size = 40,
  variant = "solid",
}: {
  name: IconName;
  tone?: Tone;
  size?: number;
  variant?: "solid" | "soft";
}) {
  const IconCmp = Icons[name];
  const iconSize = Math.round(size * 0.55);

  if (variant === "soft") {
    const s = SOFT[tone];
    return (
      <span style={{
        width: size, height: size, flexShrink: 0,
        borderRadius: Math.round(size * 0.26),
        background: s.bg, color: s.fg,
        display: "inline-flex", alignItems: "center", justifyContent: "center",
      }}>
        <IconCmp size={iconSize} weight="duotone" />
      </span>
    );
  }

  const [top, bottom, glow] = GRADIENTS[tone];
  return (
    <span style={{
      width: size, height: size, flexShrink: 0,
      borderRadius: Math.round(size * 0.28),
      background: `linear-gradient(160deg, ${top} 0%, ${bottom} 100%)`,
      boxShadow: `inset 0 1px 0 rgba(255,255,255,0.35), inset 0 -1px 0 rgba(0,0,0,0.12), 0 4px 10px -2px ${glow}`,
      color: "white",
      display: "inline-flex", alignItems: "center", justifyContent: "center",
    }}>
      <IconCmp size={iconSize} weight="duotone" />
    </span>
  );
}
