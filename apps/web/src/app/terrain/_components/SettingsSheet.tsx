"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Button, IconTile } from "@/components/ui";
import {
  currentTenant, getCurrentActor, getCurrentRole, listDevTenants,
  setCurrentActor, setCurrentRole, setCurrentTenant, type UserRole,
} from "@/lib/api";
import { Icons } from "@/lib/icons";

/** Rôles qui font les gestes du magasin. */
export const MAGASIN_ROLES: UserRole[] = ["ARCHIVIST", "ARCHIVES_HEAD"];

const ROLE_LABEL: Record<string, string> = {
  ARCHIVIST: "Archiviste",
  ARCHIVES_HEAD: "Responsable des archives",
};

/**
 * Espace, rôle et nom de la personne qui scanne. Sur iPhone, l'appli posée sur l'écran d'accueil
 * ne partage pas le stockage de Safari : ces réglages se font ici, une fois par téléphone.
 * Rôle simulé tant que la connexion (OIDC) n'est pas branchée.
 */
export function SettingsSheet({ onClose, forced }: { onClose: () => void; forced: boolean }) {
  const tenants = useQuery({ queryKey: ["dev-tenants"], queryFn: listDevTenants, retry: false, staleTime: 60_000 });
  const [tenant, setTenant] = useState(currentTenant());
  const startRole = getCurrentRole();
  const [role, setRole] = useState<UserRole>(MAGASIN_ROLES.includes(startRole) ? startRole : "ARCHIVIST");
  const [actor, setActor] = useState(MAGASIN_ROLES.includes(startRole) ? getCurrentActor() : "");

  const save = () => {
    const changedTenant = tenant !== currentTenant();
    setCurrentTenant(tenant);
    setCurrentRole(role);
    setCurrentActor(actor.trim());
    if (changedTenant) window.location.reload();
    else onClose();
  };

  return (
    <div role="dialog" aria-modal="true" aria-label="Réglages du téléphone" style={{
      position: "fixed", inset: 0, zIndex: 50, background: "rgba(10, 14, 26, 0.55)",
      display: "flex", alignItems: "flex-end", justifyContent: "center",
    }}>
      <div style={{
        width: "100%", maxWidth: 520, background: "var(--surface-card)",
        borderRadius: "18px 18px 0 0", padding: "18px 18px calc(18px + env(safe-area-inset-bottom))",
        boxShadow: "var(--shadow-lg)",
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 16 }}>
          <IconTile name="settings" tone="slate" size={40} />
          <div style={{ flex: 1 }}>
            <h2 style={{ fontSize: 17 }}>Ce téléphone</h2>
            <p style={{ fontSize: 12, color: "var(--text-secondary)" }}>
              {forced ? "Les gestes du magasin sont réservés au personnel des archives." : "Espace, rôle et nom inscrits dans le journal."}
            </p>
          </div>
          {!forced && (
            <button type="button" onClick={onClose} aria-label="Fermer" style={iconButton}>
              <Icons.close size={20} />
            </button>
          )}
        </div>

        <label style={fieldLabel}>Espace</label>
        <select value={tenant} onChange={(e) => setTenant(e.target.value)} style={field}>
          {(tenants.data ?? [{ id: tenant, displayName: tenant }]).map((t) => (
            <option key={t.id} value={t.id}>{t.displayName}</option>
          ))}
        </select>

        <label style={fieldLabel}>Rôle <span style={{ fontWeight: 400, color: "var(--text-tertiary)" }}>(simulé en attendant la connexion)</span></label>
        <div style={{ display: "flex", gap: 8, marginBottom: 14 }}>
          {MAGASIN_ROLES.map((r) => (
            <button key={r} type="button" onClick={() => setRole(r)} style={{
              flex: 1, padding: "12px 8px", borderRadius: 12, fontSize: 14, fontWeight: 600,
              border: `2px solid ${role === r ? "var(--brand-500)" : "var(--border-subtle)"}`,
              background: role === r ? "var(--brand-50)" : "var(--surface-card)",
              color: role === r ? "var(--brand-500)" : "var(--text-primary)",
            }}>{ROLE_LABEL[r]}</button>
          ))}
        </div>

        <label style={fieldLabel} htmlFor="magasin-actor">Votre nom</label>
        <input id="magasin-actor" value={actor} onChange={(e) => setActor(e.target.value)}
          placeholder="ex. moussa.ndiaye" autoCapitalize="none" autoCorrect="off" style={field} />

        <Button size="lg" fullWidth onClick={save} disabled={!actor.trim()} style={{ marginTop: 6, padding: "14px 20px", fontSize: 16 }}>
          Enregistrer
        </Button>
      </div>
    </div>
  );
}

const fieldLabel: React.CSSProperties = {
  display: "block", fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", marginBottom: 6,
};
const field: React.CSSProperties = {
  width: "100%", fontSize: 16, padding: "12px 12px", marginBottom: 14,
  borderRadius: 12, border: "1px solid var(--border-strong)", background: "var(--surface-card)",
  color: "var(--text-primary)",
};
const iconButton: React.CSSProperties = {
  width: 40, height: 40, borderRadius: 20, border: "none", background: "var(--surface-sunken)",
  color: "var(--text-secondary)", display: "inline-flex", alignItems: "center", justifyContent: "center",
};
