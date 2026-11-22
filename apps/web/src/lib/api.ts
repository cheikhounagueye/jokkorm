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
}

export async function createContainer(input: {
  barcode: string;
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
