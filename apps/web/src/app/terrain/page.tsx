"use client";

import { useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import { Button, CONTAINER_STATE, IconTile, StatusBadge } from "@/components/ui";
import {
  currentTenant, getCurrentActor, getCurrentRole, listDevTenants, placeFromScan, problemOf, problemSlug,
  recordArrival, removeFromShelf, scanCode, type ScanResponse,
} from "@/lib/api";
import { Icons } from "@/lib/icons";
import { BOX_FINISH } from "../magasin/_components/kinds";
import { beepDone, beepError, beepScan, unlockAudio } from "./_components/feedback";
import { GESTURES, INSTRUCTION, STEP_LABEL, gestureOf, type Gesture } from "./_components/gestures";
import { MAGASIN_ROLES, SettingsSheet } from "./_components/SettingsSheet";
import { AIM, useCameraScanner } from "./_components/useCameraScanner";

/**
 * Mode magasin (pattern 12) — l'écran de l'iPhone dans les rayons.
 * On choisit le geste, puis on scanne : 1 scan = arrivée, 2 = placer, 3 = retirer.
 * Chaque scan est vérifié tout de suite (bonne sorte d'étiquette, bonne boîte) ;
 * le geste part quand le dernier scan est bon. L'écran dit toujours où on en est.
 */

type Notice = {
  tone: "success" | "danger" | "info";
  title: string;
  text?: string;
  /** Retrait au mauvais endroit : la boîte est ici en vrai, on corrige en la plaçant ici. */
  fix?: { containerId: string; positionId: string; address: string };
};

type LogEntry = { at: Date; gesture: Gesture; title: string; detail: string };

const boxName = (s: ScanResponse) => s.container?.label || s.container?.barcode || s.code;
const hhmm = (d: Date) => d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
const dayTime = (iso: string) => new Date(iso).toLocaleString("fr-FR",
  { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

/** Refus connus d'avance, sans attendre le serveur (qui revérifie de toute façon). */
function boxProblem(gesture: Gesture, s: ScanResponse): Notice | null {
  const c = s.container!;
  if (gesture === "arrival") return null; // le serveur explique chaque cas (rangée, en prêt…)
  if (c.state === "DESTROYED") return { tone: "danger", title: "Boîte détruite", text: `${boxName(s)} n'existe plus en magasin.` };
  if (s.containerOnLoan) {
    return { tone: "danger", title: "Boîte en prêt",
      text: "Elle est chez l'emprunteur ; sa fiche fantôme garde sa place. Enregistre d'abord son retour." };
  }
  if (gesture === "remove" && !c.positionId) {
    return { tone: "danger", title: "Rien à retirer", text: `${boxName(s)} est déjà en zone tampon.` };
  }
  return null;
}

export default function TerrainPage() {
  const [gesture, setGesture] = useState<Gesture>("identify");
  const [scans, setScans] = useState<ScanResponse[]>([]);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [identified, setIdentified] = useState<ScanResponse | null>(null);
  const [log, setLog] = useState<LogEntry[]>([]);
  const [flash, setFlash] = useState<"ok" | "ko" | null>(null);
  const [manual, setManual] = useState("");
  const [showManual, setShowManual] = useState(false);
  const manualRef = useRef<HTMLInputElement>(null);
  /** Ouvre la saisie du code et y met le curseur, même si elle était déjà ouverte. */
  const openManual = () => {
    setShowManual(true);
    window.setTimeout(() => manualRef.current?.focus(), 0);
  };
  const [settings, setSettings] = useState<"closed" | "open" | "forced">("closed");
  const [who, setWho] = useState<{ tenant: string; actor: string } | null>(null);
  const tenants = useQuery({ queryKey: ["dev-tenants"], queryFn: listDevTenants, retry: false, staleTime: 60_000 });

  useEffect(() => {
    const role = getCurrentRole();
    const actor = getCurrentActor();
    setWho({ tenant: currentTenant(), actor });
    if (!MAGASIN_ROLES.includes(role) || !actor) setSettings("forced");
  }, [settings]);

  const def = gestureOf(gesture);
  const expect = def.steps[Math.min(scans.length, def.steps.length - 1)];

  const pulse = (kind: "ok" | "ko") => {
    setFlash(kind);
    window.setTimeout(() => setFlash(null), 450);
  };
  const fail = (n: Notice) => { beepError(); pulse("ko"); setNotice(n); };
  const accept = () => { beepScan(); pulse("ok"); };
  const done = (entry: Omit<LogEntry, "at">, n: Notice) => {
    beepDone(); pulse("ok"); setNotice(n);
    setLog((l) => [{ ...entry, at: new Date() }, ...l].slice(0, 12));
    setScans([]);
  };

  /** Le dernier scan est bon : on envoie le geste. */
  const run = async (all: ScanResponse[]) => {
    const box = all[0];
    try {
      if (gesture === "arrival") {
        const r = await recordArrival(box.code);
        if (r.alreadyReceived) {
          done({ gesture, title: boxName(box), detail: "déjà pointée" }, { tone: "info", title: "Déjà pointée",
            text: `Reçue le ${dayTime(r.container.receivedAt!)} par ${r.container.receivedBy}. Rien de changé.` });
        } else {
          done({ gesture, title: boxName(box), detail: "arrivée en zone tampon" }, { tone: "success",
            title: "Arrivée pointée", text: `${boxName(box)} est en zone tampon, à ranger.` });
        }
      } else if (gesture === "place") {
        const pos = all[1].position!;
        if (box.container!.positionId === pos.id) {
          done({ gesture, title: boxName(box), detail: "déjà à sa place" }, { tone: "info",
            title: "Déjà à cet emplacement", text: pos.address });
          return;
        }
        await placeFromScan(box.container!.id, pos.id);
        done({ gesture, title: boxName(box), detail: pos.address }, { tone: "success", title: "Rangée", text: pos.address });
      } else if (gesture === "remove") {
        const pos = all[1].position!;
        await removeFromShelf(box.container!.id, pos.id);
        done({ gesture, title: boxName(box), detail: `retirée de ${pos.address}` }, { tone: "success",
          title: "Retirée de sa tablette", text: `${boxName(box)} est en zone tampon. Sa place est libre.` });
      }
    } catch (e) {
      const slug = problemSlug(e);
      const p = problemOf(e);
      const pos = all[1]?.position;
      fail({
        tone: "danger",
        title: slug === "forbidden-role" ? "Geste réservé aux archivistes" : "Refusé",
        text: (e as Error).message,
        fix: slug === "container-not-at-position" && pos
          ? { containerId: box.container!.id, positionId: pos.id, address: p?.currentAddress ?? "" } : undefined,
      });
      // Placer refusé (place manquante…) : on garde la boîte, il suffit de scanner un autre emplacement.
      setScans(gesture === "place" ? [box] : []);
    }
  };

  const handle = async (raw: string) => {
    if (busyRef.current || settings !== "closed") return;
    busyRef.current = true;
    setBusy(true);
    try {
      let s: ScanResponse;
      try {
        s = await scanCode(raw);
      } catch (e) {
        fail({ tone: "danger", title: problemSlug(e) === "scan-code-unknown" ? "Code inconnu" : "Lecture refusée",
          text: (e as Error).message });
        return;
      }

      if (expect === "any") {
        accept(); setNotice(null); setIdentified(s);
        return;
      }

      // Un fantôme tient la place d'une boîte prêtée : il n'est jamais la boîte (D10).
      if (s.kind === "GHOST") {
        const g = s.ghost!;
        const name = g.box?.label || g.box?.barcode || "la boîte";
        fail({ tone: "danger", title: "C'est un fantôme",
          text: `Il garde la place de ${name}, en prêt chez ${g.borrowerName}. Scanne `
            + (expect === "position" ? "l'étiquette de l'emplacement." : "la boîte elle-même.") });
        return;
      }

      // Une boîte attendue — ou une autre boîte scannée à la place de l'emplacement : on repart avec elle.
      if (expect === "box" || (expect === "position" && s.kind === "CONTAINER")) {
        if (s.kind !== "CONTAINER") {
          fail({ tone: "danger", title: "J'attends une boîte", text: `C'est l'emplacement : ${s.position!.address}.` });
          return;
        }
        const problem = boxProblem(gesture, s);
        if (problem) { fail(problem); setScans([]); return; }
        if (def.steps.length === 1) { await run([s]); return; }
        accept();
        setNotice(expect === "position"
          ? { tone: "info", title: "Nouvelle boîte", text: `On repart avec ${boxName(s)}.` } : null);
        setScans([s]);
        return;
      }

      if (expect === "position") {
        const box = scans[0];
        if (gesture === "remove" && box.container!.positionId !== s.position!.id) {
          fail({ tone: "danger", title: "Pas le bon emplacement",
            text: `${boxName(box)} est enregistrée sur : ${box.containerAddress}.`,
            fix: { containerId: box.container!.id, positionId: s.position!.id, address: box.containerAddress ?? "" } });
          return;
        }
        const next = [...scans, s];
        if (def.steps.length === 2) { await run(next); return; }
        accept(); setNotice(null); setScans(next);
        return;
      }

      // expect === "sameBox" : le 3e scan confirme la sortie.
      if (s.kind !== "CONTAINER" || s.container!.id !== scans[0].container!.id) {
        fail({ tone: "danger", title: "Pas la même boîte",
          text: `Rescanne ${boxName(scans[0])} pour confirmer, ou recommence.` });
        return;
      }
      await run([...scans, s]);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const camera = useCameraScanner((text) => void handle(text), busy || settings !== "closed");

  const choose = (g: Gesture) => {
    unlockAudio();
    setGesture(g); setScans([]); setNotice(null); setIdentified(null);
  };

  const placeHere = async (fix: NonNullable<Notice["fix"]>) => {
    unlockAudio();
    setBusy(true); busyRef.current = true;
    try {
      await placeFromScan(fix.containerId, fix.positionId);
      setScans([]);
      done({ gesture: "place", title: "Correction", detail: "rangée là où elle était vraiment" },
        { tone: "success", title: "Corrigé : rangée ici", text: "L'adresse enregistrée est maintenant celle de cet emplacement." });
    } catch (e) {
      fail({ tone: "danger", title: "Refusé", text: (e as Error).message });
    } finally {
      busyRef.current = false; setBusy(false);
    }
  };

  const submitManual = (e: React.FormEvent) => {
    e.preventDefault();
    unlockAudio();
    const code = manual.trim();
    if (code) { setManual(""); void handle(code); }
  };

  const tenantLabel = tenants.data?.find((t) => t.id === who?.tenant)?.displayName ?? who?.tenant;
  const frameColor = flash === "ok" ? "#22c55e" : flash === "ko" ? "#ef4444" : "rgba(255,255,255,0.92)";

  return (
    <div style={{
      minHeight: "100dvh", background: "var(--surface-page)", color: "var(--text-primary)",
      paddingBottom: "calc(24px + env(safe-area-inset-bottom))",
    }}>
      <div style={{ maxWidth: 560, margin: "0 auto" }}>
        {/* En-tête */}
        <header style={{
          display: "flex", alignItems: "center", gap: 10,
          padding: "calc(12px + env(safe-area-inset-top)) 16px 12px",
        }}>
          <IconTile name="box" tone="kraft" size={36} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 17, fontWeight: 700, letterSpacing: "-0.01em" }}>Magasin</div>
            <div style={{ fontSize: 11, color: "var(--text-tertiary)", whiteSpace: "nowrap" }}>JokkoRM</div>
          </div>
          <button type="button" onClick={() => setSettings("open")} style={{
            display: "inline-flex", alignItems: "center", gap: 8, maxWidth: "62%",
            padding: "6px 10px", borderRadius: 999, border: "1px solid var(--border-subtle)",
            background: "var(--surface-card)", color: "var(--text-primary)", fontSize: 12, fontWeight: 600,
          }}>
            <Icons.user size={16} weight="duotone" />
            <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              {who ? `${who.actor || "?"} · ${tenantLabel}` : "…"}
            </span>
          </button>
        </header>

        {/* Choix du geste */}
        <nav aria-label="Geste" style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8, padding: "0 12px" }}>
          {GESTURES.map((g) => {
            const active = g.id === gesture;
            return (
              <button key={g.id} type="button" onClick={() => choose(g.id)} aria-pressed={active} style={{
                display: "flex", flexDirection: "column", alignItems: "center", gap: 6,
                padding: "10px 4px 9px", borderRadius: 14,
                border: `2px solid ${active ? "var(--brand-500)" : "var(--border-subtle)"}`,
                background: active ? "var(--brand-50)" : "var(--surface-card)",
                color: "var(--text-primary)", boxShadow: active ? "var(--shadow-md)" : "none",
              }}>
                <IconTile name={g.icon} tone={g.tone} size={34} />
                <span style={{ fontSize: 13, fontWeight: 700 }}>{g.label}</span>
                <span aria-label={g.id === "identify" ? "aucun geste" : `${g.steps.length} scan(s)`}
                  style={{ display: "flex", gap: 3, height: 6 }}>
                  {g.id !== "identify" && g.steps.map((_, i) => (
                    <span key={i} style={{ width: 6, height: 6, borderRadius: 3,
                      background: active ? "var(--brand-500)" : "var(--text-tertiary)" }} />
                  ))}
                </span>
              </button>
            );
          })}
        </nav>

        {/* Caméra */}
        <section style={{ padding: "12px 12px 0" }}>
          <div style={{
            position: "relative", height: "min(46vh, 420px)", minHeight: 260, borderRadius: 18,
            overflow: "hidden", background: "#0b0e14",
          }}>
            <video ref={camera.videoRef} muted playsInline autoPlay
              style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover",
                opacity: camera.state === "running" ? 1 : 0 }} />

            {camera.state === "running" && (
              <div aria-hidden style={{
                position: "absolute", left: `${(1 - AIM.width) * 50}%`, top: `${(1 - AIM.height) * 50}%`,
                width: `${AIM.width * 100}%`, height: `${AIM.height * 100}%`, borderRadius: 14,
                border: `3px solid ${frameColor}`, boxShadow: "0 0 0 9999px rgba(5, 8, 14, 0.45)",
                transition: "border-color 120ms ease",
              }} />
            )}

            {camera.state !== "running" && (
              <div style={{
                position: "absolute", inset: 0, display: "flex", flexDirection: "column",
                alignItems: "center", justifyContent: "center", gap: 14, padding: 20, textAlign: "center", color: "#e5e7eb",
              }}>
                {camera.state === "denied" || camera.state === "unsupported" || camera.state === "error"
                  ? <Icons.cameraOff size={44} weight="duotone" />
                  : <Icons.camera size={44} weight="duotone" />}
                {camera.error && <p style={{ fontSize: 14, maxWidth: 360, lineHeight: 1.4 }}>{camera.error}</p>}
                <Button size="lg" loading={camera.state === "starting"}
                  onClick={() => { unlockAudio(); void camera.start(); }}
                  icon={<Icons.scan size={18} />} style={{ padding: "14px 22px", fontSize: 16 }}>
                  {camera.state === "idle" ? "Ouvrir la caméra" : "Réessayer"}
                </Button>
              </div>
            )}

            {camera.state === "running" && (
              <>
                <div style={{
                  position: "absolute", left: 12, right: 12, bottom: 12, padding: "10px 12px", borderRadius: 12,
                  background: "rgba(5, 8, 14, 0.72)", color: "white", fontSize: 15, fontWeight: 600,
                  display: "flex", alignItems: "center", gap: 10,
                }}>
                  {busy ? <Icons.spinner size={18} className="spin" /> : <Icons.scan size={18} />}
                  <span>{busy ? "Vérification…" : INSTRUCTION[expect]}</span>
                </div>
                <div style={{ position: "absolute", top: 10, right: 10, display: "flex", gap: 8 }}>
                  {camera.torch.available && (
                    <button type="button" onClick={() => void camera.toggleTorch()} aria-label="Lampe"
                      aria-pressed={camera.torch.on} style={roundButton(camera.torch.on)}>
                      <Icons.torch size={20} weight={camera.torch.on ? "fill" : "regular"} />
                    </button>
                  )}
                  <button type="button" onClick={camera.stop} aria-label="Fermer la caméra" style={roundButton(false)}>
                    <Icons.close size={20} />
                  </button>
                </div>
              </>
            )}
          </div>
        </section>

        {/* Étapes du geste */}
        {gesture !== "identify" && (
          <section aria-label="Étapes" style={{ display: "flex", gap: 6, padding: "10px 12px 0", alignItems: "stretch" }}>
            {def.steps.map((step, i) => {
              const s = scans[i];
              const current = i === scans.length;
              // L'étape attendue n'est pas un menu : la toucher ouvre la saisie du code (étiquette illisible,
              // pas de caméra). Le scan reste la preuve normale.
              return (
                <div key={i} role={current ? "button" : undefined} tabIndex={current ? 0 : undefined}
                  aria-label={current ? `Saisir le code : ${STEP_LABEL[step]}` : undefined}
                  onClick={current ? openManual : undefined}
                  onKeyDown={current ? (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openManual(); } } : undefined}
                  style={{
                  flex: 1, minWidth: 0, padding: "8px 10px", borderRadius: 12, cursor: current ? "pointer" : undefined,
                  border: `1.5px solid ${s ? "var(--success-500)" : current ? "var(--brand-500)" : "var(--border-subtle)"}`,
                  background: s ? "var(--success-50)" : "var(--surface-card)",
                }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 11, fontWeight: 700,
                    color: s ? "var(--success-700)" : current ? "var(--brand-500)" : "var(--text-tertiary)" }}>
                    {s ? <Icons.success size={14} weight="fill" /> : <span>{i + 1}</span>}
                    {STEP_LABEL[step]}
                  </div>
                  <div style={{ fontSize: 12, marginTop: 2, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
                    color: s ? "var(--text-primary)" : current ? "var(--brand-500)" : "var(--text-tertiary)" }}>
                    {s ? (s.kind === "CONTAINER" ? boxName(s) : s.position!.address.split(" · ").slice(-2).join(" · "))
                      : current ? "Scanner, ou toucher pour saisir" : "—"}
                  </div>
                </div>
              );
            })}
            {scans.length > 0 && (
              <button type="button" onClick={() => { setScans([]); setNotice(null); }} aria-label="Recommencer"
                style={{ ...roundButton(false), background: "var(--surface-card)", color: "var(--text-secondary)",
                  border: "1px solid var(--border-subtle)", alignSelf: "center" }}>
                <Icons.restart size={18} />
              </button>
            )}
          </section>
        )}

        {/* Message du dernier scan */}
        {notice && (
          <section style={{ padding: "10px 12px 0" }}>
            <div role="status" style={{
              padding: "12px 14px", borderRadius: 14,
              background: `var(--${notice.tone === "danger" ? "danger" : notice.tone === "success" ? "success" : "info"}-50)`,
              border: `1px solid var(--${notice.tone === "danger" ? "danger" : notice.tone === "success" ? "success" : "info"}-500)`,
            }}>
              <div style={{ display: "flex", alignItems: "flex-start", gap: 10 }}>
                {notice.tone === "danger" ? <Icons.error size={22} weight="fill" style={{ color: "var(--danger-500)", flexShrink: 0 }} />
                  : notice.tone === "success" ? <Icons.success size={22} weight="fill" style={{ color: "var(--success-500)", flexShrink: 0 }} />
                  : <Icons.info size={22} weight="fill" style={{ color: "var(--info-500)", flexShrink: 0 }} />}
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 15, fontWeight: 700 }}>{notice.title}</div>
                  {notice.text && <div style={{ fontSize: 14, marginTop: 2, lineHeight: 1.4 }}>{notice.text}</div>}
                </div>
              </div>
              {notice.fix && (
                <Button variant="secondary" fullWidth onClick={() => void placeHere(notice.fix!)}
                  icon={<Icons.position size={16} />} style={{ marginTop: 10, padding: "12px 14px", fontSize: 15 }}>
                  Elle est bien ici : la placer ici
                </Button>
              )}
            </div>
          </section>
        )}

        {/* Identifier : fiche de ce qui vient d'être scanné */}
        {gesture === "identify" && identified && <IdentifiedCard s={identified} />}

        {/* Saisie à la main ou douchette Bluetooth (clavier) */}
        <section style={{ padding: "10px 12px 0" }}>
          {showManual ? (
            <form onSubmit={submitManual} style={{ display: "flex", gap: 8 }}>
              <input ref={manualRef} value={manual} onChange={(e) => setManual(e.target.value)} autoFocus
                placeholder={expect === "position" ? "Code de l'emplacement : EMP-…"
                  : expect === "any" ? "BOX-… ou EMP-…" : "Code de la boîte : BOX-…"}
                aria-label="Code à saisir" autoCapitalize="characters" autoCorrect="off"
                style={{ flex: 1, minWidth: 0, fontSize: 16, padding: "12px", borderRadius: 12,
                  border: "1px solid var(--border-strong)", background: "var(--surface-card)",
                  fontFamily: "var(--font-mono)", letterSpacing: "0.04em", color: "var(--text-primary)" }} />
              <Button type="submit" size="lg" disabled={!manual.trim() || busy}>OK</Button>
            </form>
          ) : (
            <button type="button" onClick={openManual} style={{
              display: "inline-flex", alignItems: "center", gap: 8, padding: "8px 4px", border: "none",
              background: "transparent", color: "var(--text-secondary)", fontSize: 13, fontWeight: 600,
            }}>
              <Icons.keyboard size={18} /> Saisir un code ou utiliser une douchette
            </button>
          )}
        </section>

        {/* Journal du téléphone */}
        {log.length > 0 && (
          <section style={{ padding: "14px 12px 0" }}>
            <h2 style={{ fontSize: 12, fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.06em",
              color: "var(--text-tertiary)", margin: "0 4px 6px" }}>Sur ce téléphone</h2>
            <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 6 }}>
              {log.map((l, i) => {
                const g = gestureOf(l.gesture);
                return (
                  <li key={i} style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 10px",
                    borderRadius: 12, background: "var(--surface-card)", border: "1px solid var(--border-subtle)" }}>
                    <IconTile name={g.icon} tone={g.tone} size={28} variant="soft" />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 13, fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        {l.title}
                      </div>
                      <div style={{ fontSize: 12, color: "var(--text-secondary)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        {g.label} · {l.detail}
                      </div>
                    </div>
                    <span style={{ fontSize: 12, color: "var(--text-tertiary)", fontVariantNumeric: "tabular-nums" }}>{hhmm(l.at)}</span>
                  </li>
                );
              })}
            </ul>
          </section>
        )}
      </div>

      {settings !== "closed" && (
        <SettingsSheet forced={settings === "forced"} onClose={() => setSettings("closed")} />
      )}
    </div>
  );
}

function IdentifiedCard({ s }: { s: ScanResponse }) {
  if (s.kind === "GHOST") return <GhostCard s={s} />;
  if (s.kind === "POSITION") {
    const p = s.position!;
    return (
      <section style={{ padding: "10px 12px 0" }}>
        <div style={cardStyle}>
          <IconTile name="position" tone="success" size={48} />
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.06em" }}>
              Emplacement
            </div>
            <div style={{ fontSize: 15, fontWeight: 700, lineHeight: 1.35 }}>{p.address}</div>
            <code style={{ fontSize: 12, color: "var(--text-secondary)" }}>{p.scanCode}</code>
          </div>
        </div>
      </section>
    );
  }
  const c = s.container!;
  return (
    <section style={{ padding: "10px 12px 0" }}>
      <div style={cardStyle}>
        <ArchiveBoxIso size={64} finish={BOX_FINISH[c.state]} />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
            <span style={{ fontSize: 15, fontWeight: 700 }}>{c.label || c.barcode}</span>
            <StatusBadge meta={CONTAINER_STATE[c.state]} size="sm" />
          </div>
          <code style={{ fontSize: 12, color: "var(--text-secondary)" }}>{c.barcode}</code>
          <div style={{ fontSize: 14, marginTop: 6, lineHeight: 1.4 }}>
            {s.containerOnLoan ? <strong style={{ color: "var(--warning-700)" }}>En prêt</strong>
              : s.containerAddress ? s.containerAddress
              : <span style={{ color: "var(--warning-700)", fontWeight: 600 }}>
                  En zone tampon{c.receivedAt ? ` · reçue le ${dayTime(c.receivedAt)} par ${c.receivedBy}` : " · arrivée non pointée"}
                </span>}
          </div>
        </div>
      </div>
    </section>
  );
}

/** Fantôme de prêt : qui a la boîte, jusqu'à quand, et s'il faut retirer le fantôme du rayon. */
function GhostCard({ s }: { s: ScanResponse }) {
  const g = s.ghost!;
  const day = (iso: string) => new Date(iso).toLocaleDateString("fr-FR", { day: "numeric", month: "long" });
  const active = g.status === "OUT" || g.status === "OVERDUE";
  const finished = g.status === "RETURNED" || g.status === "CANCELLED";
  return (
    <section style={{ padding: "10px 12px 0" }}>
      <div style={cardStyle}>
        <IconTile name="ghost" tone={finished ? "danger" : "warning"} size={48} />
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: "var(--text-tertiary)", textTransform: "uppercase", letterSpacing: "0.06em" }}>
            Fantôme de prêt
          </div>
          <div style={{ fontSize: 15, fontWeight: 700, lineHeight: 1.35 }}>{g.box?.label || g.box?.barcode || "Boîte inconnue"}</div>
          <code style={{ fontSize: 12, color: "var(--text-secondary)" }}>{g.ghostCode}{g.box && <> · boîte {g.box.barcode}</>}</code>
          <div style={{ fontSize: 14, marginTop: 6, lineHeight: 1.4 }}>
            {active && <>
              Chez {g.borrowerName}{g.borrowerDept ? ` (${g.borrowerDept})` : ""} · retour prévu le {day(g.expectedReturnAt)}
              {g.overdue && <strong style={{ color: "var(--danger-500)" }}> · en retard</strong>}
            </>}
            {g.status === "REQUESTED" && <>Prêt demandé, pas encore sorti : ce fantôme ne devrait pas être en rayon.</>}
            {finished && <strong style={{ color: "var(--danger-500)" }}>Prêt terminé : retire ce fantôme du rayon.</strong>}
          </div>
          {g.boxAddress && <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>Place gardée : {g.boxAddress}</div>}
        </div>
      </div>
    </section>
  );
}

const cardStyle: React.CSSProperties = {
  display: "flex", alignItems: "center", gap: 14, padding: 14, borderRadius: 14,
  background: "var(--surface-card)", border: "1px solid var(--border-subtle)", boxShadow: "var(--shadow-sm)",
};

function roundButton(on: boolean): React.CSSProperties {
  return {
    width: 40, height: 40, borderRadius: 20, border: "none",
    background: on ? "#facc15" : "rgba(5, 8, 14, 0.6)", color: on ? "#111" : "white",
    display: "inline-flex", alignItems: "center", justifyContent: "center",
  };
}
