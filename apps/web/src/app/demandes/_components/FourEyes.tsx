"use client";

import { useEffect, useState } from "react";
import { IconTile, type Tone } from "@/components/ui";
import type { AccessRequestResponse, UserRole } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons, type IconName } from "@/lib/icons";

/**
 * Circuit d'une demande d'accès (pattern 07) : Demande → Vérification → Approbation → Remise.
 * Vérification et approbation sont faites par deux personnes différentes : ce sont les « 4 yeux ».
 */
export const STEPS: {
  key: string; title: string; who: string; what: string; icon: IconName; tone: Tone; roles: UserRole[];
}[] = [
  { key: "ask", title: "Demande", who: "Le demandeur", what: "Quelle boîte, et pourquoi.",
    icon: "requests", tone: "brand", roles: ["REQUESTER"] },
  { key: "verify", title: "Vérification", who: "L'archiviste", what: "La boîte existe et elle est disponible.",
    icon: "verify", tone: "info", roles: ["ARCHIVIST"] },
  { key: "approve", title: "Approbation", who: "Le responsable des archives", what: "Une 2ᵉ personne donne son accord.",
    icon: "stamp", tone: "violet", roles: ["ARCHIVES_HEAD"] },
  { key: "handover", title: "Remise", who: "L'archiviste", what: "Boîte, pièces ou scan remis au demandeur.",
    icon: "handover", tone: "success", roles: ["ARCHIVIST"] },
];

const HIDE_KEY = "jokkorm.fourEyes.hidden";

/** Grand schéma explicatif en tête de /demandes, repliable (préférence gardée dans ce navigateur). */
export function FourEyesBanner({ role }: { role: UserRole | null }) {
  const [hidden, setHidden] = useState(false);
  useEffect(() => {
    try { setHidden(localStorage.getItem(HIDE_KEY) === "1"); } catch { /* stockage indisponible */ }
  }, []);
  const toggle = (value: boolean) => {
    setHidden(value);
    try { localStorage.setItem(HIDE_KEY, value ? "1" : "0"); } catch { /* stockage indisponible */ }
  };

  if (hidden) {
    return (
      <button type="button" onClick={() => toggle(false)} style={{
        display: "inline-flex", alignItems: "center", gap: 8, marginBottom: 16, padding: "6px 12px",
        fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", cursor: "pointer",
        background: "var(--surface-card)", border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-full)",
      }}>
        <Icons.eyes size={16} weight="duotone" /> Comment est traitée une demande ? (4 yeux)
      </button>
    );
  }

  return (
    <div style={{
      position: "relative", marginBottom: 20, padding: "18px 20px 20px",
      background: "var(--surface-card)", border: "1px solid var(--border-subtle)",
      borderRadius: "var(--radius-lg)", boxShadow: "var(--shadow-sm)",
    }}>
      <button type="button" onClick={() => toggle(true)} title="Masquer le schéma" aria-label="Masquer le schéma"
        style={{
          position: "absolute", top: 10, right: 10, padding: 6, lineHeight: 0, cursor: "pointer",
          background: "none", border: "none", color: "var(--text-tertiary)",
        }}>
        <Icons.close size={14} weight="bold" />
      </button>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", columnGap: 12 }}>
        {/* accolade « 4 yeux » au-dessus de Vérification + Approbation */}
        <div style={{ gridColumn: "2 / 4", gridRow: 1, display: "flex", flexDirection: "column", alignItems: "center", marginBottom: 10 }}>
          <div style={{
            display: "inline-flex", alignItems: "center", gap: 8, padding: "5px 14px 5px 6px",
            borderRadius: "var(--radius-full)", fontSize: 12, fontWeight: 600,
            background: "color-mix(in srgb, #7c3aed 10%, var(--surface-card))",
            border: "1px solid color-mix(in srgb, #7c3aed 30%, transparent)", color: "var(--text-primary)",
          }}>
            <IconTile name="eyes" tone="violet" size={26} />
            <span><strong>4 yeux</strong> · deux personnes différentes doivent dire oui</span>
          </div>
          <div aria-hidden style={{
            width: "calc(100% - 96px)", height: 12, marginTop: 6,
            border: "2px solid color-mix(in srgb, #7c3aed 45%, transparent)", borderBottom: "none",
            borderRadius: "10px 10px 0 0",
          }} />
        </div>

        {/* trait qui relie les quatre tuiles */}
        <div aria-hidden style={{ gridColumn: "1 / -1", gridRow: 2, position: "relative", height: 64 }}>
          <div style={{
            position: "absolute", left: "12.5%", right: "12.5%", top: "50%", height: 2,
            background: "repeating-linear-gradient(90deg, var(--border-strong) 0 8px, transparent 8px 14px)",
          }} />
          {[25, 50, 75].map((x) => (
            <span key={x} style={{
              position: "absolute", left: `${x}%`, top: "50%", transform: "translate(-50%, -50%)",
              width: 22, height: 22, borderRadius: 99, display: "inline-flex", alignItems: "center", justifyContent: "center",
              background: "var(--surface-card)", border: "1px solid var(--border-subtle)", color: "var(--text-tertiary)",
            }}>
              <Icons.chevronRight size={12} weight="bold" />
            </span>
          ))}
        </div>

        {STEPS.map((s, i) => {
          const mine = role !== null && s.roles.includes(role);
          return (
            <div key={s.key} style={{ gridColumn: i + 1, gridRow: "2 / 4", display: "flex", flexDirection: "column", alignItems: "center", textAlign: "center", zIndex: 1 }}>
              <div style={{ position: "relative" }}>
                <IconTile name={s.icon} tone={s.tone} size={64} />
                <span style={{
                  position: "absolute", top: -6, left: -6, width: 22, height: 22, borderRadius: 99,
                  display: "inline-flex", alignItems: "center", justifyContent: "center",
                  fontSize: 11, fontWeight: 700, color: "var(--text-primary)",
                  background: "var(--surface-card)", border: "1px solid var(--border-strong)", boxShadow: "var(--shadow-sm)",
                }}>{i + 1}</span>
              </div>
              <div style={{ fontSize: 15, fontWeight: 700, marginTop: 10 }}>{s.title}</div>
              <div style={{ display: "inline-flex", alignItems: "center", gap: 5, fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", marginTop: 2 }}>
                <Icons.user size={13} weight="duotone" /> {s.who}
              </div>
              <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 4, maxWidth: 200 }}>{s.what}</div>
              {mine && (
                <span style={{
                  marginTop: 8, padding: "2px 10px", borderRadius: 99, fontSize: 11, fontWeight: 700,
                  background: "var(--brand-50)", color: "var(--brand-500)", border: "1px solid var(--brand-200)",
                }}>C&apos;est vous</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

type StepState = "done" | "current" | "todo" | "rejected" | "cancelled";

/** Où en est une demande dans les quatre étapes, avec qui a fait quoi et quand. */
export function progressOf(r: AccessRequestResponse): { states: StepState[]; notes: string[] } {
  const doneAt: (string | null)[] = [
    r.status === "DRAFT" ? null : r.createdAt,
    r.qualifiedAt,
    r.approvedAt,
    r.fulfilledAt,
  ];
  const by = [r.requesterName, r.qualifiedBy, r.approvedBy, r.fulfilledBy];
  const firstOpen = doneAt.findIndex((d) => d === null);
  const states = doneAt.map((d, i): StepState => {
    if (d !== null) return "done";
    if (i !== firstOpen) return "todo";
    if (r.status === "REJECTED") return "rejected";
    if (r.status === "CANCELLED") return "cancelled";
    return "current";
  });
  const notes = states.map((st, i) => {
    const s = STEPS[i];
    if (st === "done") return `${s.title} : faite${by[i] ? ` par ${by[i]}` : ""} le ${formatInstant(doneAt[i]!)}`;
    if (st === "rejected") return `${s.title} : rejetée${r.rejectedBy ? ` par ${r.rejectedBy}` : ""}${r.rejectReason ? ` — ${r.rejectReason}` : ""}`;
    if (st === "cancelled") return `${s.title} : demande annulée`;
    if (st === "current") return `${s.title} : en attente — ${s.who.toLowerCase()}`;
    return `${s.title} : à venir`;
  });
  return { states, notes };
}

const STATE_STYLE: Record<StepState, { bg: string; fg: string; border: string }> = {
  done:      { bg: "var(--success-500)", fg: "#fff", border: "var(--success-500)" },
  current:   { bg: "var(--surface-card)", fg: "var(--brand-500)", border: "var(--brand-500)" },
  todo:      { bg: "var(--surface-sunken)", fg: "var(--text-tertiary)", border: "var(--border-subtle)" },
  rejected:  { bg: "var(--danger-500)", fg: "#fff", border: "var(--danger-500)" },
  cancelled: { bg: "var(--surface-sunken)", fg: "var(--text-tertiary)", border: "var(--border-strong)" },
};

/**
 * Frise compacte des quatre étapes pour une demande : vert = fait, cerclé = en attente,
 * gris = à venir, rouge = rejetée. Survol : qui, quand. `labels` ajoute le nom des étapes.
 */
export function RequestProgress({ r, labels = false }: { r: AccessRequestResponse; labels?: boolean }) {
  const { states, notes } = progressOf(r);
  const dot = labels ? 34 : 26;
  return (
    <div role="list" aria-label="Avancement de la demande" style={{ display: "flex", alignItems: "flex-start" }}>
      {STEPS.map((s, i) => {
        const st = states[i];
        const c = STATE_STYLE[st];
        const StepIcon = st === "done" ? Icons.success : st === "rejected" ? Icons.error
          : st === "cancelled" ? Icons.blocked : Icons[s.icon];
        return (
          <div key={s.key} role="listitem" style={{ display: "flex", alignItems: "flex-start" }}>
            {i > 0 && (
              <div aria-hidden style={{
                width: labels ? 44 : 14, height: 2, marginTop: dot / 2 - 1,
                background: states[i - 1] === "done" ? "var(--success-500)" : "var(--border-subtle)",
              }} />
            )}
            <div title={notes[i]} style={{ display: "flex", flexDirection: "column", alignItems: "center", width: labels ? 76 : dot }}>
              <span style={{
                width: dot, height: dot, borderRadius: 99, flexShrink: 0,
                display: "inline-flex", alignItems: "center", justifyContent: "center",
                background: c.bg, color: c.fg, border: `2px solid ${c.border}`,
                boxShadow: st === "current" ? "0 0 0 4px color-mix(in srgb, var(--brand-500) 18%, transparent)" : "none",
              }}>
                <StepIcon size={Math.round(dot * 0.52)} weight={st === "done" || st === "rejected" ? "fill" : "duotone"} />
              </span>
              {labels && (
                <span style={{
                  marginTop: 6, fontSize: 11, textAlign: "center", lineHeight: 1.2,
                  fontWeight: st === "current" || st === "rejected" ? 700 : 500,
                  color: st === "todo" || st === "cancelled" ? "var(--text-tertiary)"
                    : st === "rejected" ? "var(--danger-700)" : "var(--text-primary)",
                }}>{s.title}</span>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
