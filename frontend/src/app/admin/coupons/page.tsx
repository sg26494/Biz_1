'use client';

import { useState } from 'react';
import { api, errorMessage } from '@/lib/api';
import { istDate, money, paiseToRupees, rupeesToPaise } from '@/lib/format';
import { useApi } from '@/lib/useApi';

type Coupon = { id: number; code: string; description?: string; type: 'PERCENT' | 'FLAT'; discountValue: number;
  minOrderPaise: number; maxDiscountPaise?: number; startsAt?: string; endsAt?: string; usageLimitTotal?: number;
  usageLimitPerUser?: number; active: boolean; timesUsed: number; totalDiscountPaise: number };

const empty = { code: '', description: '', type: 'PERCENT', value: '', minOrder: '', maxDiscount: '', startsAt: '', endsAt: '', limitTotal: '', limitUser: '1' };

export default function AdminCoupons() {
  const { data, reload } = useApi<Coupon[]>('/admin/coupons');
  const [f, setF] = useState<typeof empty | null>(null);
  const [editId, setEditId] = useState<number | null>(null);
  const [err, setErr] = useState<string | null>(null);

  function edit(c?: Coupon) {
    setErr(null);
    setEditId(c?.id ?? null);
    setF(c ? {
      code: c.code, description: c.description || '', type: c.type,
      value: c.type === 'PERCENT' ? String(c.discountValue / 100) : paiseToRupees(c.discountValue),
      minOrder: paiseToRupees(c.minOrderPaise), maxDiscount: paiseToRupees(c.maxDiscountPaise),
      startsAt: c.startsAt?.slice(0, 16) || '', endsAt: c.endsAt?.slice(0, 16) || '',
      limitTotal: c.usageLimitTotal ? String(c.usageLimitTotal) : '', limitUser: c.usageLimitPerUser ? String(c.usageLimitPerUser) : '',
    } : { ...empty });
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!f) return;
    const body = {
      code: f.code, description: f.description || undefined, type: f.type,
      discountValue: f.type === 'PERCENT' ? Math.round(parseFloat(f.value) * 100) : rupeesToPaise(f.value),
      minOrderPaise: f.minOrder ? rupeesToPaise(f.minOrder) : 0,
      maxDiscountPaise: f.maxDiscount ? rupeesToPaise(f.maxDiscount) : undefined,
      startsAt: f.startsAt ? new Date(f.startsAt).toISOString() : undefined,
      endsAt: f.endsAt ? new Date(f.endsAt).toISOString() : undefined,
      usageLimitTotal: f.limitTotal ? Number(f.limitTotal) : undefined,
      usageLimitPerUser: f.limitUser ? Number(f.limitUser) : undefined, active: true,
    };
    try {
      if (editId) await api(`/admin/coupons/${editId}`, { method: 'PUT', body });
      else await api('/admin/coupons', { method: 'POST', body });
      setF(null);
      reload();
    } catch (e2) {
      setErr(errorMessage(e2));
    }
  }

  const inp = (k: keyof typeof empty, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => f && (
    <div><label className="label">{label}</label><input className="input" value={f[k]} onChange={(e) => setF({ ...f, [k]: e.target.value })} {...props} /></div>
  );

  return (
    <div>
      <div className="flex items-center justify-between"><h1 className="text-2xl">Coupons</h1>
        <button className="btn-primary" onClick={() => edit()}>New coupon</button></div>
      {f && (
        <form onSubmit={save} className="card mt-4 grid gap-3 p-4 sm:grid-cols-3">
          {inp('code', 'Code', { required: true })}
          <div><label className="label">Type</label><select className="input" value={f.type} onChange={(e) => setF({ ...f, type: e.target.value })}>
            <option value="PERCENT">Percent off</option><option value="FLAT">Flat ₹ off</option></select></div>
          {inp('value', f.type === 'PERCENT' ? 'Percent (e.g. 10)' : 'Amount ₹', { required: true, inputMode: 'decimal' })}
          {inp('minOrder', 'Min order ₹')}
          {inp('maxDiscount', 'Max discount ₹')}
          {inp('description', 'Description')}
          {inp('startsAt', 'Starts', { type: 'datetime-local' })}
          {inp('endsAt', 'Ends', { type: 'datetime-local' })}
          {inp('limitTotal', 'Total uses (blank = no limit)')}
          {inp('limitUser', 'Uses per customer')}
          <div className="flex items-end gap-2 sm:col-span-3"><button className="btn-primary">Save</button>
            <button type="button" className="btn-secondary" onClick={() => setF(null)}>Cancel</button>{err && <span className="text-sm text-red-700">{err}</span>}</div>
        </form>
      )}
      <table className="table card mt-4">
        <thead><tr><th>Code</th><th>Discount</th><th>Min order</th><th>Valid</th><th>Used</th><th>Given</th><th>Active</th><th></th></tr></thead>
        <tbody>{data?.map((c) => (
          <tr key={c.id}><td className="font-mono">{c.code}</td>
            <td>{c.type === 'PERCENT' ? `${c.discountValue / 100}%${c.maxDiscountPaise ? ` (max ${money(c.maxDiscountPaise)})` : ''}` : money(c.discountValue)}</td>
            <td>{money(c.minOrderPaise)}</td><td className="text-xs">{istDate(c.startsAt, false) || '—'} → {istDate(c.endsAt, false) || '—'}</td>
            <td>{c.timesUsed}{c.usageLimitTotal ? ` / ${c.usageLimitTotal}` : ''}</td><td>{money(c.totalDiscountPaise)}</td>
            <td><button className="btn-link" onClick={async () => { await api(`/admin/coupons/${c.id}/active?value=${!c.active}`, { method: 'POST' }); reload(); }}>{c.active ? 'Disable' : 'Enable'}</button></td>
            <td><button className="btn-link" onClick={() => edit(c)}>Edit</button></td></tr>
        ))}</tbody>
      </table>
    </div>
  );
}
