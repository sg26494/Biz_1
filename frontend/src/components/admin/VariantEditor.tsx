'use client';

import { useState } from 'react';
import { api, ApiError, errorMessage } from '@/lib/api';
import { money, paiseToRupees, rupeesToPaise } from '@/lib/format';
import type { AttributeDef } from '@/lib/types';
import type { VariantAdmin } from './ProductForm';

export function VariantEditor({ productId, variants, defs, onChange }: {
  productId: number; variants: VariantAdmin[]; defs: AttributeDef[]; onChange: () => void;
}) {
  const [editing, setEditing] = useState<VariantAdmin | 'new' | null>(null);
  return (
    <section className="card p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-lg">Variants</h2>
        {!editing && <button className="btn-secondary" onClick={() => setEditing('new')}>Add variant</button>}
      </div>
      <div className="mt-3 overflow-x-auto">
        <table className="table">
          <thead><tr><th>SKU</th><th>Title</th><th>Price</th><th>MRP</th><th>Weight</th><th>On hand</th><th>Reserved</th><th>Active</th><th></th></tr></thead>
          <tbody>
            {variants.map((v) => (
              <tr key={v.id}>
                <td className="font-mono text-xs">{v.sku}</td><td>{v.title}</td><td>{money(v.pricePaise)}</td>
                <td>{money(v.mrpPaise)}</td><td>{v.weightGrams} g</td><td>{v.onHand}</td><td>{v.reserved}</td>
                <td>{v.active ? 'Yes' : 'No'}</td>
                <td><button className="btn-link" onClick={() => setEditing(v)}>Edit</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editing && (
        <VariantForm productId={productId} defs={defs} initial={editing === 'new' ? undefined : editing}
          onDone={() => { setEditing(null); onChange(); }} onCancel={() => setEditing(null)} />
      )}
      <p className="mt-3 text-xs text-sand-600">Stock is changed on the Inventory page (with a reason).</p>
    </section>
  );
}

function VariantForm({ productId, defs, initial, onDone, onCancel }: {
  productId: number; defs: AttributeDef[]; initial?: VariantAdmin; onDone: () => void; onCancel: () => void;
}) {
  const [f, setF] = useState({
    sku: initial?.sku || '', title: initial?.title || '',
    price: paiseToRupees(initial?.pricePaise), mrp: paiseToRupees(initial?.mrpPaise),
    weightGrams: String(initial?.weightGrams || ''), lengthCm: String(initial?.lengthCm || ''),
    breadthCm: String(initial?.breadthCm || ''), heightCm: String(initial?.heightCm || ''),
    active: initial?.active ?? true,
  });
  const [attrs, setAttrs] = useState<Record<string, string>>({ ...(initial?.attributes || {}) });
  const [err, setErr] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setErr(null);
    const cleanAttrs = Object.fromEntries(Object.entries(attrs).filter(([, v]) => v.trim()));
    const body = {
      productId, sku: f.sku, title: f.title || Object.values(cleanAttrs).join(' / ') || f.sku,
      attributes: cleanAttrs, pricePaise: rupeesToPaise(f.price), mrpPaise: rupeesToPaise(f.mrp),
      weightGrams: Number(f.weightGrams), lengthCm: Number(f.lengthCm), breadthCm: Number(f.breadthCm),
      heightCm: Number(f.heightCm), active: f.active,
    };
    try {
      if (initial) await api(`/admin/variants/${initial.id}`, { method: 'PUT', body });
      else await api('/admin/variants', { method: 'POST', body });
      onDone();
    } catch (e2) {
      if (e2 instanceof ApiError) setFields(e2.fieldErrors());
      setErr(errorMessage(e2));
    }
  }

  const inp = (k: keyof typeof f, label: string, extra: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <div><label className="label">{label}</label>
      <input className="input" value={f[k] as string} onChange={(e) => setF({ ...f, [k]: e.target.value })} {...extra} />
      {fields[k] && <p className="field-error">{fields[k]}</p>}</div>
  );

  return (
    <form onSubmit={submit} className="mt-4 grid gap-3 rounded-md bg-sand-50 p-4 sm:grid-cols-4">
      {inp('sku', 'SKU', { required: true })}
      {inp('title', 'Title (blank = from attributes)')}
      {inp('price', 'Selling price ₹ (incl. GST)', { required: true, inputMode: 'decimal' })}
      {inp('mrp', 'MRP ₹', { required: true, inputMode: 'decimal' })}
      {defs.map((d) => (
        <div key={d.key}><label className="label">{d.label}</label>
          {d.options?.length ? (
            <select className="input" value={attrs[d.key] || ''} onChange={(e) => setAttrs({ ...attrs, [d.key]: e.target.value })}>
              <option value="">—</option>{d.options.map((o) => <option key={o}>{o}</option>)}
            </select>
          ) : (
            <input className="input" value={attrs[d.key] || ''} onChange={(e) => setAttrs({ ...attrs, [d.key]: e.target.value })} />
          )}
        </div>
      ))}
      {inp('weightGrams', 'Packed weight (g)', { required: true, inputMode: 'numeric' })}
      {inp('lengthCm', 'Box length (cm)', { required: true, inputMode: 'decimal' })}
      {inp('breadthCm', 'Box breadth (cm)', { required: true, inputMode: 'decimal' })}
      {inp('heightCm', 'Box height (cm)', { required: true, inputMode: 'decimal' })}
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} /> Active</label>
      <div className="flex items-center gap-2 sm:col-span-4">
        <button className="btn-primary">Save variant</button>
        <button type="button" className="btn-secondary" onClick={onCancel}>Cancel</button>
        {err && <span className="text-sm text-red-700">{err}</span>}
      </div>
    </form>
  );
}
