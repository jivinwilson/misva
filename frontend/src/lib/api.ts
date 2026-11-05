const API_BASE = process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export interface ProblemDetail {
  status: number;
  title: string;
  detail?: string;
  code?: string;
  traceId?: string;
  errors?: { field: string; message: string }[];
  businesses?: BusinessRef[];
}

export interface BusinessRef {
  businessId: string;
  name: string;
  slug: string;
}

export class ApiError extends Error {
  constructor(public readonly problem: ProblemDetail) {
    super(problem.detail ?? problem.title);
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit & { token?: string }): Promise<T> {
  const { token, headers, ...rest } = init ?? {};
  const res = await fetch(`${API_BASE}/api/v1${path}`, {
    ...rest,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    cache: "no-store",
  });
  if (!res.ok) {
    const problem = (await res.json().catch(() => null)) as ProblemDetail | null;
    throw new ApiError(problem ?? { status: res.status, title: res.statusText });
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export interface HealthResponse {
  status: string;
  service: string;
  env: string;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

export interface Registration {
  businessId: string;
  slug: string;
  headOfficeBranchId: string;
  tokens: TokenPair;
}

export interface Me {
  userId: string;
  businessId: string | null;
  permissions: string[];
}

export interface BusinessSummary {
  id: string;
  name: string;
  slug: string;
  industry: string;
  country: string;
  currency: string;
  timezone: string;
  status: string;
}

export interface Branch {
  id: string;
  code: string;
  name: string;
  status: string;
  headOffice: boolean;
}

export const getHealth = () => apiFetch<HealthResponse>("/health");
