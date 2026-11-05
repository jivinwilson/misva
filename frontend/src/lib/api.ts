const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export interface ProblemDetail {
  status: number;
  title: string;
  detail?: string;
  code?: string;
  traceId?: string;
  errors?: { field: string; message: string }[];
}

export class ApiError extends Error {
  constructor(public readonly problem: ProblemDetail) {
    super(problem.detail ?? problem.title);
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}/api/v1${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
    cache: "no-store",
  });
  if (!res.ok) {
    const problem = (await res.json().catch(() => null)) as ProblemDetail | null;
    throw new ApiError(problem ?? { status: res.status, title: res.statusText });
  }
  return (await res.json()) as T;
}

export interface HealthResponse {
  status: string;
  service: string;
  env: string;
}

export const getHealth = () => apiFetch<HealthResponse>("/health");
