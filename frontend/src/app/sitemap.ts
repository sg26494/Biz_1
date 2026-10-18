import type { MetadataRoute } from 'next';
import { SITE_URL } from '@/lib/format';
import { serverGet } from '@/lib/server-api';
import type { Category } from '@/lib/types';

export const revalidate = 3600;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const [products, categories] = await Promise.all([
    serverGet<{ slug: string; updatedAt: string }[]>('/meta/sitemap', 3600),
    serverGet<Category[]>('/categories', 3600),
  ]);
  const pages = ['about', 'contact', 'shipping', 'returns', 'privacy', 'terms'];
  return [
    { url: SITE_URL, changeFrequency: 'daily', priority: 1 },
    ...(categories || []).map((c) => ({ url: `${SITE_URL}/c/${c.slug}`, changeFrequency: 'daily' as const, priority: 0.8 })),
    ...(products || []).map((p) => ({ url: `${SITE_URL}/products/${p.slug}`, lastModified: p.updatedAt, priority: 0.7 })),
    ...pages.map((s) => ({ url: `${SITE_URL}/pages/${s}`, priority: 0.3 })),
  ];
}
