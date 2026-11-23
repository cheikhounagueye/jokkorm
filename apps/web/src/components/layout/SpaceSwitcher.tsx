"use client";

import { useQueries, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { IconTile } from "@/components/ui";
import { currentTenant, getMasterInfo, listDevTenants, setCurrentTenant } from "@/lib/api";
import { Icons } from "@/lib/icons";
import { masterLook, useMasterInfo } from "@/lib/useMasterInfo";

/**
 * Espace de travail (tenant) et la GED maître qui le sert. En dev on bascule entre
 * l'espace de démonstration et un espace branché sur le vrai Maarch RM, sans redémarrer.
 */
export function SpaceSwitcher() {
  const [tenant, setTenant] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  useEffect(() => setTenant(currentTenant()), []);

  const info = useMasterInfo();
  const tenants = useQuery({ queryKey: ["dev-tenants"], queryFn: listDevTenants, retry: false, staleTime: 60_000 });
  const masters = useQueries({
    queries: (open ? tenants.data ?? [] : []).map((t) => ({
      queryKey: ["master-info", t.id], queryFn: () => getMasterInfo(t.id), staleTime: Infinity, retry: false,
    })),
  });

  if (tenant === null) return <div style={{ marginRight: "auto" }} />;
  const label = tenants.data?.find((t) => t.id === tenant)?.displayName ?? tenant;
  const look = masterLook(info.data?.kind);
  const canSwitch = (tenants.data?.length ?? 0) > 1;

  const choose = (id: string) => {
    setOpen(false);
    if (id === tenant) return;
    setCurrentTenant(id);
    const url = new URL(window.location.href);
    url.searchParams.delete("tenant");
    window.location.href = url.pathname === "/records/detail" ? "/records" : url.toString();
  };

  return (
    <div style={{ position: "relative", marginRight: "auto" }}>
      <button type="button" onClick={() => canSwitch && setOpen(!open)} disabled={!canSwitch}
        title={info.data ? `${look.source}` : undefined}
        style={{
          display: "inline-flex", alignItems: "center", gap: 10, padding: "4px 10px 4px 4px",
          background: "var(--surface-card)", border: "1px solid var(--border-subtle)",
          borderRadius: "var(--radius-md)", color: "var(--text-primary)", cursor: canSwitch ? "pointer" : "default",
        }}>
        <IconTile name="site" tone="slate" size={28} variant="soft" />
        <span style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", lineHeight: 1.15 }}>
          <span style={{ fontSize: 10, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)" }}>Espace</span>
          <span style={{ fontSize: 13, fontWeight: 600 }}>{label}</span>
        </span>
        {info.data && <MasterChip kind={info.data.kind} name={info.data.displayName} />}
        {canSwitch && <Icons.chevronDown size={14} style={{ color: "var(--text-tertiary)" }} />}
      </button>

      {open && (
        <>
          <div onClick={() => setOpen(false)} style={{ position: "fixed", inset: 0, zIndex: 20 }} />
          <div style={{
            position: "absolute", top: "calc(100% + 6px)", left: 0, zIndex: 30, minWidth: 320, padding: 8,
            background: "var(--surface-elevated)", border: "1px solid var(--border-subtle)",
            borderRadius: "var(--radius-lg)", boxShadow: "var(--shadow-lg)",
          }}>
            <div style={{ padding: "6px 10px 10px", fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text-tertiary)" }}>
              Mode DEV — changer d&apos;espace
            </div>
            {(tenants.data ?? []).map((t, i) => {
              const m = masters[i]?.data;
              const active = t.id === tenant;
              return (
                <button key={t.id} type="button" onClick={() => choose(t.id)} style={{
                  display: "flex", alignItems: "center", gap: 10, width: "100%", padding: "8px 10px",
                  border: "none", borderRadius: "var(--radius-md)", textAlign: "left", cursor: "pointer",
                  background: active ? "var(--brand-50)" : "transparent", color: "var(--text-primary)",
                }}>
                  <span style={{ flex: 1, minWidth: 0 }}>
                    <span style={{ display: "block", fontSize: 13, fontWeight: active ? 700 : 500 }}>{t.displayName}</span>
                    <code style={{ fontSize: 11, color: "var(--text-tertiary)" }}>{t.id}</code>
                  </span>
                  {m && <MasterChip kind={m.kind} name={m.displayName} />}
                  {active && <Icons.success size={16} weight="fill" style={{ color: "var(--brand-500)" }} />}
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

function MasterChip({ kind, name }: { kind: string; name: string }) {
  const look = masterLook(kind);
  return (
    <span title={look.source} style={{
      display: "inline-flex", alignItems: "center", gap: 6, padding: "2px 9px", whiteSpace: "nowrap",
      fontSize: 11, fontWeight: 600, borderRadius: 99, color: "var(--text-secondary)",
      background: "var(--surface-sunken)", border: "1px solid var(--border-subtle)",
    }}>
      <span style={{ width: 7, height: 7, borderRadius: 99, background: look.dot }} />
      {name}
    </span>
  );
}
