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

export function batchLabelsPdfUrl(batchId: string, template = "AVERY_L7160", symbology = "CODE128"): string {
  const qs = new URLSearchParams({ template, symbology });
  return `/api/v1/physical/batches/${batchId}/labels.pdf?${qs.toString()}`;
}
