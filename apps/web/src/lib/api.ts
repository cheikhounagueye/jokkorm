/**
 * Client API minimal pour JokkoRM — phase 3D.
 *
 * Pas encore d'auth OIDC côté front (chantier dédié phase ultérieure). Le tenant
 * est passé via X-Tenant-Id — en dev, la sécurité backend est désactivée avec
 * `jokkorm.security.enabled=false` quand on veut tester sans Keycloak.
 *
 * Toutes les requêtes passent par le rewrite Next.js `/api/*` → `http://localhost:8091/api/*`
 * configuré dans next.config.mjs.
 */

export type PhysicalArchiveStatus =
  | "REGISTERED"
  | "STORED"
  | "FROZEN"
  | "DESTROYED"
  | "TRANSFERRED";

export interface RecordResponse {
  id: string;
  cote: string;
  status: PhysicalArchiveStatus;
  registeredAt: string;
  lastTransitionAt: string;
  containerId: string | null;
}

export interface RecordsPageResponse {
  content: RecordResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  timestamp?: string;
  fieldErrors?: Record<string, string>;
  /** Règle « un dossier, une seule boîte » : la boîte où se trouve déjà le dossier. */
  currentBarcode?: string | null;
  currentLabel?: string | null;
  currentAddress?: string | null;
}

/** Slug d'erreur (`…/errors/<slug>`) d'une erreur levée par `parse`, sinon null. */
export function problemSlug(error: unknown): string | null {
  const type = (error as { problem?: ProblemDetail } | null)?.problem?.type;
  return type ? type.substring(type.lastIndexOf("/") + 1) : null;
}

export function problemOf(error: unknown): ProblemDetail | null {
  return (error as { problem?: ProblemDetail } | null)?.problem ?? null;
}

const TENANT_KEY = "jokkorm.tenant";
const TENANT_SLUG = /^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$/;

/**
 * Espace (tenant) courant. Dev : `?tenant=acme` dans l'URL le fixe, comme `?as=` pour le
 * rôle. Chaque espace a son propre maître (ex. demo = maître de démo, acme = Maarch RM).
 */
export function currentTenant(): string {
  if (typeof window === "undefined") return "demo";
  const fromUrl = new URLSearchParams(window.location.search).get("tenant");
  if (fromUrl && TENANT_SLUG.test(fromUrl)) window.localStorage.setItem(TENANT_KEY, fromUrl);
  return window.localStorage.getItem(TENANT_KEY) || "demo";
}

export function setCurrentTenant(id: string) {
  if (typeof window !== "undefined" && TENANT_SLUG.test(id)) window.localStorage.setItem(TENANT_KEY, id);
}

function headers(extra: Record<string, string> = {}): HeadersInit {
  return {
    "Accept": "application/json",
    "X-Tenant-Id": currentTenant(),
    ...extra,
  };
}

/**
 * Récupère un binaire (image, PDF) avec l'en-tête tenant. Une balise <img src> ou un
 * lien <a href> ne peuvent pas porter X-Tenant-Id : passer par ici puis un blob.
 */
export async function fetchTenantBlob(url: string, signal?: AbortSignal): Promise<Blob> {
  const response = await fetch(url, { headers: { "X-Tenant-Id": currentTenant() }, signal });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.blob();
}

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as ProblemDetail;
    const err = new Error(
      body.detail || body.title || `HTTP ${response.status}`,
    ) as Error & { problem?: ProblemDetail };
    err.problem = body;
    throw err;
  }
  return response.json() as Promise<T>;
}

export async function listRecords(params: {
  page?: number;
  size?: number;
  status?: PhysicalArchiveStatus;
}): Promise<RecordsPageResponse> {
  const qs = new URLSearchParams();
  qs.set("page", String(params.page ?? 0));
  qs.set("size", String(params.size ?? 20));
  if (params.status) qs.set("status", params.status);
  const response = await fetch(`/api/v1/records?${qs.toString()}`, { headers: headers() });
  return parse<RecordsPageResponse>(response);
}

export async function findRecordByCote(cote: string): Promise<RecordResponse> {
  const qs = new URLSearchParams({ cote });
  const response = await fetch(`/api/v1/records/by-cote?${qs.toString()}`, { headers: headers() });
  return parse<RecordResponse>(response);
}

export type MasterStatus = "AVAILABLE" | "UNAVAILABLE" | "UNREACHABLE";

export interface ConsolidatedRecordResponse {
  local: RecordResponse;
  master: {
    status: MasterStatus;
    title?: string | null;
    classificationPath?: string | null;
    duaYears?: number | null;
    finalFate?: string | null;
    legalHold?: boolean | null;
    /** Page de l'archive dans l'interface du maître (ses propres droits s'y appliquent). */
    masterPageUrl?: string | null;
  };
}

export async function getConsolidatedRecord(cote: string): Promise<ConsolidatedRecordResponse> {
  const qs = new URLSearchParams({ cote });
  const response = await fetch(`/api/v1/records/consolidated?${qs.toString()}`, { headers: headers() });
  return parse<ConsolidatedRecordResponse>(response);
}

/**
 * Range un dossier dans une boîte. Un dossier déjà rangé ailleurs est refusé (409
 * `record-in-other-container`) sauf `transfer` : à ne passer qu'après confirmation explicite.
 */
export async function storeRecord(cote: string, containerBarcode: string, transfer = false): Promise<RecordResponse> {
  const qs = new URLSearchParams({ cote });
  const response = await fetch(`/api/v1/records/store?${qs.toString()}`, {
    method: "POST",
    headers: { ...headers(), "Content-Type": "application/json" },
    body: JSON.stringify(transfer ? { containerBarcode, transfer: true } : { containerBarcode }),
  });
  return parse<RecordResponse>(response);
}

// ─── Physical topography (phase 5A) ────────────────────────────────────────────

export type StorageLocationKind = "SITE" | "STOREHOUSE" | "SHELF" | "RACK" | "POSITION";

export interface StorageLocationResponse {
  id: string;
  kind: StorageLocationKind;
  code: string;
  label: string | null;
  parentId: string | null;
  path: string;
  /** Longueur utile saisie en mm (travée, tablette, position). null = déduite (pattern 10). */
  lengthMm: number | null;
  /** Mobilier et dimensions propres (pattern 10, 5T.B). null = non mesuré (ou hérité de la travée). */
  furnitureType: FurnitureType | null;
  depthMm: number | null;
  clearanceMm: number | null;
  heightMm: number | null;
  faces: 1 | 2 | null;
  aisleCode: string | null;
  aisleOrder: number | null;
  aisleSide: "A" | "B" | null;
  /** Code de l'étiquette d'emplacement (POSITION seulement) — mode magasin. */
  scanCode: string | null;
  createdAt: string;
  updatedAt: string;
}

export type FurnitureType =
  | "FIXED_SHELVING" | "MOBILE_SHELVING" | "CABINET" | "FIRE_SAFE" | "PLAN_CHEST" | "PALLET_RACK";

export interface StorageSpec {
  furnitureType?: FurnitureType | null;
  depthMm?: number | null;
  clearanceMm?: number | null;
  heightMm?: number | null;
  faces?: 1 | 2 | null;
  aisleCode?: string | null;
  aisleOrder?: number | null;
  aisleSide?: "A" | "B" | null;
}

/** Mobilier et dimensions d'une travée, ou dimensions propres d'une tablette (remplace tout). */
export async function setStorageSpec(locationId: string, spec: StorageSpec): Promise<StorageLocationResponse> {
  const response = await fetch(`/api/v1/physical/locations/${locationId}/spec`, {
    method: "PUT",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(spec),
  });
  return parse<StorageLocationResponse>(response);
}

export interface RoomSpecInput {
  lengthMm: number | null;
  widthMm: number | null;
  ceilingHeightMm: number | null;
  usableAreaM2: number | null;
  floorLoadKgM2: number | null;
  tempMinC: number | null;
  tempMaxC: number | null;
  rhMinPct: number | null;
  rhMaxPct: number | null;
  /** Poids moyen au mètre linéaire (kg/ml), mesuré sur place ; sans lui, pas de charge estimée. */
  avgWeightKgPerMl: number | null;
}

export interface RoomSpecView {
  spec: RoomSpecInput | null;
  limits: { areaM2: number | null; footprintM2: number; measuredShelves: number; shelves: number; warnings: string[] };
}

/** Gabarit du générateur de topographie (5T.C) ; mesures en mm, null = non mesuré. */
export interface ShelvingTemplate {
  aisles: number;
  ranksPerFace: number;
  faces: 1 | 2;
  tablettesPerTravee: number;
  positionsPerTablette: number;
  lengthMm: number | null;
  furnitureType: FurnitureType | null;
  depthMm: number | null;
  clearanceMm: number | null;
  heightMm: number | null;
}

export interface ShelvingPlan {
  travees: number;
  tablettes: number;
  positions: number;
  installedMm: number;
  aisleCodes: string[];
  firstTravee: string;
  lastTravee: string;
  applied: boolean;
}

/** Plan (dryRun) ou création des épis, travées, tablettes et emplacements d'une salle. */
export async function generateShelving(storehouseId: string, template: ShelvingTemplate, dryRun: boolean): Promise<ShelvingPlan> {
  const response = await fetch(`/api/v1/physical/rooms/${storehouseId}/generate?dryRun=${dryRun}`, {
    method: "POST",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(template),
  });
  return parse<ShelvingPlan>(response);
}

// ─── Indicateurs en mètres linéaires (5T.D) ────────────────────────────────────

export interface RoomIndicators {
  installedMm: number;
  usedMm: number;
  freeMm: number;
  placeableMm: number;
  rate: number;
  boxes: number;
  byAisle: { key: string; label: string; travees: number; boxes: number; installedMm: number; usedMm: number; placeableMm: number; rate: number }[];
  capacityByFormat: { code: string; label: string; spineMm: number; boxes: number }[];
  areaM2: number | null;
  densityMlPerM2: number | null;
  load: { kgPerMl: number; estimatedKg: number; footprintM2: number; kgPerM2: number | null; limitKgM2: number | null; overLimit: boolean } | null;
  loadNote: string | null;
  saturation: { windowDays: number; historyDays: number; intakeMm: number; mmPerMonth: number | null; monthsLeft: number | null; date: string | null; note: string | null };
}

export async function getRoomIndicators(storehouseId: string): Promise<RoomIndicators> {
  const response = await fetch(`/api/v1/physical/rooms/${storehouseId}/indicators`, { headers: headers() });
  return parse<RoomIndicators>(response);
}

export async function getRoomSpec(storehouseId: string): Promise<RoomSpecView> {
  const response = await fetch(`/api/v1/physical/rooms/${storehouseId}/spec`, { headers: headers() });
  return parse<RoomSpecView>(response);
}

export async function setRoomSpec(storehouseId: string, input: RoomSpecInput): Promise<RoomSpecView> {
  const response = await fetch(`/api/v1/physical/rooms/${storehouseId}/spec`, {
    method: "PUT",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parse<RoomSpecView>(response);
}

export interface StorageLocationTreeResponse {
  node: StorageLocationResponse;
  children: StorageLocationTreeResponse[];
}

export async function listStorageTree(): Promise<StorageLocationTreeResponse[]> {
  const response = await fetch(`/api/v1/physical/locations/tree`, { headers: headers() });
  return parse<StorageLocationTreeResponse[]>(response);
}

export async function createStorageLocation(input: {
  kind: StorageLocationKind;
  code: string;
  label?: string;
  parentId?: string | null;
}): Promise<StorageLocationResponse> {
  const response = await fetch(`/api/v1/physical/locations`, {
    method: "POST",
    headers: { ...headers(), "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parse<StorageLocationResponse>(response);
}

export async function updateStorageLocation(id: string, label: string | null): Promise<StorageLocationResponse> {
  const response = await fetch(`/api/v1/physical/locations/${id}`, {
    method: "PUT",
    headers: { ...headers(), "Content-Type": "application/json" },
    body: JSON.stringify({ label }),
  });
  return parse<StorageLocationResponse>(response);
}

export async function deleteStorageLocation(id: string): Promise<void> {
  const response = await fetch(`/api/v1/physical/locations/${id}`, {
    method: "DELETE",
    headers: headers(),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as ProblemDetail;
    const err = new Error(body.detail || body.title || `HTTP ${response.status}`) as Error & { problem?: ProblemDetail };
    err.problem = body;
    throw err;
  }
}

// ─── Physical containers (phase 5B) ────────────────────────────────────────────

export type ContainerState = "EMPTY" | "OPEN" | "SEALED" | "DESTROYED";

export interface PhysicalContainerResponse {
  id: string;
  barcode: string;
  label: string | null;
  positionId: string | null;
  capacity: number | null;
  state: ContainerState;
  createdAt: string;
  updatedAt: string;
  lastMovedAt: string | null;
  batchId: string | null;
  sequenceInBatch: number | null;
  externalReference: string | null;
  boxFormatId: string | null;
  /** Pointage d'arrivée en zone tampon (mode magasin) ; null si jamais pointée. */
  receivedAt: string | null;
  receivedBy: string | null;
}

// ─── Prêts (phase 5E) ──────────────────────────────────────────────────────────

export type LoanStatus = "REQUESTED" | "OUT" | "OVERDUE" | "RETURNED" | "CANCELLED";

export interface LoanResponse {
  id: string;
  containerId: string;
  borrowerName: string;
  borrowerDept: string | null;
  purpose: string | null;
  status: LoanStatus;
  overdue: boolean;
  requestedAt: string;
  requestedBy: string;
  expectedReturnAt: string;
  outAt: string | null;
  outBy: string | null;
  returnedAt: string | null;
  returnedBy: string | null;
  cancelReason: string | null;
}

export async function requestLoan(input: {
  containerId: string;
  borrowerName: string;
  borrowerDept?: string;
  purpose?: string;
  expectedReturnAt: string;
}): Promise<LoanResponse> {
  const response = await fetch(`/api/v1/loans`, {
    method: "POST",
    headers: { ...headers(), "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parse<LoanResponse>(response);
}

export async function listActiveLoans(): Promise<LoanResponse[]> {
  const response = await fetch(`/api/v1/loans/active`, { headers: headers() });
  return parse<LoanResponse[]>(response);
}

export async function markLoanOut(loanId: string): Promise<LoanResponse> {
  const response = await fetch(`/api/v1/loans/${loanId}/out`, { method: "POST", headers: headers() });
  return parse<LoanResponse>(response);
}

export async function markLoanReturned(loanId: string): Promise<LoanResponse> {
  const response = await fetch(`/api/v1/loans/${loanId}/return`, { method: "POST", headers: headers() });
  return parse<LoanResponse>(response);
}

export async function cancelLoan(loanId: string, reason: string): Promise<LoanResponse> {
  const response = await fetch(`/api/v1/loans/${loanId}/cancel`, {
    method: "POST",
    headers: { ...headers(), "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
  return parse<LoanResponse>(response);
}

export async function downloadGhostCard(loanId: string): Promise<void> {
  const response = await fetch(`/api/v1/loans/${loanId}/ghost-card.pdf`, { headers: headers() });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `fantome-${loanId}.pdf`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

// ─── Access requests workflow 4-eyes (phase 5P) ────────────────────────────────

export type RequestType = "BORROW_BOX" | "EXTRACT_PIECES" | "DIGITIZE_EXTRACT";
export type AccessRequestStatus = "DRAFT" | "SUBMITTED" | "QUALIFIED" | "APPROVED"
  | "FULFILLED" | "COMPLETED" | "REJECTED" | "CANCELLED";
export type UserRole = "REQUESTER" | "ARCHIVIST" | "ARCHIVES_HEAD" | "AUDITOR";

export interface AccessRequestResponse {
  id: string;
  type: RequestType;
  containerId: string;
  status: AccessRequestStatus;
  requesterId: string;
  requesterName: string;
  requesterDept: string | null;
  purpose: string;
  piecesDescription: string | null;
  expectedReturnAt: string | null;
  qualifiedAt: string | null; qualifiedBy: string | null; qualificationNotes: string | null;
  approvedAt: string | null; approvedBy: string | null; approvalNotes: string | null;
  fulfilledAt: string | null; fulfilledBy: string | null; fulfillmentNotes: string | null;
  completedAt: string | null;
  maarchResId: string | null; maarchUrl: string | null;
  rejectedAt: string | null; rejectedBy: string | null; rejectReason: string | null;
  cancelledAt: string | null; cancelReason: string | null;
  physicalLoanId: string | null;
  createdAt: string; updatedAt: string;
}

export interface QualificationCheck {
  code: string;
  severity: "BLOCKING" | "WARNING" | "INFO";
  passed: boolean;
  message: string;
}

const ROLE_KEY = "jokkorm.role";
const ACTOR_KEY = "jokkorm.actor";
const ROLES: UserRole[] = ["REQUESTER", "ARCHIVIST", "ARCHIVES_HEAD", "AUDITOR"];
const DEFAULT_ACTOR: Record<UserRole, string> = {
  REQUESTER: "awa.diop",
  ARCHIVIST: "archiviste",
  ARCHIVES_HEAD: "resp-archives",
  AUDITOR: "auditeur",
};

/** Dev uniquement (rôle simulé) : `?as=ARCHIVIST&actor=x` fixe le rôle avant le premier appel API. */
function applyRoleFromUrl() {
  const params = new URLSearchParams(window.location.search);
  const as = params.get("as") as UserRole | null;
  if (as && ROLES.includes(as)) {
    localStorage.setItem(ROLE_KEY, as);
    localStorage.setItem(ACTOR_KEY, params.get("actor") || DEFAULT_ACTOR[as]);
  }
}

export function getCurrentRole(): UserRole {
  if (typeof window === "undefined") return "REQUESTER";
  applyRoleFromUrl();
  return (localStorage.getItem(ROLE_KEY) as UserRole) || "REQUESTER";
}
export function setCurrentRole(role: UserRole) {
  if (typeof window !== "undefined") localStorage.setItem(ROLE_KEY, role);
}
export function getCurrentActor(): string {
  if (typeof window === "undefined") return "requester";
  applyRoleFromUrl();
  return localStorage.getItem(ACTOR_KEY) || "requester";
}
export function setCurrentActor(actor: string) {
  if (typeof window !== "undefined") localStorage.setItem(ACTOR_KEY, actor);
}

function accessHeaders(): HeadersInit {
  return {
    ...headers(),
    "X-Role": getCurrentRole(),
    "X-Actor": getCurrentActor(),
  };
}

export async function createAccessRequest(body: {
  type: RequestType; containerId: string;
  requesterName: string; requesterDept?: string;
  purpose: string; piecesDescription?: string; expectedReturnAt?: string;
}): Promise<AccessRequestResponse> {
  const r = await fetch(`/api/v1/access-requests`, {
    method: "POST",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return parse<AccessRequestResponse>(r);
}

export async function listMyRequests(): Promise<AccessRequestResponse[]> {
  const r = await fetch(`/api/v1/access-requests/mine`, { headers: accessHeaders() });
  return parse<AccessRequestResponse[]>(r);
}

export async function listToQualify(): Promise<AccessRequestResponse[]> {
  const r = await fetch(`/api/v1/access-requests/to-qualify`, { headers: accessHeaders() });
  return parse<AccessRequestResponse[]>(r);
}

export async function listToApprove(): Promise<AccessRequestResponse[]> {
  const r = await fetch(`/api/v1/access-requests/to-approve`, { headers: accessHeaders() });
  return parse<AccessRequestResponse[]>(r);
}

export async function listToFulfill(): Promise<AccessRequestResponse[]> {
  const r = await fetch(`/api/v1/access-requests/to-fulfill`, { headers: accessHeaders() });
  return parse<AccessRequestResponse[]>(r);
}

async function postAction<T>(id: string, action: string, body?: object): Promise<T> {
  const r = await fetch(`/api/v1/access-requests/${id}/${action}`, {
    method: "POST",
    headers: body
      ? { ...accessHeaders(), "Content-Type": "application/json" }
      : accessHeaders(),
    body: body ? JSON.stringify(body) : undefined,
  });
  return parse<T>(r);
}

export const submitAccessRequest = (id: string) => postAction<AccessRequestResponse>(id, "submit");
export const qualifyAccessRequest = (id: string, notes?: string) =>
  postAction<{ request: AccessRequestResponse; checks: QualificationCheck[]; qualified: boolean; blockingReason: string | null }>(id, "qualify", { notes });
export const approveAccessRequest = (id: string, notes?: string) =>
  postAction<AccessRequestResponse>(id, "approve", { notes });
export const rejectAccessRequest = (id: string, reason: string) =>
  postAction<AccessRequestResponse>(id, "reject", { reason });
export const cancelAccessRequest = (id: string, reason: string) =>
  postAction<AccessRequestResponse>(id, "cancel", { reason });
export const fulfillAccessRequest = (id: string, notes?: string, digitizedPdfBase64?: string) =>
  postAction<{ request: AccessRequestResponse; createdLoanId: string | null }>(id, "fulfill", {
    notes, digitizedPdfBase64,
  });
export const completeAccessRequest = (id: string) => postAction<AccessRequestResponse>(id, "complete");

// ─── Recherche métier (phase 5Q, pattern 08) ────────────────────────────────

export interface MasterSearchCriteria {
  freeText?: string;
  service?: string;
  typologie?: string;
  classificationPath?: string;
  dateFrom?: string;   // ISO date YYYY-MM-DD
  dateTo?: string;
  cote?: string;
  limit?: number;
}

export interface MasterSearchResult {
  cote: string;
  title: string;
  service: string | null;
  typologie: string | null;
  classificationPath: string | null;
  dateStart: string | null;
  dateEnd: string | null;
  duaRemainingYears: number | null;
  communicable: boolean | null;
  availableInPhysical: boolean;
  // Masqué côté REQUESTER par le backend
  containerId: string | null;
  containerBarcode: string | null;
  containerLabel: string | null;
}

export async function masterSearch(criteria: MasterSearchCriteria): Promise<MasterSearchResult[]> {
  const response = await fetch(`/api/v1/master-search`, {
    method: "POST",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(criteria),
  });
  return parse<MasterSearchResult[]>(response);
}

// ─── GED maître du tenant (ADR-0003) ──────────────────────────────────────────

export type MasterKind = "noop" | "demo" | "maarch" | "maarchrm" | string;

export interface MasterInfo { tenant: string; kind: MasterKind; displayName: string }

/** Maître qui sert l'espace courant, ou celui d'un autre espace si `tenant` est donné. */
export async function getMasterInfo(tenant?: string): Promise<MasterInfo> {
  const h = tenant ? { "Accept": "application/json", "X-Tenant-Id": tenant } : headers();
  const response = await fetch(`/api/v1/master/info`, { headers: h });
  return parse<MasterInfo>(response);
}

export interface ImportFromMasterResponse { recordId: string; cote: string; title: string | null; created: boolean }

/** L'archiviste fait entrer une archive du maître dans JokkoRM (idempotent). */
export async function importFromMaster(cote: string): Promise<ImportFromMasterResponse> {
  const response = await fetch(`/api/v1/master/import`, {
    method: "POST",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({ cote }),
  });
  return parse<ImportFromMasterResponse>(response);
}

/** Affichage de l'état physique dans le maître pour une cote (tranche 2a). */
export interface MasterSyncStatus {
  cote: string;
  status: "PENDING" | "SENT" | "REJECTED";
  attempts: number;
  lastSentAt: string | null;
  lastAttemptAt: string | null;
  nextAttemptAt: string | null;
  lastError: string | null;
  lastPayload: string | null;
}

/** null si le maître de l'espace n'affiche pas l'état physique ou si rien n'a encore été envoyé. */
export async function getMasterSync(cote: string): Promise<MasterSyncStatus | null> {
  const response = await fetch(`/api/v1/master/sync?${new URLSearchParams({ cote })}`, { headers: headers() });
  if (response.status === 204) return null;
  return parse<MasterSyncStatus>(response);
}

/** Veilleur du journal du maître (étape 1). */
export interface MasterWatchStatus {
  available: boolean;
  lastRunAt: string | null;
  lastSuccessAt: string | null;
  lastError: string | null;
  cursorAt: string | null;
  lastEvents: number;
  openActions: number;
}

export type MasterAlertKind =
  | "LOCATION_RESTORED" | "LOCATION_MISMATCH" | "FROZEN_WHILE_OUT" | "FROZEN_WITH_REQUEST" | "FROZEN" | "UNFROZEN";

export interface MasterAlertItem {
  id: string;
  kind: MasterAlertKind;
  requiresAction: boolean;
  cote: string;
  occurredAt: string;
  actor: string | null;
  detail: string | null;
  createdAt: string;
  acknowledgedAt: string | null;
  acknowledgedBy: string | null;
}

export async function getMasterWatch(): Promise<MasterWatchStatus> {
  return parse<MasterWatchStatus>(await fetch(`/api/v1/master/watch`, { headers: headers() }));
}

export async function listMasterAlerts(scope: "open" | "all"): Promise<MasterAlertItem[]> {
  return parse<MasterAlertItem[]>(await fetch(`/api/v1/master/alerts?scope=${scope}`, { headers: headers() }));
}

export async function acknowledgeMasterAlert(id: string): Promise<void> {
  const r = await fetch(`/api/v1/master/alerts/${encodeURIComponent(id)}/acknowledge`, {
    method: "POST",
    headers: accessHeaders(),
  });
  if (r.status === 404) throw new Error("Signalement introuvable ou déjà traité");
  if (!r.ok) await parse<unknown>(r);
}

// ─── Liste « à importer » (étape 2) ───────────────────────────────────────────

export type ImportStatus = "PENDING" | "IMPORTED" | "IGNORED" | "UNREADABLE";

export interface ImportCandidate {
  id: string;
  /** null si la GED maître ne donne pas la cote au compte de JokkoRM (UNREADABLE). */
  cote: string | null;
  profileReference: string | null;
  masterArchiveId: string;
  depositedAt: string;
  depositedBy: string | null;
  status: ImportStatus;
  auto: boolean;
  decidedBy: string | null;
  decidedAt: string | null;
  /** Lu en direct dans la GED maître (archives à décider seulement), jamais stocké. */
  title: string | null;
  masterPageUrl: string | null;
  titleUnavailable: boolean;
}

export interface ImportProfile {
  profileReference: string;
  autoImport: boolean;
  decidedBy: string | null;
  decidedAt: string | null;
  pending: number;
}

export interface ImportSummary {
  available: boolean;
  pending: number;
  unreadable: number;
  profiles: ImportProfile[];
}

export interface ImportCatchUp {
  days: number;
  read: number;
  outcomes: Partial<Record<"PENDING" | "AUTO_IMPORTED" | "UNREADABLE" | "ALREADY_TRACKED" | "SUB_ARCHIVE" | "ALREADY_SEEN", number>>;
}

export async function getImportSummary(): Promise<ImportSummary> {
  return parse<ImportSummary>(await fetch(`/api/v1/master/imports/summary`, { headers: accessHeaders() }));
}

export async function listImports(status: ImportStatus): Promise<ImportCandidate[]> {
  return parse<ImportCandidate[]>(await fetch(`/api/v1/master/imports?status=${status}`, { headers: accessHeaders() }));
}

async function importAction(path: string, init: RequestInit = {}): Promise<ImportCandidate> {
  return parse<ImportCandidate>(await fetch(`/api/v1/master/imports/${path}`, {
    method: "POST", ...init, headers: { ...accessHeaders(), ...(init.headers ?? {}) },
  }));
}

export const importCandidate = (id: string) => importAction(`${encodeURIComponent(id)}/import`);
export const ignoreCandidate = (id: string) => importAction(`${encodeURIComponent(id)}/ignore`);
export const restoreCandidate = (id: string) => importAction(`${encodeURIComponent(id)}/restore`);

export async function setProfileAutoImport(profile: string, autoImport: boolean): Promise<ImportProfile> {
  return parse<ImportProfile>(await fetch(`/api/v1/master/imports/profiles/${encodeURIComponent(profile)}`, {
    method: "PUT",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({ autoImport }),
  }));
}

export async function importPendingOfProfile(profile: string): Promise<{ imported: number; failed: number; firstError: string | null }> {
  return parse(await fetch(`/api/v1/master/imports/profiles/${encodeURIComponent(profile)}/import-pending`, {
    method: "POST", headers: accessHeaders(),
  }));
}

export async function catchUpDeposits(days: number): Promise<ImportCatchUp> {
  return parse<ImportCatchUp>(await fetch(`/api/v1/master/imports/catch-up?days=${days}`, {
    method: "POST", headers: accessHeaders(),
  }));
}

export interface DevTenant { id: string; displayName: string }

/** Dev uniquement : liste des espaces (404 si les endpoints de démo sont désactivés). */
export async function listDevTenants(): Promise<DevTenant[]> {
  const response = await fetch(`/api/v1/dev/tenants`, { headers: headers() });
  return parse<DevTenant[]>(response);
}

// ─── Import Excel topographie (phase 5I) ──────────────────────────────────────

export interface ImportTopographyReport {
  created: number;
  /** Nœuds existants dont les mesures ont été complétées (5T.C). */
  updated: number;
  skippedExisting: number;
  errors: string[];
}

export function topographyTemplateUrl(): string {
  return `/api/v1/physical/locations/import/template.xlsx`;
}

/** Télécharge le modèle Excel via fetch+blob (header X-Tenant-Id obligatoire). */
export async function downloadTopographyTemplate(): Promise<void> {
  const response = await fetch(topographyTemplateUrl(), { headers: headers() });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "jokkorm-topographie-modele.xlsx";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export async function importTopographyExcel(file: File): Promise<ImportTopographyReport> {
  const formData = new FormData();
  formData.append("file", file);
  const response = await fetch(`/api/v1/physical/locations/import`, {
    method: "POST",
    // Rôle requis (l'import peut changer des dimensions). Pas de Content-Type : FormData met le boundary.
    headers: accessHeaders(),
    body: formData,
  });
  return parse<ImportTopographyReport>(response);
}

export async function setContainerExternalReference(
  containerId: string,
  externalReference: string | null,
): Promise<PhysicalContainerResponse> {
  const response = await fetch(`/api/v1/physical/containers/${containerId}/external-reference`, {
    method: "PUT",
    headers: { ...headers(), "Content-Type": "application/json" },
    body: JSON.stringify({ externalReference }),
  });
  return parse<PhysicalContainerResponse>(response);
}

export async function createContainer(input: {
  label?: string;
  capacity?: number;
  boxFormatId?: string;
}): Promise<PhysicalContainerResponse> {
  const response = await fetch(`/api/v1/physical/containers`, {
    method: "POST",
    headers: { ...headers(), "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parse<PhysicalContainerResponse>(response);
}

export async function findContainerByBarcode(barcode: string): Promise<PhysicalContainerResponse> {
  const qs = new URLSearchParams({ barcode });
  const response = await fetch(`/api/v1/physical/containers/by-barcode?${qs.toString()}`, {
    headers: headers(),
  });
  return parse<PhysicalContainerResponse>(response);
}

export async function placeContainer(containerId: string, positionId: string): Promise<PhysicalContainerResponse> {
  const response = await fetch(`/api/v1/physical/containers/${containerId}/position`, {
    method: "PUT",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({ positionId }),
  });
  return parse<PhysicalContainerResponse>(response);
}

export async function listContainersAtPosition(positionId: string): Promise<PhysicalContainerResponse[]> {
  const qs = new URLSearchParams({ positionId });
  const response = await fetch(`/api/v1/physical/containers?${qs.toString()}`, { headers: headers() });
  return parse<PhysicalContainerResponse[]>(response);
}

// ─── Container batches (phase 5M) ──────────────────────────────────────────────

export type BatchStatus = "CREATED" | "PRINTED" | "FULLY_DEPLOYED" | "ARCHIVED";

export interface ContainerBatchResponse {
  id: string;
  reference: string;
  size: number;
  labelPattern: string | null;
  defaultCapacity: number | null;
  status: BatchStatus;
  createdAt: string;
  createdBy: string;
  firstPrintedAt: string | null;
  lastPrintedAt: string | null;
  updatedAt: string;
}

export interface BatchHealthResponse {
  batch: ContainerBatchResponse;
  total: number;
  inTransit: number;
  deployed: number;
  deploymentPercent: number;
  storedEmpty: number;
  open: number;
  sealed: number;
  destroyed: number;
}

export async function createBatch(input: {
  reference: string;
  size: number;
  labelPattern?: string;
  defaultCapacity?: number;
  boxFormatId?: string;
}): Promise<{ batch: ContainerBatchResponse; containers: PhysicalContainerResponse[] }> {
  const response = await fetch(`/api/v1/physical/batches`, {
    method: "POST",
    headers: { ...headers(), "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parse<{ batch: ContainerBatchResponse; containers: PhysicalContainerResponse[] }>(response);
}

export async function listBatches(page = 0, size = 20): Promise<ContainerBatchResponse[]> {
  const qs = new URLSearchParams({ page: String(page), size: String(size) });
  const response = await fetch(`/api/v1/physical/batches?${qs.toString()}`, { headers: headers() });
  return parse<ContainerBatchResponse[]>(response);
}

export async function getBatchHealth(batchId: string): Promise<BatchHealthResponse> {
  const response = await fetch(`/api/v1/physical/batches/${batchId}/health`, { headers: headers() });
  return parse<BatchHealthResponse>(response);
}

export interface StaleBufferEntry {
  batchId: string;
  batchReference: string;
  staleCount: number;
  oldestCreatedAt: string;
}

export interface BufferZoneAlert {
  thresholdDays: number;
  totalStaleContainers: number;
  byBatch: StaleBufferEntry[];
  /** Calculé côté client (backend n'expose pas ce champ en JSON) */
  hasAlerts?: boolean;
}

export async function getBufferZoneAlerts(): Promise<BufferZoneAlert> {
  const response = await fetch(`/api/v1/physical/batches/alerts/buffer-zone`, { headers: headers() });
  return parse<BufferZoneAlert>(response);
}

export function batchLabelsPdfUrl(batchId: string, template = "AVERY_L7160", symbology = "CODE128"): string {
  const qs = new URLSearchParams({ template, symbology });
  return `/api/v1/physical/batches/${batchId}/labels.pdf?${qs.toString()}`;
}

/**
 * Télécharge le PDF via fetch (avec header X-Tenant-Id) puis déclenche un save
 * navigateur par blob + object URL. Pattern obligatoire pour downloads
 * authentifiés : un <a href> natif ne porte aucun header custom.
 */
export async function downloadBatchLabelsPdf(
  batchId: string,
  template = "AVERY_L7160",
  symbology = "CODE128",
): Promise<void> {
  const response = await fetch(batchLabelsPdfUrl(batchId, template, symbology), {
    headers: headers(),
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as ProblemDetail;
    throw new Error(body.detail || body.title || `HTTP ${response.status}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `etiquettes-${batchId}.pdf`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

// ─── Salle d'archives / visite 3D (phase 5S) ───────────────────────────────────

export type EquipmentKind = "FIRE_EXTINGUISHER" | "SMOKE_DETECTOR" | "EMERGENCY_EXIT" | "THERMO_HYGROMETER";

/**
 * Occupation en longueur (pattern 10). `capacity` = équivalent en boîtes du format par
 * défaut ; la vérité est `usedMm` / `lengthMm`. `placeableMm` déduit la marge de la tablette.
 */
export interface Occupancy {
  boxes: number;
  capacity: number;
  rate: number;
  usedMm: number;
  lengthMm: number;
  placeableMm: number;
}
export interface RoomNode { id: string; code: string; label: string | null; path: string }

export interface RoomBox {
  id: string;
  /** null hors archiviste / responsable / auditeur (pattern 08). */
  barcode: string | null;
  label: string | null;
  state: ContainerState;
  /** Dos de la boîte en mm (longueur occupée sur la tablette). */
  spineMm: number;
  formatCode: string | null;
  onLoan: boolean;
  overdue: boolean;
  expectedReturnAt: string | null;
}
export interface RoomPosition { node: RoomNode; occupancy: Occupancy; boxes: RoomBox[] }
/** {@code clearanceMm} : hauteur libre propre à la tablette (null = celle de la travée). */
export interface RoomRack { node: RoomNode; occupancy: Occupancy; positions: RoomPosition[]; clearanceMm: number | null }
/** Mobilier et épi d'une travée (pattern 10, 5T.B) : la 3D place les épis à l'échelle. */
export interface RoomFurniture {
  furnitureType: FurnitureType | null;
  depthMm: number | null;
  clearanceMm: number | null;
  heightMm: number | null;
  faces: 1 | 2 | null;
  aisleCode: string | null;
  aisleOrder: number | null;
  aisleSide: "A" | "B" | null;
}
export interface RoomShelf { node: RoomNode; occupancy: Occupancy; racks: RoomRack[]; furniture: RoomFurniture }
export interface RoomEquipment {
  id: string;
  kind: EquipmentKind;
  label: string | null;
  x: number;
  z: number;
  ceilingMounted: boolean;
  lastInspectionOn: string | null;
  nextInspectionOn: string | null;
  inspectionOverdue: boolean;
}
export interface RoomResponse {
  room: RoomNode;
  occupancy: Occupancy;
  positions: number;
  boxesOnLoan: number;
  detailsRevealed: boolean;
  shelves: RoomShelf[];
  equipment: RoomEquipment[];
  defaultSpineMm: number;
  marginPercent: number;
  /** Dimensions mesurées de la salle (5T.B), null si non saisies. */
  dims: { lengthMm: number | null; widthMm: number | null; ceilingHeightMm: number | null } | null;
}

export async function getRoom(storehouseId: string): Promise<RoomResponse> {
  const response = await fetch(`/api/v1/physical/rooms/${storehouseId}`, { headers: accessHeaders() });
  return parse<RoomResponse>(response);
}

/** Longueur utile (mm) d'une travée, tablette ou position ; null = déduite. */
export async function setStorageLength(locationId: string, lengthMm: number | null): Promise<StorageLocationResponse> {
  const response = await fetch(`/api/v1/physical/locations/${locationId}/length`, {
    method: "PUT",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({ lengthMm }),
  });
  return parse<StorageLocationResponse>(response);
}

export interface SpaceFit { formatId: string; code: string; label: string; spineMm: number; count: number }
export interface StorageSpace {
  locationId: string;
  kind: StorageLocationKind;
  lengthMm: number;
  usedMm: number;
  placeableMm: number;
  boxes: number;
  marginPercent: number;
  fits: SpaceFit[];
}

/** Place restante d'une tablette ou position, et boîtes qui tiennent par format. */
export async function getStorageSpace(locationId: string): Promise<StorageSpace> {
  const response = await fetch(`/api/v1/physical/locations/${locationId}/space`, { headers: headers() });
  return parse<StorageSpace>(response);
}

// ─── Formats de boîtes (phase 5T, pattern 10) ──────────────────────────────────

export type BoxQuality = "CONSERVATION" | "STANDARD" | "POLYPROPYLENE";

export interface BoxFormatResponse {
  id: string;
  code: string;
  label: string;
  spineMm: number;
  depthMm: number;
  heightMm: number;
  orientation: "UPRIGHT" | "FLAT";
  quality: BoxQuality;
  maxWeightKg: number | null;
  active: boolean;
  isDefault: boolean;
}

export async function listBoxFormats(): Promise<BoxFormatResponse[]> {
  const response = await fetch(`/api/v1/physical/box-formats`, { headers: headers() });
  return parse<BoxFormatResponse[]>(response);
}

export async function createBoxFormat(input: {
  code: string;
  label: string;
  spineMm: number;
  depthMm: number;
  heightMm: number;
  quality?: BoxQuality;
  maxWeightKg?: number;
}): Promise<BoxFormatResponse> {
  const response = await fetch(`/api/v1/physical/box-formats`, {
    method: "POST",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  return parse<BoxFormatResponse>(response);
}

export async function updateBoxFormat(id: string, change: {
  label?: string;
  quality?: BoxQuality;
  maxWeightKg?: number;
  active?: boolean;
  isDefault?: boolean;
}): Promise<BoxFormatResponse> {
  const response = await fetch(`/api/v1/physical/box-formats/${id}`, {
    method: "PUT",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(change),
  });
  return parse<BoxFormatResponse>(response);
}

export async function changeContainerFormat(containerId: string, boxFormatId: string): Promise<PhysicalContainerResponse> {
  const response = await fetch(`/api/v1/physical/containers/${containerId}/format`, {
    method: "PUT",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify({ boxFormatId }),
  });
  return parse<PhysicalContainerResponse>(response);
}

/** 1000 → « 100 cm », 45 → « 4,5 cm ». */
export function cm(mm: number): string {
  const v = mm / 10;
  return `${Number.isInteger(v) ? v : v.toFixed(1).replace(".", ",")} cm`;
}

/** 15120 → « 15,1 ml ». */
export function ml(mm: number): string {
  const v = mm / 1000;
  return `${v >= 10 ? v.toFixed(1) : v.toFixed(2)}`.replace(".", ",") + " ml";
}

// ─── Mode magasin (pattern 12) : gestes au scan depuis l'iPhone ou une douchette ───

export interface ScanPositionView {
  id: string;
  path: string;
  scanCode: string;
  address: string;
}

/** Ce qu'une étiquette scannée désigne : une boîte OU un emplacement. */
export interface ScanResponse {
  code: string;
  kind: "CONTAINER" | "POSITION";
  container: PhysicalContainerResponse | null;
  containerAddress: string | null;
  containerOnLoan: boolean;
  position: ScanPositionView | null;
}

export interface ArrivalResponse {
  container: PhysicalContainerResponse;
  alreadyReceived: boolean;
}

export interface PositionLabelResponse {
  id: string;
  path: string;
  scanCode: string;
  address: string;
}

const MAGASIN = "/api/v1/physical/magasin";

async function magasinPost<T>(path: string, body: object): Promise<T> {
  const r = await fetch(`${MAGASIN}${path}`, {
    method: "POST",
    headers: { ...accessHeaders(), "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return parse<T>(r);
}

export async function scanCode(code: string): Promise<ScanResponse> {
  const r = await fetch(`${MAGASIN}/scan?${new URLSearchParams({ code })}`, { headers: accessHeaders() });
  return parse<ScanResponse>(r);
}

export const recordArrival = (barcode: string) => magasinPost<ArrivalResponse>("/arrivals", { barcode });

export const placeFromScan = (containerId: string, positionId: string) =>
  magasinPost<PhysicalContainerResponse>("/placements", { containerId, positionId });

export const removeFromShelf = (containerId: string, positionId: string) =>
  magasinPost<PhysicalContainerResponse>("/removals", { containerId, positionId });

export async function listPositionLabels(nodeId: string): Promise<PositionLabelResponse[]> {
  const r = await fetch(`${MAGASIN}/locations/${nodeId}/position-labels`, { headers: accessHeaders() });
  return parse<PositionLabelResponse[]>(r);
}

/** Planche Avery L7169 des étiquettes d'emplacement sous un nœud (fetch + blob : en-têtes requis). */
export async function downloadPositionLabelsPdf(nodeId: string, fileName: string): Promise<void> {
  const response = await fetch(`${MAGASIN}/locations/${nodeId}/position-labels.pdf`, { headers: accessHeaders() });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as ProblemDetail;
    throw new Error(body.detail || body.title || `HTTP ${response.status}`);
  }
  const url = URL.createObjectURL(await response.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
