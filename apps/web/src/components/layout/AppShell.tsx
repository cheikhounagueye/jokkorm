"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { IconTile } from "@/components/ui/IconTile";
import { useRole } from "@/app/demandes/_components/useRole";
import { SESSION_STORAGE_KEYS, getImportSummary, getMasterWatch, listPendingConditioning } from "@/lib/api";

/**
 * L'espace ou le rôle a changé dans un autre onglet : cet onglet recharge, pour ne jamais
 * afficher un espace et en interroger un autre. Une fiche de dossier (propre à un espace)
 * renvoie à la liste des archives.
 */
function useFollowSessionChanges() {
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === null || !SESSION_STORAGE_KEYS.includes(e.key) || e.oldValue === e.newValue) return;
      if (window.location.pathname === "/records/detail") window.location.href = "/records";
      else window.location.reload();
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);
}
import { Icons, type IconName } from "@/lib/icons";
import { TopBar } from "./TopBar";

/** Nombre de signalements à traiter (veilleur du journal maître), rafraîchi chaque minute. */
function OpenAlertsCount() {
  const q = useQuery({ queryKey: ["master-watch"], queryFn: getMasterWatch, refetchInterval: 60_000, retry: false });
  return <CountBadge n={q.data?.openActions ?? 0} label="signalement(s) à traiter" color="var(--danger-500)" />;
}

/** Nouvelles archives de la GED maître en attente de décision (étape 2). */
function PendingImportsCount() {
  const role = useRole();
  const staff = role === "ARCHIVIST" || role === "ARCHIVES_HEAD" || role === "AUDITOR";
  const q = useQuery({ queryKey: ["imports-summary"], queryFn: getImportSummary, refetchInterval: 60_000,
    retry: false, enabled: staff });
  return <CountBadge n={staff ? q.data?.pending ?? 0 : 0} label="archive(s) à importer" color="var(--brand-500)" />;
}

/** Dossiers enregistrés qui attendent d'être mis en boîte (conditionnement, lot 1). */
function PendingConditioningCount() {
  const role = useRole();
  const staff = role === "ARCHIVIST" || role === "ARCHIVES_HEAD" || role === "AUDITOR";
  const q = useQuery({ queryKey: ["conditioning-pending", "count"], queryFn: () => listPendingConditioning(0, 1),
    refetchInterval: 60_000, retry: false, enabled: staff });
  return <CountBadge n={staff ? q.data?.total ?? 0 : 0} label="dossier(s) à conditionner" color="var(--warning-500)" />;
}

function CountBadge({ n, label, color }: { n: number; label: string; color: string }) {
  if (n === 0) return null;
  return (
    <span aria-label={`${n} ${label}`} style={{
      marginLeft: "auto", minWidth: 20, height: 20, padding: "0 6px", borderRadius: 10,
      background: color, color: "white", fontSize: 11, fontWeight: 700,
      display: "inline-flex", alignItems: "center", justifyContent: "center",
    }}>{n}</span>
  );
}

type NavItem = {
  href: string;
  label: string;
  icon: IconName;
  section: string;
};

const NAV_ITEMS: NavItem[] = [
  { href: "/",             label: "Tableau de bord",  icon: "dashboard",   section: "Accueil" },
  { href: "/demandes",     label: "Demandes",         icon: "requests",    section: "Travail" },
  { href: "/prets",        label: "Prêts",            icon: "loans",       section: "Travail" },
  { href: "/a-importer",   label: "À importer",        icon: "inbox",       section: "Travail" },
  { href: "/signalements", label: "Signalements",     icon: "eyes",        section: "Travail" },
  { href: "/magasin",      label: "Salle d'archives", icon: "archiveRoom", section: "Gestion" },
  { href: "/magasin/conditionner", label: "À conditionner", icon: "conditioning", section: "Gestion" },
  { href: "/magasin/etiquette", label: "Étiquettes",  icon: "label",       section: "Gestion" },
  { href: "/magasin/visite", label: "Visite 3D",      icon: "virtualTour", section: "Gestion" },
  { href: "/magasin/lots", label: "Lots de boîtes",   icon: "batches",     section: "Gestion" },
  { href: "/magasin/formats", label: "Formats de boîtes", icon: "boxFormats", section: "Gestion" },
  { href: "/records",      label: "Archives",         icon: "archives",    section: "Référence" },
];

function isActive(pathname: string, href: string): boolean {
  if (href === "/") return pathname === "/";
  if (href === "/magasin") return pathname === "/magasin";
  return pathname === href || pathname.startsWith(href + "/");
}

const SIDEBAR_WIDTH = 220;
const TOPBAR_HEIGHT = 56;

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  useFollowSessionChanges();
  // Mode magasin (iPhone) : plein écran, sans menu latéral ni barre du haut.
  if (pathname === "/terrain" || pathname.startsWith("/terrain/")) return <>{children}</>;
  return (
    <div style={{ minHeight: "100vh", background: "var(--surface-page)" }}>
      <Sidebar />
      <div data-app-content style={{
        marginLeft: SIDEBAR_WIDTH,
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
      }}>
        <TopBar />
        <main style={{
          flex: 1,
          padding: "28px 32px 48px",
          background: "var(--surface-page)",
        }}>
          {children}
        </main>
      </div>
    </div>
  );
}

function Sidebar() {
  const pathname = usePathname();
  const sections = Array.from(new Set(NAV_ITEMS.map((i) => i.section)));

  return (
    <aside data-app-chrome style={{
      position: "fixed",
      top: 0, left: 0, bottom: 0,
      width: SIDEBAR_WIDTH,
      background: "var(--surface-card)",
      borderRight: "1px solid var(--border-subtle)",
      padding: "16px 0",
      display: "flex",
      flexDirection: "column",
      zIndex: 10,
    }}>
      <Link href="/" style={{
        display: "flex", alignItems: "center", gap: 10,
        padding: "8px 20px 20px",
        textDecoration: "none", color: "var(--text-primary)",
      }}>
        <IconTile name="box" tone="kraft" size={34} />
        <div>
          <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: "-0.015em" }}>JokkoRM</div>
          <div style={{ fontSize: 10, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.06em" }}>
            Archivage physique
          </div>
        </div>
      </Link>

      <nav style={{ flex: 1, padding: "0 12px", overflow: "auto" }}>
        {sections.map((section) => (
          <div key={section} style={{ marginBottom: 20 }}>
            <div style={{
              fontSize: 10, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.08em",
              color: "var(--text-tertiary)", padding: "6px 10px 4px",
            }}>
              {section}
            </div>
            {NAV_ITEMS.filter((i) => i.section === section).map((item) => {
              const active = isActive(pathname, item.href);
              const NavIcon = Icons[item.icon];
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
                  <NavIcon size={20} weight={active ? "fill" : "duotone"} />
                  {item.label}
                  {item.href === "/signalements" && <OpenAlertsCount />}
                  {item.href === "/a-importer" && <PendingImportsCount />}
                  {item.href === "/magasin/conditionner" && <PendingConditioningCount />}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      <div style={{ padding: "12px 20px", borderTop: "1px solid var(--border-subtle)", textAlign: "right" }}>
        <a href="https://github.com/cheikhounagueye/jokkorm" target="_blank" rel="noreferrer"
          style={{ fontSize: 11, color: "var(--text-tertiary)", textDecoration: "none" }}>
          GitHub · AGPL-3.0
        </a>
      </div>
    </aside>
  );
}
