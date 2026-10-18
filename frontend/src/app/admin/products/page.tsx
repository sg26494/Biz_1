'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useState } from 'react';
import { istDate, money } from '@/lib/format';
import type { Page } from '@/lib/types';
import { useApi } from '@/lib/useApi';

type Row = { id: number; title: string; slug: string; status: string; categoryName: string; variantCount: number;
  minPricePaise?: number; totalAvailable: number; imageUrl?: string; updatedAt: string };

export default function AdminProducts() {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const { data } = useApi<Page<Row>>(`/admin/products?q=${encodeURIComponent(q)}&status=${status}&page=${page}`);
  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="text-2xl">Products</h1>
        <Link href="/admin/products/new" className="btn-primary">New product</Link>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        <input className="input max-w-xs" placeholder="Search title or SKU" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        <select className="input w-auto" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All statuses</option><option>DRAFT</option><option>ACTIVE</option><option>ARCHIVED</option>
        </select>
      </div>
      <div className="card mt-4 overflow-x-auto">
        <table className="table">
          <thead><tr><th></th><th>Title</th><th>Category</th><th>Status</th><th>Variants</th><th>From</th><th>Stock</th><th>Updated</th></tr></thead>
          <tbody>
            {data?.items.map((p) => (
              <tr key={p.id}>
                <td className="w-12"><div className="relative h-12 w-10 overflow-hidden rounded bg-sand-100">
                  {p.imageUrl && <Image src={p.imageUrl} alt="" fill sizes="40px" className="object-cover" />}</div></td>
                <td><Link href={`/admin/products/${p.id}`} className="font-medium hover:text-clay-600">{p.title}</Link></td>
                <td>{p.categoryName}</td><td>{p.status}</td><td>{p.variantCount}</td>
                <td>{money(p.minPricePaise)}</td><td>{p.totalAvailable}</td><td>{istDate(p.updatedAt, false)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {data && data.totalPages > 1 && (
        <div className="mt-4 flex gap-2">
          <button className="btn-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <button className="btn-secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </div>
  );
}
