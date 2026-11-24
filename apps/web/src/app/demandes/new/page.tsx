"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import ArchiveSearchForm from "@/components/ArchiveSearchForm";
import {
  Alert,
  Button,
  Card,
  Field,
  IconTile,
  Input,
  PageHeader,
  REQUEST_TYPE,
  Segmented,
  Textarea,
} from "@/components/ui";
import {
  createAccessRequest,
  findContainerByBarcode,
  submitAccessRequest,
  type MasterSearchResult,
  type PhysicalContainerResponse,
  type RequestType,
} from "@/lib/api";
import { Icons, type IconName } from "@/lib/icons";
import { useRole } from "../_components/useRole";

const TYPE_DESC: Record<RequestType, string> = {
  BORROW_BOX: "La boîte complète pour une consultation étendue (audit, instruction longue).",
  EXTRACT_PIECES: "Seulement 2-3 documents précis. La boîte reste en magasin.",
  DIGITIZE_EXTRACT: "Consultation à distance : on numérise et tu reçois un lien Maarch.",
};

/** Objet sélectionné pour la demande : par recherche métier ou par scan. */
type Selection = {
  containerId: string;
  displayTitle: string;    // ce qu'on montre au demandeur (jamais le code-barres)
  displayMeta?: string;
  barcode?: string;        // affiché seulement pour l'archiviste
};

export default function NewDemandePage() {
  const router = useRouter();
  const role = useRole();
  const [mode, setMode] = useState<"search" | "barcode">("search");
  const [selection, setSelection] = useState<Selection | null>(null);
  const [type, setType] = useState<RequestType | null>(null);
  const [name, setName] = useState("");
  const [dept, setDept] = useState("");
  const [purpose, setPurpose] = useState("");
  const [piecesDescription, setPiecesDescription] = useState("");
  const [days, setDays] = useState(14);
  const canUseBarcodeMode = role === "ARCHIVIST" || role === "ARCHIVES_HEAD";

  function selectFromSearch(r: MasterSearchResult) {
    setSelection({
      containerId: r.containerId!, // garanti par availableInPhysical=true côté formulaire
      displayTitle: r.title,
      displayMeta: [r.service, r.classificationPath ?? r.cote].filter(Boolean).join(" · "),
      barcode: r.containerBarcode ?? undefined,
    });
  }

  const mutation = useMutation({
    mutationFn: async () => {
      const r = await createAccessRequest({
        type: type!, containerId: selection!.containerId,
        requesterName: name, requesterDept: dept || undefined,
        purpose,
        piecesDescription: type === "BORROW_BOX" ? undefined : piecesDescription,
        expectedReturnAt: type === "BORROW_BOX"
          ? new Date(Date.now() + days * 86400_000).toISOString()
          : undefined,
      });
      await submitAccessRequest(r.id);
      return r;
    },
    onSuccess: () => router.push("/demandes"),
  });

  const step = !selection ? 1 : !type ? 2 : 3;
  const canSubmit = !!name && !!purpose && (type === "BORROW_BOX" || !!piecesDescription);

  return (
    <section className="fade-up">
      <PageHeader
        icon="sparkle"
        tone="brand"
        title="Nouvelle demande d'accès"
        subtitle="Trouve le dossier, choisis le type de demande, précise ton besoin."
        crumbs={[{ label: "Accueil", href: "/" }, { label: "Demandes", href: "/demandes" }, { label: "Nouvelle demande" }]}
      />

      <Stepper step={step} />

      {/* Étape 1 — Dossier */}
      {!selection ? (
        <Card>
          <StepHeader icon="record" title="Quel dossier recherches-tu ?"
            subtitle="Cherche par service, typologie ou période — pas besoin de connaître la boîte."
            action={canUseBarcodeMode && (
              <Segmented value={mode} onChange={setMode} options={[
                { value: "search", label: "Recherche métier", icon: <Icons.search size={14} /> },
                { value: "barcode", label: "Scan expert", icon: <Icons.scan size={14} /> },
              ]} />
            )} />
          {mode === "search" || !canUseBarcodeMode ? (
            <ArchiveSearchForm onSelect={selectFromSearch} />
          ) : (
            <BarcodeSearchBox onFound={(c) => setSelection({
              containerId: c.id,
              displayTitle: c.label ?? c.barcode,
              displayMeta: "Boîte saisie par scan",
              barcode: c.barcode,
            })} />
          )}
        </Card>
      ) : (
        <Card style={{ marginBottom: 16 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
            <IconTile name="record" tone="kraft" size={44} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 11, fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--success-500)", display: "flex", alignItems: "center", gap: 4, marginBottom: 2 }}>
                <Icons.success size={14} weight="fill" /> Dossier sélectionné
              </div>
              <div style={{ fontSize: 15, fontWeight: 600 }}>{selection.displayTitle}</div>
              {selection.displayMeta && <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>{selection.displayMeta}</div>}
              {canUseBarcodeMode && selection.barcode && (
                <div style={{ marginTop: 4 }}><code>{selection.barcode}</code></div>
              )}
            </div>
            <Button variant="secondary" size="sm" icon={<Icons.refresh size={14} />}
              onClick={() => { setSelection(null); setType(null); }}>
              Changer
            </Button>
          </div>
        </Card>
      )}

      {/* Étape 2 — Type */}
      {selection && (
        <Card style={{ marginBottom: 16 }}>
          <StepHeader icon="documents" title="Que veux-tu faire ?"
            subtitle="Choisis la forme d'accès la plus légère qui répond à ton besoin." />
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: 12 }}>
            {(Object.keys(REQUEST_TYPE) as RequestType[]).map((t) => {
              const meta = REQUEST_TYPE[t];
              const active = type === t;
              return (
                <button key={t} type="button" onClick={() => setType(t)} aria-pressed={active}
                  style={{
                    position: "relative", textAlign: "left", padding: 16,
                    background: active ? "var(--brand-50)" : "var(--surface-card)",
                    border: `1.5px solid ${active ? "var(--brand-500)" : "var(--border-subtle)"}`,
                    borderRadius: "var(--radius-lg)",
                    boxShadow: active ? "var(--shadow-md)" : "none",
                    transition: "all 150ms ease",
                  }}>
                  {active && (
                    <Icons.success size={20} weight="fill"
                      style={{ position: "absolute", top: 12, right: 12, color: "var(--brand-500)" }} />
                  )}
                  <IconTile name={meta.icon} tone={meta.tone} size={48} />
                  <div style={{ fontSize: 14, fontWeight: 600, marginTop: 12, color: "var(--text-primary)" }}>{meta.label}</div>
                  <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>{TYPE_DESC[t]}</div>
                </button>
              );
            })}
          </div>
        </Card>
      )}

      {/* Étape 3 — Détails */}
      {selection && type && (
        <Card>
          <StepHeader icon="edit" title="Précise ta demande"
            subtitle="Ces informations accompagnent la demande tout au long de la validation." />
          <form onSubmit={(e) => { e.preventDefault(); if (canSubmit) mutation.mutate(); }}>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 16 }}>
              <Field label="Qui demande ?" required>
                <Input value={name} onChange={(e) => setName(e.target.value)} required placeholder="Nom complet" />
              </Field>
              <Field label="Service">
                <Input value={dept} onChange={(e) => setDept(e.target.value)} placeholder="ex. DRH, Juridique, Finances" />
              </Field>
            </div>
            <Field label="Motif" required>
              <Input value={purpose} onChange={(e) => setPurpose(e.target.value)} required placeholder="ex. Audit RH 2026-Q4" />
            </Field>

            {type !== "BORROW_BOX" && (
              <Field label="Pièces demandées" required hint="Sois précis : nature, date, nom… l'archiviste sort exactement ces pièces.">
                <Textarea value={piecesDescription} onChange={(e) => setPiecesDescription(e.target.value)} required
                  placeholder="ex. Contrat de travail signé 2015, 3 derniers bulletins de paie…" />
              </Field>
            )}

            {type === "BORROW_BOX" && (
              <Field label="Durée du prêt (jours)" required
                hint={`Retour prévu le ${new Date(Date.now() + days * 86400_000).toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" })}`}>
                <Input type="number" min={1} max={365} value={days} style={{ maxWidth: 160 }}
                  onChange={(e) => setDays(Math.max(1, Math.min(365, parseInt(e.target.value, 10) || 1)))} />
              </Field>
            )}

            {mutation.isError && (
              <div style={{ marginBottom: 16 }}>
                <Alert tone="danger" title="Envoi impossible">{(mutation.error as Error).message}</Alert>
              </div>
            )}

            <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
              <Button type="submit" size="lg" disabled={!canSubmit} loading={mutation.isPending}
                icon={<Icons.requests size={16} />}>
                Soumettre la demande
              </Button>
              <Link href="/demandes"><Button type="button" variant="ghost" size="lg">Annuler</Button></Link>
              <span style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 12, color: "var(--text-tertiary)", marginLeft: "auto" }}>
                <Icons.shield size={16} /> Qualifiée par l’archiviste, puis validée par le responsable des archives.
              </span>
            </div>
          </form>
        </Card>
      )}
    </section>
  );
}

function StepHeader({ icon, title, subtitle, action }: {
  icon: IconName; title: string; subtitle: string; action?: React.ReactNode;
}) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 18 }}>
      <IconTile name={icon} tone="brand" size={36} variant="soft" />
      <div style={{ flex: 1, minWidth: 0 }}>
        <h2>{title}</h2>
        <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 2 }}>{subtitle}</p>
      </div>
      {action}
    </div>
  );
}

function Stepper({ step }: { step: number }) {
  const steps = [
    { n: 1, label: "Dossier", icon: Icons.record },
    { n: 2, label: "Type", icon: Icons.documents },
    { n: 3, label: "Détails", icon: Icons.edit },
  ];
  return (
    <ol style={{ display: "flex", alignItems: "center", gap: 8, listStyle: "none", padding: 0, margin: "0 0 20px" }}>
      {steps.map((s, i) => {
        const done = step > s.n;
        const active = step === s.n;
        const StepIcon = done ? Icons.success : s.icon;
        const color = done ? "var(--success-500)" : active ? "var(--brand-500)" : "var(--text-tertiary)";
        return (
          <li key={s.n} style={{ display: "flex", alignItems: "center", gap: 8, flex: i < steps.length - 1 ? 1 : undefined }}>
            <span style={{
              display: "inline-flex", alignItems: "center", gap: 8,
              padding: "6px 12px 6px 6px",
              background: active ? "var(--brand-50)" : "var(--surface-card)",
              border: `1px solid ${active ? "var(--brand-500)" : "var(--border-subtle)"}`,
              borderRadius: "var(--radius-full)",
              color, fontSize: 13, fontWeight: active ? 600 : 500,
              whiteSpace: "nowrap",
            }}>
              <span style={{
                width: 26, height: 26, borderRadius: "var(--radius-full)",
                display: "inline-flex", alignItems: "center", justifyContent: "center",
                background: done ? "var(--success-50)" : active ? "var(--brand-500)" : "var(--surface-sunken)",
                color: done ? "var(--success-500)" : active ? "var(--text-on-brand)" : "var(--text-tertiary)",
              }}>
                <StepIcon size={15} weight={done ? "fill" : "duotone"} />
              </span>
              {s.n} · {s.label}
            </span>
            {i < steps.length - 1 && (
              <span style={{ flex: 1, height: 2, borderRadius: 2, background: done ? "var(--success-500)" : "var(--border-subtle)" }} />
            )}
          </li>
        );
      })}
    </ol>
  );
}

function BarcodeSearchBox({ onFound }: { onFound: (c: PhysicalContainerResponse) => void }) {
  const [barcode, setBarcode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleLookup() {
    setError(null);
    if (!barcode.trim()) return;
    setLoading(true);
    try { onFound(await findContainerByBarcode(barcode.trim())); }
    catch (e) { setError((e as Error).message); }
    finally { setLoading(false); }
  }

  return (
    <div style={{
      padding: 18, background: "var(--surface-sunken)",
      border: "1px dashed var(--border-strong)", borderRadius: "var(--radius-md)",
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <IconTile name="scan" tone="slate" size={44} />
        <div style={{ flex: 1, display: "flex", gap: 8 }}>
          <Input value={barcode} onChange={(e) => setBarcode(e.target.value.toUpperCase())}
            onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); handleLookup(); } }}
            placeholder="Scanne la douchette ou saisis BOX-YYMMDD-XXXXXX"
            style={{ fontFamily: "var(--font-mono)" }} autoFocus />
          <Button type="button" onClick={handleLookup} loading={loading} icon={<Icons.search size={16} weight="bold" />}>
            Chercher
          </Button>
        </div>
      </div>
      {error && <div style={{ marginTop: 12 }}><Alert tone="danger" title="Boîte introuvable">{error}</Alert></div>}
    </div>
  );
}
