import { ProductGrid } from '@/components/ProductCard';
import { Pagination } from '@/components/Pagination';
import { serverGet } from '@/lib/server-api';
import type { Page, ProductCard } from '@/lib/types';

export const metadata = { title: 'Search', robots: { index: false } };

export default async function SearchPage({ searchParams }: { searchParams: Promise<{ q?: string; page?: string }> }) {
  const { q = '', page = '0' } = await searchParams;
  const results = q.trim()
    ? await serverGet<Page<ProductCard>>(`/search?q=${encodeURIComponent(q)}&page=${page}`, 60)
    : null;
  return (
    <div className="container-page py-8">
      <form action="/search" className="flex max-w-lg gap-2">
        <input name="q" defaultValue={q} className="input" placeholder="Search products" aria-label="Search" />
        <button className="btn-primary">Search</button>
      </form>
      {q && <p className="mt-6 text-sm text-sand-600">{results?.total ?? 0} results for “{q}”</p>}
      <div className="mt-6">{results && <ProductGrid items={results.items} />}</div>
      {results && <Pagination page={results.page} totalPages={results.totalPages} baseParams={{ q }} path="/search" />}
    </div>
  );
}
