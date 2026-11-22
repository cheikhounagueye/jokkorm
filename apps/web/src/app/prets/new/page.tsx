"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ArchiveBoxIso } from "@/components/illustrations/ArchiveBoxIso";
import {
  Alert,
  Badge,
  Button,
  CONTAINER_STATE,
  Card,
  Field,
  IconTile,
  Input,
  PageHeader,
  StatusBadge,
} from "@/components/ui";
import {
  downloadGhostCard,
  findContainerByBarcode,
  markLoanOut,
  requestLoan,
  type LoanResponse,
  type PhysicalContainerResponse,
} from "@/lib/api";
import { Icons } from "@/lib/icons";
import { boxFinish } from "../_components/boxFinish";
import { shortDate } from "../_components/dates";

const DURATION_PRESETS = [7, 14, 30, 90];

export default function NewPretPage() {
  const router = useRouter();
  const [barcode, setBarcode] = useState("");
  const [container, setContainer] = useState<PhysicalContainerResponse | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [looking, setLooking] = useState(false);
  const [borrowerName, setBorrowerName] = useState("");
  const [borrowerDept, setBorrowerDept] = useState("");
  const [purpose, setPurpose] = useState("");
  const [days, setDays] = useState(14);
  const [resultLoan, setResultLoan] = useState<LoanResponse | null>(null);

  async function handleLookup() {
    setLookupError(null);
    setContainer(null);
    if (!barcode.trim()) return;
    setLooking(true);
    try {
      const c = await findContainerByBarcode(barcode.trim());
      setContainer(c);
    } catch (e) {
      setLookupError((e as Error).message);
    } finally {
      setLooking(false);
    }
  }

  const requestMutation = useMutation({
    mutationFn: () => requestLoan({
      containerId: container!.id,
      borrowerName,
      borrowerDept: borrowerDept || undefined,
      purpose: purpose || undefined,
      expectedReturnAt: new Date(Date.now() + days * 86400_000).toISOString(),
    }),
    onSuccess: async (loan) => {
      // Enchaîne directement : marque sorti + télécharge le fantôme
      const outLoan = await markLoanOut(loan.id);
      setResultLoan(outLoan);
      await downloadGhostCard(loan.id);
    },
  });

  function startOver() {
    setResultLoan(null);
    setContainer(null);
    setBarcode("");
    setBorrowerName("");
    setBorrowerDept("");
    setPurpose("");
    setDays(14);
    requestMutation.reset();
    router.push("/prets/new");
  }

  const returnDate = new Date(Date.now() + days * 86400_000).toISOString();

  if (resultLoan) {
    return (
      <section className="fade-up">
        <PageHeader
          icon="loans"
          tone="info"
          title="Prêt enregistré"
          crumbs={[{ label: "Accueil", href: "/" }, { label: "Prêts", href: "/prets" }, { label: "Nouveau prêt" }]}
        />
        <Card style={{ maxWidth: 720 }}>
          <div style={{ display: "flex", gap: 24, alignItems: "center" }}>
            <ArchiveBoxIso size={120} finish="loaned" title="Boîte sortie" />
            <div style={{ flex: 1 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
                <IconTile name="success" tone="success" size={32} />
                <h2>Boîte sortie, fantôme téléchargé</h2>
              </div>
              <p style={{ color: "var(--text-secondary)", marginBottom: 6 }}>
                La boîte <code>{container?.barcode}</code> est prêtée à <strong>{resultLoan.borrowerName}</strong>
                {" "}jusqu&apos;au <strong>{shortDate(resultLoan.expectedReturnAt)}</strong>.
              </p>
              <p style={{ color: "var(--text-tertiary)", fontSize: 13, marginBottom: 18 }}>
                Imprime le fantôme PDF+QR et glisse-le à l&apos;emplacement de la boîte.
              </p>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <Link href="/prets">
                  <Button icon={<Icons.loans size={16} />}>Voir les prêts</Button>
                </Link>
                <Button variant="secondary" icon={<Icons.ghost size={16} />} onClick={() => downloadGhostCard(resultLoan.id)}>
                  Re-télécharger le fantôme
                </Button>
                <Button variant="ghost" icon={<Icons.add size={16} weight="bold" />} onClick={startOver}>
                  Nouveau prêt
                </Button>
              </div>
            </div>
          </div>
        </Card>
      </section>
    );
  }

  return (
    <section className="fade-up">
      <PageHeader
        icon="loans"
        tone="info"
        title="Nouveau prêt"
        subtitle="Scanne la boîte, renseigne l'emprunteur : le fantôme PDF+QR est généré automatiquement."
        crumbs={[{ label: "Accueil", href: "/" }, { label: "Prêts", href: "/prets" }, { label: "Nouveau prêt" }]}
      />

      <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1fr)", gap: 16, alignItems: "start", maxWidth: 1100 }}>
        {/* Étape 1 : scan de la boîte */}
        <Card>
          <StepTitle n={1} title="Identifier la boîte" done={!!container} />
          <div style={{ display: "flex", gap: 8, alignItems: "stretch" }}>
            <div style={{ position: "relative", flex: 1 }}>
              <Icons.barcode size={20} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: "var(--text-tertiary)" }} />
              <Input
                value={barcode}
                onChange={(e) => setBarcode(e.target.value.toUpperCase())}
                onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); handleLookup(); } }}
                placeholder="BOX-261122-XXXXXX"
                autoFocus
                aria-label="Code-barres de la boîte"
                style={{ fontFamily: "var(--font-mono)", fontSize: 16, letterSpacing: "0.04em", paddingLeft: 42, height: 44 }}
              />
            </div>
            <Button onClick={handleLookup} loading={looking} icon={<Icons.search size={16} />} style={{ height: 44 }}>
              Chercher
            </Button>
          </div>
          <p style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 8, display: "flex", alignItems: "center", gap: 6 }}>
            <Icons.scan size={14} /> La douchette saisit le code et valide automatiquement.
          </p>

          {lookupError && (
            <div style={{ marginTop: 14 }}>
              <Alert tone="danger" title="Boîte introuvable">{lookupError}</Alert>
            </div>
          )}

          {container && (
            <div style={{
              marginTop: 16, padding: 16,
              border: "1px solid var(--border-strong)", borderRadius: "var(--radius-lg)",
              background: "var(--surface-sunken)",
              display: "flex", gap: 16, alignItems: "center",
            }}>
              <ArchiveBoxIso size={88} finish={boxFinish(container)} title={container.barcode} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <code style={{ fontSize: 13 }}>{container.barcode}</code>
                <div style={{ fontWeight: 600, fontSize: 15, margin: "6px 0 8px" }}>
                  {container.label ?? "Boîte sans libellé"}
                </div>
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                  <StatusBadge meta={CONTAINER_STATE[container.state]} />
                  {container.positionId
                    ? <Badge tone="success" icon={<Icons.position size={12} weight="bold" />}>Rangée</Badge>
                    : <Badge tone="warning" icon={<Icons.warning size={12} weight="bold" />}>Zone tampon</Badge>}
                </div>
              </div>
            </div>
          )}
        </Card>

        {/* Étape 2 : infos emprunteur */}
        <Card style={{ opacity: container ? 1 : 0.55, transition: "opacity 200ms ease" }}>
          <StepTitle n={2} title="Emprunteur et durée" done={false} />
          {!container ? (
            <p style={{ fontSize: 13, color: "var(--text-tertiary)" }}>
              Identifie d&apos;abord la boîte à prêter.
            </p>
          ) : (
            <form onSubmit={(e) => { e.preventDefault(); requestMutation.mutate(); }}>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <Field label="Emprunteur" required>
                  <Input value={borrowerName} onChange={(e) => setBorrowerName(e.target.value)}
                    required placeholder="Nom complet" />
                </Field>
                <Field label="Service">
                  <Input value={borrowerDept} onChange={(e) => setBorrowerDept(e.target.value)}
                    placeholder="DRH, Juridique, Finances…" />
                </Field>
              </div>
              <Field label="Motif">
                <Input value={purpose} onChange={(e) => setPurpose(e.target.value)}
                  placeholder="ex. Audit RH 2026-Q4" />
              </Field>
              <div style={{ marginBottom: 16 }} role="group" aria-labelledby="duration-label">
                <span id="duration-label" style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", marginBottom: 6 }}>
                  Durée du prêt (jours)<span style={{ color: "var(--danger-500)", marginLeft: 3 }}>*</span>
                </span>
                <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                  {DURATION_PRESETS.map((d) => (
                    <button key={d} type="button" onClick={() => setDays(d)}
                      style={{
                        padding: "8px 12px", fontSize: 13, fontWeight: 600,
                        borderRadius: "var(--radius-md)",
                        border: `1px solid ${days === d ? "var(--brand-500)" : "var(--border-strong)"}`,
                        background: days === d ? "var(--brand-50)" : "var(--surface-card)",
                        color: days === d ? "var(--brand-500)" : "var(--text-secondary)",
                      }}>
                      {d} j
                    </button>
                  ))}
                  <Input type="number" min={1} max={365} value={days}
                    onChange={(e) => setDays(Math.max(1, Math.min(365, parseInt(e.target.value, 10) || 1)))}
                    aria-label="Durée en jours"
                    style={{ width: 90 }} />
                </div>
                <span style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--text-tertiary)", marginTop: 6 }}>
                  <Icons.calendar size={13} /> Retour prévu le <strong>{shortDate(returnDate)}</strong>
                </span>
              </div>

              {requestMutation.isError && (
                <div style={{ marginBottom: 14 }}>
                  <Alert tone="danger" title="Prêt non enregistré">{(requestMutation.error as Error).message}</Alert>
                </div>
              )}

              <div style={{ display: "flex", gap: 8, marginTop: 4 }}>
                <Button type="submit" size="lg" disabled={!borrowerName} loading={requestMutation.isPending}
                  icon={<Icons.ghost size={16} />}>
                  Enregistrer et télécharger le fantôme
                </Button>
                <Link href="/prets">
                  <Button type="button" variant="ghost" size="lg">Annuler</Button>
                </Link>
              </div>
            </form>
          )}
        </Card>
      </div>
    </section>
  );
}

function StepTitle({ n, title, done }: { n: number; title: string; done: boolean }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 16 }}>
      <span style={{
        width: 26, height: 26, borderRadius: "var(--radius-full)", flexShrink: 0,
        background: done ? "var(--success-500)" : "var(--brand-500)",
        color: "var(--text-on-brand)",
        display: "inline-flex", alignItems: "center", justifyContent: "center",
        fontSize: 13, fontWeight: 700,
      }}>
        {done ? <Icons.success size={16} weight="bold" /> : n}
      </span>
      <h2>{title}</h2>
    </div>
  );
}
