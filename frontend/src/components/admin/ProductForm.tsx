'use client';

import { useState } from 'react';
import { api, ApiError, errorMessage } from '@/lib/api';
import type { Category } from '@/lib/types';

export type ProductAdmin = {
  id: number; categoryId: number; title: string; slug: string; description: string; status: string; hsnCode: string;
  gstRateBp: number; returnable: boolean; attributes: Record<string, string>; seoTitle?: string; seoDescription?: string;
  variants: VariantAdmin[]; images: { id: number; variantId?: number; url: string; alt?: string }[];
};

export type VariantAdmin = {
  id: number; productId: number; sku: string; title: string; attributes: Record<string, string>; mrpPaise: number;
  pricePaise: number; weightGrams: number; lengthCm: number; breadthCm: number; heightCm: number; active: boolean;
  sortOrder: number; onHand: number; reserved: number;
};

/** GST rates in basis points; confirm the right one per HSN with your CA. */
const GST_RATES = [0, 500, 1200, 1800];

export function ProductForm({ categories, initial, onSaved }: {
  categories: Category[]; initial?: ProductAdmin; onSaved: (p: ProductAdmin) => void;
}) {
  const [f, setF] = useState({
    categoryId: initial?.categoryId || categories[0]?.id || 0,
    title: initial?.title || '', slug: initial?.slug || '', description: initial?.description || '',
    hsnCode: initial?.hsnCode || '', gstRateBp: initial?.gstRateBp ?? 500, returnable: initial?.returnable ?? true,
    seoTitle: initial?.seoTitle || '', seoDescription: initial?.seoDescription || '',
    attributes: Object.entries(initial?.attributes || {}).map(([k, v]) => `${k}: ${v}`).join('\n'),
  });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    setFields({});
    const attributes: Record<string, string> = {};
    f.attributes.split('\n').map((l) => l.split(':')).forEach(([k, ...v]) => {
      if (k?.trim() && v.length) attributes[k.trim().toLowerCase().replace(/\s+/g, '_')] = v.join(':').trim();
    });
    const body = { ...f, slug: f.slug || undefined, attributes, seoTitle: f.seoTitle || undefined,
      seoDescription: f.seoDescription || undefined };
    try {
      onSaved(initial
        ? await api<ProductAdmin>(`/admin/products/${initial.id}`, { method: 'PUT', body })
        : await api<ProductAdmin>('/admin/products', { method: 'POST', body }));
    } catch (e2) {
      if (e2 instanceof ApiError) setFields(e2.fieldErrors());
      setErr(errorMessage(e2));
    } finally {
      setBusy(false);
    }
  }

  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setF({ ...f, [k]: e.target.type === 'checkbox' ? (e.target as HTMLInputElement).checked
      : ['categoryId', 'gstRateBp'].includes(k) ? Number(e.target.value) : e.target.value });

  return (
    <form onSubmit={submit} className="card grid gap-4 p-5 sm:grid-cols-2">
      <div className="sm:col-span-2"><label className="label">Title</label><input className="input" required value={f.title} onChange={set('title')} />
        {fields.title && <p className="field-error">{fields.title}</p>}</div>
      <div><label className="label">Category</label>
        <select className="input" value={f.categoryId} onChange={set('categoryId')}>
          {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select></div>
      <div><label className="label">URL slug (blank = from title)</label><input className="input" value={f.slug} onChange={set('slug')} placeholder="jaipuri-razai-indigo" />
        {fields.slug && <p className="field-error">{fields.slug}</p>}</div>
      <div className="sm:col-span-2"><label className="label">Description</label>
        <textarea className="input min-h-32" value={f.description} onChange={set('description')} /></div>
      <div><label className="label">HSN code</label><input className="input" required value={f.hsnCode} onChange={set('hsnCode')} placeholder="6304" />
        {fields.hsnCode && <p className="field-error">{fields.hsnCode}</p>}</div>
      <div><label className="label">GST rate</label>
        <select className="input" value={f.gstRateBp} onChange={set('gstRateBp')}>
          {GST_RATES.map((r) => <option key={r} value={r}>{r / 100}%</option>)}
        </select></div>
      <div className="sm:col-span-2"><label className="label">Product details (one per line, e.g. &quot;fabric: Cotton&quot;)</label>
        <textarea className="input min-h-24 font-mono text-xs" value={f.attributes} onChange={set('attributes')} /></div>
      <div><label className="label">SEO title (optional)</label><input className="input" value={f.seoTitle} onChange={set('seoTitle')} /></div>
      <div><label className="label">SEO description (optional)</label><input className="input" value={f.seoDescription} onChange={set('seoDescription')} /></div>
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={f.returnable} onChange={set('returnable')} /> Returnable</label>
      <div className="flex items-center gap-3 sm:col-span-2">
        <button className="btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save product'}</button>
        {err && <span className="text-sm text-red-700">{err}</span>}
      </div>
    </form>
  );
}
