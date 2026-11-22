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
}

const DEFAULT_TENANT =
  (typeof window !== "undefined" && window.localStorage.getItem("jokkorm.tenant")) ||
  "demo";

function headers(extra: Record<string, string> = {}): HeadersInit {
  return {
    "Accept": "application/json",
    "X-Tenant-Id": DEFAULT_TENANT,
    ...extra,
  };
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
  };
}

export async function getConsolidatedRecord(cote: string): Promise<ConsolidatedRecordResponse> {
  const qs = new URLSearchParams({ cote });
  const response = await fetch(`/api/v1/records/consolidated?${qs.toString()}`, { headers: headers() });
  return parse<ConsolidatedRecordResponse>(response);
}

export async function storeRecord(cote: string, containerBarcode: string): Promise<RecordResponse> {
  const qs = new URLSearchParams({ cote });
  const response = await fetch(`/api/v1/records/store?${qs.toString()}`, {
    method: "POST",
    headers: { ...headers(), "Content-Type": "application/json" },
    body: JSON.stringify({ containerBarcode }),
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
  createdAt: string;
  updatedAt: string;
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

export function getCurrentRole(): UserRole {
  if (typeof window === "undefined") return "REQUESTER";
  return (localStorage.getItem(ROLE_KEY) as UserRole) || "REQUESTER";
}
export function setCurrentRole(role: UserRole) {
  if (typeof window !== "undefined") localStorage.setItem(ROLE_KEY, role);
}
export function getCurrentActor(): string {
  if (typeof window === "undefined") return "requester";
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

// ─── Import Excel topographie (phase 5I) ──────────────────────────────────────

export interface ImportTopographyReport {
  created: number;
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
    headers: headers(), // ne pas ajouter Content-Type, FormData le fait avec boundary
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
    headers: { ...headers(), "Content-Type": "application/json" },
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
