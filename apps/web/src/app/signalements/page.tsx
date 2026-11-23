"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Alert, Badge, Button, Card, EmptyState, PageHeader, Segmented } from "@/components/ui";
import { IconTile } from "@/components/ui/IconTile";
import {
  acknowledgeMasterAlert,
  getMasterWatch,
  listMasterAlerts,
  type MasterAlertItem,
  type MasterAlertKind,
  type MasterWatchStatus,
} from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { Icons, type IconName } from "@/lib/icons";
import { useMasterInfo } from "@/lib/useMasterInfo";

type Look = { title: string; icon: IconName; tone: Parameters<typeof IconTile>[0]["tone"] };

const LOOK: Record<MasterAlertKind, Look> = {
  LOCATION_RESTORED: { title: "Emplacement rétabli", icon: "position", tone: "warning" },
  LOCATION_MISMATCH: { title: "Emplacement à vérifier", icon: "warning", tone: "warning" },
  FROZEN_WHILE_OUT: { title: "Gel pendant un prêt", icon: "locked", tone: "danger" },
  FROZEN_WITH_REQUEST: { title: "Gel avec une demande de prêt", icon: "locked", tone: "warning" },
  FROZEN: { title: "Gel posé", icon: "locked", tone: "slate" },
  UNFROZEN: { title: "Gel levé", icon: "success", tone: "success" },
};

/** « il y a 40 s », « il y a 3 min », sinon la date. */
function ago(iso: string | null): string {
  if (!iso) return "jamais";
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (s < 60) return `il y a ${s} s`;
  if (s < 3600) return `il y a ${Math.round(s / 60)} min`;
  return `le ${formatInstant(iso)}`;
}

export default function SignalementsPage() {
  const [scope, setScope] = useState<"open" | "all">("open");
  const info = useMasterInfo();
  const name = info.data?.displayName ?? "la GED maître";
  const watch = useQuery({ queryKey: ["master-watch"], queryFn: getMasterWatch, refetchInterval: 15_000 });
  const alerts = useQuery({
    queryKey: ["master-alerts", scope],
    queryFn: () => listMasterAlerts(scope),
    refetchInterval: 15_000,
    enabled: watch.data?.available === true,
  });
  const qc = useQueryClient();
  const ack = useMutation({
    mutationFn: acknowledgeMasterAlert,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["master-alerts"] });
      qc.invalidateQueries({ queryKey: ["master-watch"] });
    },
  });

  const open = watch.data?.openActions ?? 0;
  const list = alerts.data ?? [];

  return (
    <section className="fade-up">
      <PageHeader
        icon="eyes"
        tone="violet"
        title="Signalements"
        subtitle={`Ce que JokkoRM a repéré dans le journal de ${name} : gel, emplacement modifié à la main.`}
        crumbs={[{ label: "Accueil", href: "/" }, { label: "Signalements" }]}
      />

      {watch.isError && (
        <div style={{ marginBottom: 16 }}>
          <Alert tone="danger" title="État du veilleur indisponible">{(watch.error as Error).message}</Alert>
        </div>
      )}

      {watch.data && !watch.data.available && (
        <Card>
          <EmptyState
            icon={<IconTile name="eyes" tone="slate" size={56} />}
            title="Pas de journal à surveiller"
            description={`${name} ne publie pas de journal : rien à surveiller dans cet espace.`}
          />
        </Card>
      )}

      {watch.data?.available && (
        <>
          <WatchHealth status={watch.data} name={name} />

          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "20px 0 12px" }}>
            <Segmented
              value={scope}
              onChange={setScope}
              options={[
                { value: "open", label: `À traiter${open > 0 ? ` (${open})` : ""}` },
                { value: "all", label: "Historique" },
              ]}
            />
          </div>

          {ack.isError && (
            <div style={{ marginBottom: 12 }}>
              <Alert tone="danger" title="Non enregistré">{(ack.error as Error).message}</Alert>
            </div>
          )}

          {alerts.data && list.length === 0 && (
            <Card>
              {watch.data.lastError || !watch.data.lastSuccessAt ? (
                <EmptyState
                  icon={<IconTile name="pending" tone="slate" size={56} />}
                  title="Rien à afficher pour l'instant"
                  description="Le veilleur n'a pas encore pu lire le journal : rien n'est vérifié tant que ce n'est pas fait."
                />
              ) : (
                <EmptyState
                  icon={<IconTile name="success" tone="success" size={56} />}
                  title={scope === "open" ? "Rien à traiter" : "Aucun signalement"}
                  description={scope === "open"
                    ? `Les emplacements affichés dans ${name} sont ceux de JokkoRM, et aucun gel ne touche une boîte sortie.`
                    : "Le veilleur n'a encore rien repéré."}
                />
              )}
            </Card>
          )}

          <div style={{ display: "grid", gap: 10 }}>
            {list.map((a) => (
              <AlertCard key={a.id} alert={a} name={name} busy={ack.isPending} onAck={() => ack.mutate(a.id)} />
            ))}
          </div>
        </>
      )}
    </section>
  );
}

function WatchHealth({ status, name }: { status: MasterWatchStatus; name: string }) {
  const failing = !!status.lastError;
  return (
    <Card>
      <div style={{ display: "flex", alignItems: "center", gap: 14, flexWrap: "wrap" }}>
        <IconTile name="eyes" tone={failing ? "danger" : "violet"} size={44} />
        <div style={{ flex: 1, minWidth: 240 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
            <strong style={{ fontSize: 15 }}>Veilleur du journal {name}</strong>
            {failing
              ? <Badge tone="danger" icon={<Icons.error size={12} weight="bold" />}>En erreur · {ago(status.lastRunAt)}</Badge>
              : status.lastRunAt
                ? <Badge tone="success" icon={<Icons.success size={12} weight="bold" />}>Actif · dernier passage {ago(status.lastRunAt)}</Badge>
                : <Badge tone="neutral" icon={<Icons.pending size={12} weight="bold" />}>Premier passage dans moins d&apos;une minute</Badge>}
          </div>
          <div style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
            {failing
              ? <>{status.lastError}{status.lastSuccessAt && <> · dernier passage réussi {ago(status.lastSuccessAt)}</>}. Rien n&apos;est perdu : la lecture reprendra là où elle s&apos;est arrêtée.</>
              : <>Lit le journal chaque minute{status.cursorAt && <> · lu jusqu&apos;au {formatInstant(status.cursorAt)}</>}.</>}
          </div>
        </div>
      </div>
    </Card>
  );
}

function AlertCard({ alert, name, busy, onAck }: {
  alert: MasterAlertItem; name: string; busy: boolean; onAck: () => void;
}) {
  const look = LOOK[alert.kind];
  return (
    <Card>
      <div style={{ display: "flex", gap: 14, alignItems: "flex-start", flexWrap: "wrap" }}>
        <IconTile name={look.icon} tone={look.tone} size={40} />
        <div style={{ flex: 1, minWidth: 260 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
            <strong style={{ fontSize: 14 }}>{look.title}</strong>
            <Link href={`/records/detail?cote=${encodeURIComponent(alert.cote)}`}
              style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "var(--brand-600)" }}>
              {alert.cote}
            </Link>
            {!alert.requiresAction && <Badge tone="neutral">Information</Badge>}
          </div>
          {alert.detail && <div style={{ fontSize: 13, color: "var(--text-primary)", marginTop: 4 }}>{alert.detail}</div>}
          <div style={{ fontSize: 12, color: "var(--text-tertiary)", marginTop: 4 }}>
            Dans {name}{alert.actor ? <> par <strong>{alert.actor}</strong></> : null}, le {formatInstant(alert.occurredAt)}
          </div>
        </div>
        <div style={{ alignSelf: "center" }}>
          {alert.acknowledgedAt
            ? <span style={{ fontSize: 12, color: "var(--text-tertiary)" }}>
                Traité par {alert.acknowledgedBy} le {formatInstant(alert.acknowledgedAt)}
              </span>
            : alert.requiresAction && (
              <Button variant="secondary" size="sm" disabled={busy} onClick={onAck}
                icon={<Icons.success size={14} weight="bold" />}>
                Marquer traité
              </Button>
            )}
        </div>
      </div>
    </Card>
  );
}
