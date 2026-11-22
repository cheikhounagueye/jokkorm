"use client";

import { useEffect, useState } from "react";
import { getCurrentActor, getCurrentRole, setCurrentActor, setCurrentRole, type UserRole } from "@/lib/api";

const ROLES: { value: UserRole; label: string; color: string; defaultActor: string }[] = [
  { value: "REQUESTER", label: "Demandeur", color: "#5b7cb3", defaultActor: "awa.diop" },
  { value: "ARCHIVIST", label: "Archiviste", color: "#2e7d32", defaultActor: "archiviste" },
  { value: "ARCHIVES_HEAD", label: "Responsable archives", color: "#8a4b00", defaultActor: "resp-archives" },
  { value: "AUDITOR", label: "Auditeur", color: "#555", defaultActor: "auditeur" },
];

export default function RoleSwitcher() {
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
    window.location.reload(); // simple way to refresh all queries
  }

  function handleActorChange(newActor: string) {
    setActor(newActor);
    setCurrentActor(newActor);
  }

  const current = ROLES.find((r) => r.value === role) ?? ROLES[0];

  return (
    <div style={{
      background: "white", border: "1px solid #e3e5ea",
      borderLeft: `4px solid ${current.color}`,
      borderRadius: 6, padding: "0.75rem 1rem", marginBottom: "1rem",
      display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap",
    }}>
      <div style={{ fontSize: "0.85rem", color: "#666" }}>
        <strong>Dev mode</strong> · rôle simulé (OIDC phase 4A branchera les vrais)
      </div>
      <div style={{ display: "flex", gap: 6 }}>
        {ROLES.map((r) => (
          <button key={r.value} type="button" onClick={() => handleRoleChange(r.value)}
            style={{
              padding: "0.3rem 0.7rem",
              background: role === r.value ? r.color : "white",
              color: role === r.value ? "white" : r.color,
              border: `1px solid ${r.color}`,
              borderRadius: 3, cursor: "pointer", fontWeight: 600, fontSize: "0.8rem",
            }}>{r.label}</button>
        ))}
      </div>
      <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 6 }}>
        <span style={{ fontSize: "0.8rem", color: "#666" }}>Identifiant :</span>
        <input value={actor} onChange={(e) => handleActorChange(e.target.value)}
          onBlur={() => window.location.reload()}
          style={{ padding: "0.3rem 0.5rem", border: "1px solid #c8cbd4", borderRadius: 3, fontSize: "0.8rem", fontFamily: "monospace", width: 160 }} />
      </div>
    </div>
  );
}
