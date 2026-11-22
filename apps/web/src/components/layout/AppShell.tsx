"use client";

import {
  Archive,
  Boxes,
  FileText,
  LayoutDashboard,
  Package2,
  Send,
  Warehouse,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { TopBar } from "./TopBar";

type NavItem = {
  href: string;
  label: string;
  icon: ReactNode;
  section?: string;
};

const NAV_ITEMS: NavItem[] = [
  { href: "/",             label: "Tableau de bord", icon: <LayoutDashboard size={18} />, section: "Accueil" },
  { href: "/demandes",     label: "Demandes",        icon: <Send size={18} />,            section: "Travail" },
  { href: "/prets",        label: "Prêts",           icon: <FileText size={18} />,        section: "Travail" },
  { href: "/magasin",      label: "Salle d'archives",icon: <Warehouse size={18} />,       section: "Gestion" },
  { href: "/magasin/lots", label: "Lots de boîtes",  icon: <Package2 size={18} />,        section: "Gestion" },
  { href: "/records",      label: "Archives",        icon: <Archive size={18} />,         section: "Référence" },
];

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div style={{
      display: "grid",
      gridTemplateColumns: "var(--sidebar-width) 1fr",
      gridTemplateRows: "var(--topbar-height) 1fr",
      gridTemplateAreas: `
        "sidebar topbar"
        "sidebar main"
      `,
      minHeight: "100vh",
    }}>
      <Sidebar />
      <TopBar />
      <main style={{
        gridArea: "main",
        padding: "28px 40px 48px",
        overflow: "auto",
        background: "var(--surface-page)",
      }}>
        {children}
      </main>
    </div>
  );
}

function Sidebar() {
  const pathname = usePathname();
  const sections = Array.from(new Set(NAV_ITEMS.map((i) => i.section).filter(Boolean))) as string[];

  return (
    <aside style={{
      gridArea: "sidebar",
      background: "var(--surface-card)",
      borderRight: "1px solid var(--border-subtle)",
      padding: "16px 0",
      display: "flex",
      flexDirection: "column",
    }}>
      <Link href="/" style={{
        display: "flex", alignItems: "center", gap: 10,
        padding: "8px 20px 20px",
        textDecoration: "none", color: "var(--text-primary)",
      }}>
        <div style={{
          width: 32, height: 32,
          borderRadius: "var(--radius-md)",
          background: "linear-gradient(135deg, var(--brand-500), var(--brand-700))",
          display: "flex", alignItems: "center", justifyContent: "center",
          color: "white",
        }}>
          <Boxes size={18} />
        </div>
        <div>
          <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: "-0.015em" }}>JokkoRM</div>
          <div style={{ fontSize: 10, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.06em" }}>
            Archivage physique
          </div>
        </div>
      </Link>

      <nav style={{ flex: 1, padding: "0 12px" }}>
        {sections.map((section) => (
          <div key={section} style={{ marginBottom: 20 }}>
            <div style={{
              fontSize: 10, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.08em",
              color: "var(--text-tertiary)", padding: "6px 10px 4px",
            }}>
              {section}
            </div>
            {NAV_ITEMS.filter((i) => i.section === section).map((item) => {
              const active = pathname === item.href || (item.href !== "/" && pathname.startsWith(item.href));
              return (
                <Link key={item.href} href={item.href}
                  style={{
                    display: "flex", alignItems: "center", gap: 10,
                    padding: "7px 10px",
                    marginBottom: 1,
                    borderRadius: "var(--radius-md)",
                    fontSize: 13, fontWeight: active ? 600 : 500,
                    color: active ? "var(--brand-500)" : "var(--text-secondary)",
                    background: active ? "var(--brand-50)" : "transparent",
                    textDecoration: "none",
                    transition: "background 150ms ease, color 150ms ease",
                  }}>
                  {item.icon}
                  {item.label}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      <div style={{ padding: "12px 20px", borderTop: "1px solid var(--border-subtle)" }}>
        <a href="https://github.com/cheikhounagueye/jokkorm" target="_blank" rel="noreferrer"
          style={{ fontSize: 11, color: "var(--text-tertiary)", textDecoration: "none" }}>
          GitHub · AGPL-3.0
        </a>
      </div>
    </aside>
  );
}
