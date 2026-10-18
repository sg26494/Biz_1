'use client';

import { useState } from 'react';
import { api, errorMessage } from '@/lib/api';
import { istDate } from '@/lib/format';
import { useApi } from '@/lib/useApi';

type Row = { variantId: number; sku: string; variantTitle: string; productId: number; productTitle: string;
  productStatus: string; onHand: number; reserved: number; available: number; lowStockThreshold: number; lowStock: boolean };
type Movement = { id: number; sku: string; movementType: string; deltaOnHand: number; deltaReserved: number;
  onHandAfter: number; reservedAfter: number; refType?: string; refId?: string; note?: string; actorEmail?: string; createdAt: string };

export default function AdminInventory() {
  const [q, setQ] = useState('');
  const [lowOnly, setLowOnly] = useState(false);
  const { data, reload } = useApi<Row[]>(`/admin/inventory?q=${encodeURIComponent(q)}&lowOnly=${lowOnly}`);
  const [adjusting, setAdjusting] = useState<Row | null>(null);
  const [historyFor, setHistoryFor] = useState<number | null>(null);
  const { data: moves } = useApi<Movement[]>(historyFor ? `/admin/inventory/movements?variantId=${historyFor}` : null);
  const [form, setForm] = useState({ reason: 'PURCHASE', delta: '', note: '' });
  const [err, setErr] = useState<string | null>(null);

  async function adjust(e: React.FormEvent) {
    e.preventDefault();
    if (!adjusting) return;
    setErr(null);
    try {
      await api('/admin/inventory/adjust', { method: 'POST', body: {
        variantId: adjusting.variantId, reason: form.reason, delta: Number(form.delta), note: form.note || undefined } });
      setAdjusting(null);
      setForm({ reason: 'PURCHASE', delta: '', note: '' });
      reload();
    } catch (e2) {
      setErr(errorMessage(e2));
    }
  }

  return (
    <div>
      <h1 className="text-2xl">Inventory</h1>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <input className="input max-w-xs" placeholder="Search SKU or product" value={q} onChange={(e) => setQ(e.target.value)} />
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} /> Low stock only</label>
      </div>
      {adjusting && (
        <form onSubmit={adjust} className="card mt-4 grid gap-3 p-4 sm:grid-cols-4">
          <p className="text-sm sm:col-span-4"><strong>{adjusting.sku}</strong> · {adjusting.productTitle} ({adjusting.variantTitle}) · on hand {adjusting.onHand}, reserved {adjusting.reserved}</p>
          <div><label className="label">Reason</label>
            <select className="input" value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })}>
              <option value="PURCHASE">Purchase (new stock)</option><option value="DAMAGE">Damage</option><option value="CORRECTION">Correction</option>
            </select></div>
          <div><label className="label">Change (+ / −)</label><input className="input" required type="number" value={form.delta} onChange={(e) => setForm({ ...form, delta: e.target.value })} /></div>
          <div className="sm:col-span-2"><label className="label">Note</label><input className="input" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} placeholder="e.g. Invoice #123 from supplier" /></div>
          <div className="flex gap-2 sm:col-span-4"><button className="btn-primary">Save</button><button type="button" className="btn-secondary" onClick={() => setAdjusting(null)}>Cancel</button>
            {err && <span className="text-sm text-red-700">{err}</span>}</div>
        </form>
      )}
      <div className="card mt-4 overflow-x-auto">
        <table className="table">
          <thead><tr><th>SKU</th><th>Product</th><th>Variant</th><th>On hand</th><th>Reserved</th><th>Available</th><th></th></tr></thead>
          <tbody>{data?.map((r) => (
            <tr key={r.variantId} className={r.lowStock ? 'bg-clay-50' : ''}>
              <td className="font-mono text-xs">{r.sku}</td><td>{r.productTitle}</td><td>{r.variantTitle}</td>
              <td>{r.onHand}</td><td>{r.reserved}</td><td className="font-medium">{r.available}</td>
              <td className="whitespace-nowrap"><button className="btn-link" onClick={() => setAdjusting(r)}>Adjust</button>
                <button className="btn-link ml-3" onClick={() => setHistoryFor(r.variantId)}>History</button></td>
            </tr>
          ))}</tbody>
        </table>
      </div>
      {historyFor && (
        <div className="card mt-6 overflow-x-auto p-4">
          <div className="flex justify-between"><h2 className="text-lg">Movements</h2><button className="btn-link" onClick={() => setHistoryFor(null)}>Close</button></div>
          <table className="table mt-2">
            <thead><tr><th>When</th><th>Type</th><th>On hand Δ</th><th>Reserved Δ</th><th>After</th><th>Ref</th><th>By</th><th>Note</th></tr></thead>
            <tbody>{moves?.map((m) => (
              <tr key={m.id}><td>{istDate(m.createdAt)}</td><td>{m.movementType}</td><td>{m.deltaOnHand}</td><td>{m.deltaReserved}</td>
                <td>{m.onHandAfter}/{m.reservedAfter}</td><td>{m.refType} {m.refId}</td><td>{m.actorEmail || 'system'}</td><td>{m.note}</td></tr>
            ))}</tbody>
          </table>
        </div>
      )}
    </div>
  );
}
