import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { Suspense } from 'react';
import { Filters } from '@/components/Filters';
import { Pagination } from '@/components/Pagination';
import { ProductGrid } from '@/components/ProductCard';
import { serverGet } from '@/lib/server-api';
import type { CategoryDetail, Page, ProductCard } from '@/lib/types';

type Props = { params: Promise<{ slug: string }>; searchParams: Promise<Record<string, string | undefined>> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const d = await serverGet<CategoryDetail>(`/categories/${slug}`);
  if (!d) return {};
  return { title: d.category.name, description: d.category.description, alternates: { canonical: `/c/${slug}` } };
}

export default async function CategoryPage({ params, searchParams }: Props) {
  const { slug } = await params;
  const sp = await searchParams;
  const detail = await serverGet<CategoryDetail>(`/categories/${slug}`);
  if (!detail) notFound();

  const qs = new URLSearchParams({ category: slug });
  for (const [k, v] of Object.entries(sp)) if (v) qs.set(k, v);
  const products = await serverGet<Page<ProductCard>>(`/products?${qs.toString()}`, 60);
  const base: Record<string, string> = {};
  for (const [k, v] of Object.entries(sp)) if (v && k !== 'page') base[k] = v;

  return (
    <div className="container-page py-8">
      <h1 className="text-3xl">{detail.category.name}</h1>
      {detail.category.description && <p className="mt-2 max-w-2xl text-sand-600">{detail.category.description}</p>}
      <div className="my-6">
        <Suspense>
          <Filters defs={detail.category.attributeDefs} facets={detail.facets} />
        </Suspense>
      </div>
      <ProductGrid items={products?.items || []} />
      <Pagination page={products?.page || 0} totalPages={products?.totalPages || 0} baseParams={base} path={`/c/${slug}`} />
    </div>
  );
}
