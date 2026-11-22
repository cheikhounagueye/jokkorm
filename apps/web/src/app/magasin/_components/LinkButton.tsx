"use client";

import type { ReactNode } from "react";

/** Lien externe au look de Button (un <button> ne peut pas être imbriqué dans un <a>). */
export function LinkButton({
  href,
  icon,
  children,
  variant = "secondary",
  newTab,
}: {
  href: string;
  icon?: ReactNode;
  children: ReactNode;
  variant?: "primary" | "secondary";
  newTab?: boolean;
}) {
  const primary = variant === "primary";
  return (
    <a
      href={href}
      target={newTab ? "_blank" : undefined}
      rel={newTab ? "noreferrer" : undefined}
      style={{
        display: "inline-flex", alignItems: "center", gap: 6,
        padding: "6px 14px", fontSize: 13, fontWeight: 500,
        borderRadius: "var(--radius-md)",
        background: primary ? "var(--brand-500)" : "var(--surface-card)",
        color: primary ? "var(--text-on-brand)" : "var(--text-primary)",
        border: `1px solid ${primary ? "var(--brand-500)" : "var(--border-strong)"}`,
      }}
    >
      {icon}
      {children}
    </a>
  );
}
