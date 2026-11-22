"use client";

import { useEffect, useState } from "react";
import { Icons } from "@/lib/icons";

const { chevronDown: ChevronDown, moon: Moon, sun: Sun, user: User2 } = Icons;
import {
  getCurrentActor,
  getCurrentRole,
  setCurrentActor,
  setCurrentRole,
  type UserRole,
} from "@/lib/api";

const ROLES: { value: UserRole; label: string; color: string; defaultActor: string }[] = [
  { value: "REQUESTER",     label: "Demandeur",             color: "#3b82f6", defaultActor: "awa.diop" },
  { value: "ARCHIVIST",     label: "Archiviste",            color: "#2e7d32", defaultActor: "archiviste" },
  { value: "ARCHIVES_HEAD", label: "Responsable archives",  color: "#b45309", defaultActor: "resp-archives" },
  { value: "AUDITOR",       label: "Auditeur",              color: "#64748b", defaultActor: "auditeur" },
];

export function TopBar() {
  return (
    <header style={{
      height: 56,
      background: "var(--surface-card)",
      borderBottom: "1px solid var(--border-subtle)",
      display: "flex",
      alignItems: "center",
      justifyContent: "flex-end",
      gap: 12,
      padding: "0 24px",
      position: "sticky", top: 0, zIndex: 5,
    }}>
      <DarkModeToggle />
      <RoleDropdown />
    </header>
  );
}

function DarkModeToggle() {
  const [theme, setTheme] = useState<"light" | "dark" | "auto">("auto");

  useEffect(() => {
    const saved = (localStorage.getItem("jokkorm.theme") as "light" | "dark" | "auto") || "auto";
    applyTheme(saved);
    setTheme(saved);
  }, []);

  function applyTheme(t: "light" | "dark" | "auto") {
    if (t === "auto") document.documentElement.removeAttribute("data-theme");
    else document.documentElement.setAttribute("data-theme", t);
    localStorage.setItem("jokkorm.theme", t);
  }

  function toggle() {
    const next = theme === "light" ? "dark" : theme === "dark" ? "auto" : "light";
    setTheme(next);
    applyTheme(next);
  }

  const icon = theme === "dark" ? <Moon size={16} /> : theme === "light" ? <Sun size={16} /> : <Sun size={16} style={{ opacity: 0.5 }} />;
  return (
    <button onClick={toggle} title={`Thème : ${theme}`}
      style={{
        display: "inline-flex", alignItems: "center", justifyContent: "center",
        width: 32, height: 32,
        background: "transparent", border: "1px solid var(--border-subtle)",
        borderRadius: "var(--radius-md)", color: "var(--text-secondary)",
      }}>
      {icon}
    </button>
  );
}

function RoleDropdown() {
  const [open, setOpen] = useState(false);
  const [role, setRole] = useState<UserRole>("REQUESTER");
  const [actor, setActor] = useState("requester");

  useEffect(() => {
    setRole(getCurrentRole());
    setActor(getCurrentActor());
  }, []);

  function handleRoleChange(newRole: UserRole) {
    const def = ROLES.find((r) => r.value === newRole);
    setRole(newRole);
    setCurrentRole(newRole);
    if (def) {
      setActor(def.defaultActor);
      setCurrentActor(def.defaultActor);
    }
    setOpen(false);
    window.location.reload();
  }

  const current = ROLES.find((r) => r.value === role) ?? ROLES[0];

  return (
    <div style={{ position: "relative" }}>
      <button onClick={() => setOpen(!open)}
        style={{
          display: "inline-flex", alignItems: "center", gap: 8,
          padding: "5px 10px 5px 5px",
          background: "var(--surface-card)",
          border: "1px solid var(--border-subtle)",
          borderRadius: "var(--radius-md)",
          color: "var(--text-primary)",
          fontSize: 13, fontWeight: 500,
        }}>
        <div style={{
          width: 24, height: 24, borderRadius: "var(--radius-full)",
          background: current.color, color: "white",
          display: "inline-flex", alignItems: "center", justifyContent: "center",
          fontSize: 11, fontWeight: 600,
        }}>
          {actor.substring(0, 2).toUpperCase()}
        </div>
        <span>{current.label}</span>
        <ChevronDown size={14} style={{ color: "var(--text-tertiary)" }} />
      </button>

      {open && (
        <>
          <div onClick={() => setOpen(false)} style={{
            position: "fixed", inset: 0, zIndex: 20,
          }} />
          <div style={{
            position: "absolute", top: "calc(100% + 6px)", right: 0, zIndex: 30,
            minWidth: 260,
            background: "var(--surface-elevated)",
            border: "1px solid var(--border-subtle)",
            borderRadius: "var(--radius-lg)",
            boxShadow: "var(--shadow-lg)",
            padding: 8,
          }}>
            <div style={{ padding: "6px 10px 10px", borderBottom: "1px solid var(--border-subtle)", marginBottom: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)", marginBottom: 6 }}>
                Mode DEV — rôle simulé
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                <User2 size={14} style={{ color: "var(--text-tertiary)" }} />
                <input value={actor}
                  onChange={(e) => { setActor(e.target.value); setCurrentActor(e.target.value); }}
                  onBlur={() => window.location.reload()}
                  style={{ fontFamily: "var(--font-mono)", fontSize: 12, padding: "3px 6px", flex: 1 }} />
              </div>
            </div>
            {ROLES.map((r) => (
              <button key={r.value} onClick={() => handleRoleChange(r.value)}
                style={{
                  display: "flex", alignItems: "center", gap: 10,
                  width: "100%", padding: "8px 10px",
                  background: role === r.value ? "var(--brand-50)" : "transparent",
                  border: "none", borderRadius: "var(--radius-md)",
                  color: role === r.value ? "var(--brand-500)" : "var(--text-primary)",
                  fontSize: 13, fontWeight: role === r.value ? 600 : 500,
                  textAlign: "left",
                }}>
                <div style={{ width: 8, height: 8, borderRadius: "var(--radius-full)", background: r.color }} />
                {r.label}
                {role === r.value && (
                  <span style={{ marginLeft: "auto", fontSize: 11, color: "var(--text-tertiary)" }}>Actif</span>
                )}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
