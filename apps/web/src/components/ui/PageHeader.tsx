"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { Icons, type IconName } from "@/lib/icons";
import { IconTile, type Tone } from "./IconTile";

export type Crumb = { label: string; href?: string };

/**
 * En-tête standard de page : fil d'Ariane, tuile d'icône, titre, sous-titre, actions.
 * Toutes les pages l'utilisent pour garder la même grille verticale.
 */
export function PageHeader({
  icon,
  tone = "brand",
  title,
  subtitle,
  actions,
  crumbs,
}: {
  icon: IconName;
  tone?: Tone;
  title: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  crumbs?: Crumb[];
}) {
  const Chevron = Icons.chevronRight;
  return (
    <header style={{ marginBottom: 24 }}>
      {crumbs && crumbs.length > 0 && (
        <nav style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "var(--text-tertiary)", marginBottom: 12 }}>
          {crumbs.map((c, i) => (
            <span key={i} style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
              {i > 0 && <Chevron size={12} weight="bold" />}
              {c.href
                ? <Link href={c.href} style={{ color: "var(--text-secondary)" }}>{c.label}</Link>
                : <span style={{ color: "var(--text-primary)", fontWeight: 500 }}>{c.label}</span>}
            </span>
          ))}
        </nav>
      )}
      <div style={{ display: "flex", alignItems: "center", gap: 16, flexWrap: "wrap" }}>
        <IconTile name={icon} tone={tone} size={48} />
        <div style={{ flex: 1, minWidth: 220 }}>
          <h1 style={{ fontSize: 22, marginBottom: subtitle ? 2 : 0 }}>{title}</h1>
          {subtitle && <p style={{ fontSize: 13, color: "var(--text-secondary)" }}>{subtitle}</p>}
        </div>
        {actions && <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>{actions}</div>}
      </div>
    </header>
  );
}
