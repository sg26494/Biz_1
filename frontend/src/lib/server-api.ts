import 'server-only';

const API = process.env.API_INTERNAL_URL || 'http://localhost:8080';

/**
 * Server-side fetch for public data (catalog, pages). ISR: `revalidate` seconds as a safety net; admin edits
 * trigger on-demand revalidation through /api/revalidate.
 */
export async function serverGet<T>(path: string, revalidate = 300): Promise<T | null> {
  try {
    const res = await fetch(`${API}/api/v1${path}`, { next: { revalidate } });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`API ${res.status} for ${path}`);
    return (await res.json()) as T;
  } catch (e) {
    console.error('serverGet failed', path, e);
    return null;
  }
}
