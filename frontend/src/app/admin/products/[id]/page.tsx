'use client';

import Link from 'next/link';
import { use, useState } from 'react';
import { ImageManager } from '@/components/admin/ImageManager';
import { ProductForm, type ProductAdmin } from '@/components/admin/ProductForm';
import { VariantEditor } from '@/components/admin/VariantEditor';
import { api, errorMessage } from '@/lib/api';
import type { Category } from '@/lib/types';
import { useApi } from '@/lib/useApi';

export default function EditProduct({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { data: p, reload, setData } = useApi<ProductAdmin>(`/admin/products/${id}`);
  const { data: categories } = useApi<Category[]>('/admin/categories');
  const [err, setErr] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  if (!p || !categories) return <p className="text-sand-600">Loading…</p>;
  const category = categories.find((c) => c.id === p.categoryId);

  async function setStatus(status: string) {
    setErr(null);
    try {
      setData(await api<ProductAdmin>(`/admin/products/${id}/status`, { method: 'POST', body: { status } }));
    } catch (e) {
      setErr(errorMessage(e));
    }
  }

  return (
    <div className="max-w-5xl space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl">{p.title}</h1>
        <span className="badge bg-sand-100">{p.status}</span>
        <div className="ml-auto flex gap-2">
          {p.status === 'ACTIVE' && <Link href={`/products/${p.slug}`} target="_blank" className="btn-secondary">View in store</Link>}
          {p.status !== 'ACTIVE' && <button className="btn-primary" onClick={() => setStatus('ACTIVE')}>Publish</button>}
          {p.status === 'ACTIVE' && <button className="btn-secondary" onClick={() => setStatus('DRAFT')}>Unpublish</button>}
          {p.status !== 'ARCHIVED' && <button className="btn-secondary" onClick={() => confirm('Archive this product?') && setStatus('ARCHIVED')}>Archive</button>}
        </div>
      </div>
      {err && <p className="text-sm text-red-700">{err}</p>}
      <ProductForm categories={categories} initial={p} onSaved={(np) => { setData(np); setSaved(true); }} />
      {saved && <p className="text-sm text-green-700">Saved.</p>}
      <VariantEditor productId={p.id} variants={p.variants} defs={category?.attributeDefs || []} onChange={reload} />
      <ImageManager productId={p.id} images={p.images} onChange={reload} />
    </div>
  );
}
