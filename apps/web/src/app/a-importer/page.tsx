"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { useRole } from "@/app/demandes/_components/useRole";
import { Alert, Badge, Button, Card, EmptyState, PageHeader, Segmented } from "@/components/ui";
import { IconTile } from "@/components/ui/IconTile";
import {
  catchUpDeposits,
  getImportSummary,
  getMasterWatch,
  ignoreCandidate,
  importCandidate,
  importPendingOfProfile,
  listImports,
  restoreCandidate,
  setProfileAutoImport,
  type ImportCandidate,
  type ImportCatchUp,
  type ImportProfile,
  type ImportStatus,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons } from "@/lib/icons";
import { useMasterInfo } from "@/lib/useMasterInfo";

const TABS: { value: ImportStatus; label: string }[] = [
  { value: "PENDING", label: "À décider" },
  { value: "IMPORTED", label: "Importées" },
  { value: "IGNORED", label: "Ignorées" },
  { value: "UNREADABLE", label: "Illisibles" },
];

export default function ImportListPage() {
  const [tab, setTab] = useState<ImportStatus>("PENDING");
  const role = useRole();
  const name = useMasterInfo().data?.displayName ?? "la GED maître";
  const qc = useQueryClient();
  const summary = useQuery({ queryKey: ["imports-summary"], queryFn: getImportSummary, refetchInterval: 15_000 });
  const watch = useQuery({ queryKey: ["master-watch"], queryFn: getMasterWatch, refetchInterval: 15_000 });
  // Le titre est lu en direct chez le maître : pas de rafraîchissement automatique de la liste,
  // seulement quand le compteur change (nouveau dépôt) ou après une décision.
  const pendingCount = summary.data?.pending ?? 0;
  const list = useQuery({
    queryKey: ["imports", tab, tab === "PENDING" ? pendingCount : 0],
    queryFn: () => listImports(tab),
    enabled: summary.data?.available === true && role !== null && role !== "REQUESTER",
  });
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["imports"] });
    qc.invalidateQueries({ queryKey: ["imports-summary"] });
  };
  const decide = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "import" | "ignore" | "restore" }) =>
      action === "import" ? importCandidate(id) : action === "ignore" ? ignoreCandidate(id) : restoreCandidate(id),
    onSuccess: refresh,
  });
  const [catchUpResult, setCatchUpResult] = useState<ImportCatchUp | null>(null);
  const catchUp = useMutation({
    mutationFn: () => catchUpDeposits(30),
    onSuccess: (r) => { setCatchUpResult(r); refresh(); },
  });

  const canDecide = role === "ARCHIVIST" || role === "ARCHIVES_HEAD";
  const unreadable = summary.data?.unreadable ?? 0;
  const tabs = TABS.filter((t) => t.value !== "UNREADABLE" || unreadable > 0 || tab === "UNREADABLE");
  const items = list.data ?? [];
  const watching = watch.data && !watch.data.lastError && !!watch.data.lastSuccessAt;

  return (
    <section className="fade-up">
      <PageHeader
        icon="inbox"
        tone="brand"
        title="À importer"
        subtitle={`Nouvelles archives déposées dans ${name}. S'il y a du papier à ranger, importez-les ; sinon, ignorez-les.`}
        crumbs={[{ label: "Accueil", href: "/" }, { label: "À importer" }]}
      />

      {summary.isError && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="danger" title="Liste indisponible">{(summary.error as Error).message}</Alert>
        </div>
      )}

      {summary.data && !summary.data.available && (
        <Card>
          <EmptyState
            icon={<IconTile name="inbox" tone="slate" size={56} />}
            title="Pas de journal à surveiller"
            description={`${name} ne publie pas de journal : les archives s'importent depuis la page Archives, par recherche.`}
          />
        </Card>
      )}

      {summary.data?.available && role === "REQUESTER" && (
        <Card>
          <EmptyState
            icon={<IconTile name="locked" tone="slate" size={56} />}
            title="Réservé aux archivistes"
            description="Choisir d'importer une archive dans JokkoRM est un geste d'archiviste."
          />
        </Card>
      )}

      {summary.data?.available && canDecideOrAudit(role) && (
        <>
          <ProfileRules profiles={summary.data.profiles} isHead={role === "ARCHIVES_HEAD"} canImport={canDecide}
            name={name} onChanged={refresh} />

          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12,
            flexWrap: "wrap", margin: "20px 0 12px" }}>
            <Segmented
              value={tab}
              onChange={setTab}
              options={tabs.map((t) => ({
                value: t.value,
                label: t.value === "PENDING" && pendingCount > 0 ? `${t.label} (${pendingCount})`
                  : t.value === "UNREADABLE" && unreadable > 0 ? `${t.label} (${unreadable})` : t.label,
              }))}
            />
            {canDecide && (
              <Button variant="secondary" size="sm" icon={<Icons.refresh size={14} />} loading={catchUp.isPending}
                onClick={() => catchUp.mutate()}>
                Rattraper les dépôts des 30 derniers jours
              </Button>
            )}
          </div>

          {catchUp.isError && <div style={{ marginBottom: 12 }}><Alert tone="danger" title="Rattrapage impossible">{(catchUp.error as Error).message}</Alert></div>}
          {catchUpResult && <div style={{ marginBottom: 12 }}><CatchUpReport r={catchUpResult} name={name} /></div>}
          {decide.isError && <div style={{ marginBottom: 12 }}><Alert tone="danger" title="Non enregistré">{(decide.error as Error).message}</Alert></div>}

          {tab === "UNREADABLE" && items.length > 0 && (
            <div style={{ marginBottom: 12 }}>
              <Alert tone="warning" title={`${name} ne donne pas la cote de ces archives au compte de JokkoRM`}>
                Elles sont invisibles pour ce compte (autre service), sans cote, ou supprimées depuis.
                Si elles doivent être suivies, élargissez les droits du compte puis cliquez « Rattraper ».
              </Alert>
            </div>
          )}

          {list.data && items.length === 0 && (
            <Card>
              <EmptyState
                icon={<IconTile name={watching ? "success" : "pending"} tone={watching ? "success" : "slate"} size={56} />}
                title={tab === "PENDING" ? "Rien à décider" : "Rien ici pour l'instant"}
                description={tab !== "PENDING" ? "Aucune archive dans cet état."
                  : watching ? `Le veilleur lit le journal de ${name} chaque minute : une nouvelle archive y apparaîtra environ une minute après son dépôt.`
                    : "Le veilleur n'a pas encore pu lire le journal : rien n'est vérifié tant que ce n'est pas fait (voir Signalements)."}
              />
            </Card>
          )}

          <div style={{ display: "grid", gap: 10 }}>
            {items.map((c) => (
              <CandidateCard key={c.id} c={c} name={name} canDecide={canDecide}
                busy={decide.isPending && decide.variables?.id === c.id}
                onAction={(action) => decide.mutate({ id: c.id, action })} />
            ))}
          </div>
        </>
      )}
    </section>
  );
}

function canDecideOrAudit(role: string | null): boolean {
  return role === "ARCHIVIST" || role === "ARCHIVES_HEAD" || role === "AUDITOR";
}

function ProfileRules({ profiles, isHead, canImport, name, onChanged }: {
  profiles: ImportProfile[]; isHead: boolean; canImport: boolean; name: string; onChanged: () => void;
}) {
  const toggle = useMutation({
    mutationFn: ({ profile, on }: { profile: string; on: boolean }) => setProfileAutoImport(profile, on),
    onSuccess: onChanged,
  });
  const bulk = useMutation({ mutationFn: importPendingOfProfile, onSuccess: onChanged });
  return (
    <Card>
      <div style={{ display: "flex", gap: 14, alignItems: "flex-start", flexWrap: "wrap" }}>
        <IconTile name="stamp" tone="violet" size={40} />
        <div style={{ flex: 1, minWidth: 260 }}>
          <strong style={{ fontSize: 15 }}>Import automatique par profil</strong>
          <div style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
            Pour un profil qui a toujours du papier, les prochaines archives déposées dans {name} entrent seules dans
            JokkoRM, à ranger. Décision du responsable des archives, enregistrée avec son nom et la date.
          </div>
          {profiles.length === 0 && (
            <div style={{ fontSize: 13, color: "var(--text-tertiary)", marginTop: 10 }}>
              Les profils apparaîtront ici dès le premier dépôt lu dans le journal.
            </div>
          )}
          <div style={{ display: "grid", gap: 8, marginTop: profiles.length ? 12 : 0 }}>
            {profiles.map((p) => (
              <div key={p.profileReference} style={{
                display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap",
                padding: "8px 12px", border: "1px solid var(--border-subtle)", borderRadius: "var(--radius-md)",
              }}>
                <code style={{ fontSize: 13, fontWeight: 600 }}>{p.profileReference}</code>
                {p.autoImport
                  ? <Badge tone="success" icon={<Icons.success size={12} weight="bold" />}>Import automatique</Badge>
                  : <Badge tone="neutral">À décider une par une</Badge>}
                {p.decidedBy && p.decidedAt && (
                  <span style={{ fontSize: 12, color: "var(--text-tertiary)" }}>
                    {p.autoImport ? "Activé" : "Retiré"} par {p.decidedBy} le {formatInstant(p.decidedAt)}
                  </span>
                )}
                <span style={{ marginLeft: "auto", display: "flex", gap: 8 }}>
                  {canImport && p.pending > 0 && (
                    <Button size="sm" variant="secondary" loading={bulk.isPending && bulk.variables === p.profileReference}
                      onClick={() => bulk.mutate(p.profileReference)}>
                      Importer les {p.pending} en attente
                    </Button>
                  )}
                  {isHead && (
                    <Button size="sm" variant={p.autoImport ? "ghost" : "primary"}
                      loading={toggle.isPending && toggle.variables?.profile === p.profileReference}
                      onClick={() => toggle.mutate({ profile: p.profileReference, on: !p.autoImport })}>
                      {p.autoImport ? "Retirer l'import automatique" : "Activer l'import automatique"}
                    </Button>
                  )}
                </span>
              </div>
            ))}
          </div>
          {!isHead && profiles.length > 0 && (
            <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 8 }}>
              Seul le responsable des archives peut activer ou retirer une règle.
            </div>
          )}
          {toggle.isError && <div style={{ marginTop: 8 }}><Alert tone="danger">{(toggle.error as Error).message}</Alert></div>}
          {bulk.isError && <div style={{ marginTop: 8 }}><Alert tone="danger">{(bulk.error as Error).message}</Alert></div>}
          {bulk.data && (
            <div style={{ marginTop: 8 }}>
              <Alert tone={bulk.data.failed ? "warning" : "success"}
                title={`${bulk.data.imported} archive(s) importée(s)${bulk.data.failed ? `, ${bulk.data.failed} en échec` : ""}`}>
                {bulk.data.firstError}
              </Alert>
            </div>
          )}
        </div>
      </div>
    </Card>
  );
}

function CandidateCard({ c, name, canDecide, busy, onAction }: {
  c: ImportCandidate; name: string; canDecide: boolean; busy: boolean;
  onAction: (a: "import" | "ignore" | "restore") => void;
}) {
  const look = c.status === "IMPORTED" ? { icon: "success" as const, tone: "success" as const }
    : c.status === "IGNORED" ? { icon: "archives" as const, tone: "slate" as const }
      : c.status === "UNREADABLE" ? { icon: "warning" as const, tone: "warning" as const }
        : { icon: "inbox" as const, tone: "brand" as const };
  return (
    <Card>
      <div style={{ display: "flex", gap: 14, alignItems: "flex-start", flexWrap: "wrap" }}>
        <IconTile name={look.icon} tone={look.tone} size={40} />
        <div style={{ flex: 1, minWidth: 260 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
            {c.cote
              ? c.status === "IMPORTED"
                ? <Link href={`/records/detail?cote=${encodeURIComponent(c.cote)}`}
                    style={{ fontFamily: "var(--font-mono)", fontSize: 13, fontWeight: 600, color: "var(--brand-600)" }}>{c.cote}</Link>
                : <span style={{ fontFamily: "var(--font-mono)", fontSize: 13, fontWeight: 600 }}>{c.cote}</span>
              : <span style={{ fontSize: 13, fontWeight: 600 }}>Cote non communiquée</span>}
            {c.profileReference && <Badge tone="info">{c.profileReference}</Badge>}
            {c.auto && <Badge tone="success">Importée automatiquement</Badge>}
          </div>
          {c.status === "PENDING" && (
            <div style={{ fontSize: 14, marginTop: 4, color: c.title ? "var(--text-primary)" : "var(--text-tertiary)" }}>
              {c.title ?? (c.titleUnavailable ? `Titre indisponible (${name} ne répond pas ou ne trouve plus cette cote)` : "")}
            </div>
          )}
          {c.status === "UNREADABLE" && (
            <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 4 }}>
              Identifiant dans {name} : <code>{c.masterArchiveId}</code>
            </div>
          )}
          <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 4 }}>
            Déposée dans {name}{c.depositedBy ? <> par <strong>{c.depositedBy}</strong></> : null} le {formatInstant(c.depositedAt)}
            {c.decidedAt && c.status !== "PENDING" && c.status !== "UNREADABLE" && (
              <> · {c.status === "IMPORTED" ? "importée" : "ignorée"} par {c.decidedBy} le {formatInstant(c.decidedAt)}</>
            )}
          </div>
        </div>
        <div style={{ display: "flex", gap: 8, alignSelf: "center", flexWrap: "wrap" }}>
          {c.masterPageUrl && (
            <a href={c.masterPageUrl} target="_blank" rel="noreferrer" style={{ textDecoration: "none" }}>
              <Button size="sm" variant="ghost" icon={<Icons.external size={14} />}>Ouvrir dans {name}</Button>
            </a>
          )}
          {canDecide && c.status === "PENDING" && (
            <>
              <Button size="sm" icon={<Icons.download size={14} />} loading={busy} onClick={() => onAction("import")}>
                Importer
              </Button>
              <Button size="sm" variant="secondary" disabled={busy} onClick={() => onAction("ignore")}>
                Pas de papier
              </Button>
            </>
          )}
          {canDecide && c.status === "UNREADABLE" && (
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => onAction("ignore")}>Ignorer</Button>
          )}
          {canDecide && c.status === "IGNORED" && (
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => onAction("restore")}>Remettre dans la liste</Button>
          )}
        </div>
      </div>
    </Card>
  );
}

function CatchUpReport({ r, name }: { r: ImportCatchUp; name: string }) {
  const o = r.outcomes;
  const parts = [
    o.PENDING && `${o.PENDING} nouvelle(s) à décider`,
    o.AUTO_IMPORTED && `${o.AUTO_IMPORTED} importée(s) automatiquement`,
    o.UNREADABLE && `${o.UNREADABLE} illisible(s)`,
    o.ALREADY_TRACKED && `${o.ALREADY_TRACKED} déjà suivie(s) par JokkoRM`,
    o.SUB_ARCHIVE && `${o.SUB_ARCHIVE} sous-archive(s), qui suivent leur dossier`,
    o.ALREADY_SEEN && `${o.ALREADY_SEEN} déjà dans la liste`,
  ].filter(Boolean);
  return (
    <Alert tone="success" title={`Rattrapage fait : ${r.read} dépôt(s) lu(s) dans le journal de ${name} sur ${r.days} jours`}>
      {parts.length ? parts.join(" · ") : "Aucun dépôt sur la période."}
    </Alert>
  );
}
