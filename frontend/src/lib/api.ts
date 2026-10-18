'use client';

import type { ApiError as ApiErrorBody, LoginResponse } from './types';

/** Client-side API calls. Access token lives in memory only; the refresh token is an HttpOnly cookie. */

export class ApiError extends Error {
  status: number;
  code: string;
  details?: Record<string, unknown>;
  constructor(status: number, body: Partial<ApiErrorBody>) {
    super(body.message || 'Something went wrong');
    this.status = status;
    this.code = body.code || 'ERROR';
    this.details = body.details;
  }
  fieldErrors(): Record<string, string> {
    return ((this.details?.fields as Record<string, string>) || {});
  }
}

let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
const listeners = new Set<(r: LoginResponse | null) => void>();

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function onSessionChange(fn: (r: LoginResponse | null) => void) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/** One refresh at a time; concurrent 401s wait for the same attempt. */
export function refreshSession(): Promise<boolean> {
  if (!refreshing) {
    refreshing = fetch('/api/v1/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(async (res) => {
        if (!res.ok) {
          accessToken = null;
          listeners.forEach((l) => l(null));
          return false;
        }
        const body = (await res.json()) as LoginResponse;
        if (body.status === 'LOGGED_IN') {
          accessToken = body.accessToken;
          listeners.forEach((l) => l(body));
          return true;
        }
        return false;
      })
      .catch(() => false)
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

type Options = { method?: string; body?: unknown; idempotencyKey?: string; retry?: boolean };

export async function api<T>(path: string, opts: Options = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (opts.idempotencyKey) headers['Idempotency-Key'] = opts.idempotencyKey;

  const res = await fetch(`/api/v1${path}`, {
    method: opts.method || 'GET',
    headers,
    credentials: 'include',
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });

  // Expired access token: refresh once, then retry the original request.
  if (res.status === 401 && opts.retry !== false && !path.startsWith('/auth/')) {
    if (await refreshSession()) return api<T>(path, { ...opts, retry: false });
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) throw new ApiError(res.status, data || {});
  return data as T;
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : 'Something went wrong';
}
