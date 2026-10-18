'use client';

import Link from 'next/link';
import { useRef, useState } from 'react';
import { api, errorMessage, newIdempotencyKey } from '@/lib/api';
import { istDate, money, rupeesToPaise } from '@/lib/format';
import type { RefundRow } from '@/lib/types';
import { useApi } from '@/lib/useApi';

type PaymentRow = { id: number; orderId: number; orderNumber: string; gatewayOrderId: string; gatewayPaymentId?: string;
  amountPaise: number; status: string; method?: string; errorCode?: string; capturedAt?: string; createdAt: string };
type Mismatch = { id: number; runDate: string; gatewayPaymentId?: string; gatewayOrderId?: string; orderId?: number;
  orderNumber?: string; kind: string; detail: string; createdAt: string };

export default function AdminPayments() {
  const [tab, setTab] = useState<'payments' | 'refunds' | 'mismatches'>('payments');
  const payments = useApi<PaymentRow[]>(tab === 'payments' ? '/admin/payments' : null);
  const refunds = useApi<RefundRow[]>(tab === 'refunds' ? '/admin/refunds' : null);
  const mismatches = useApi<Mismatch[]>(tab === 'mismatches' ? '/admin/payments/mismatches' : null);
  const [refund, setRefund] = useState({ orderId: '', amount: '', reason: '' });
  const [msg, setMsg] = useState<string | null>(null);
  const key = useRef(newIdempotencyKey());

  async function createRefund(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    if (!confirm(`Refund ₹${refund.amount} on order id ${refund.orderId}?`)) return;
    try {
      await api('/admin/refunds', { method: 'POST', idempotencyKey: key.current, body: {
        orderId: Number(refund.orderId), amountPaise: rupeesToPaise(refund.amount), reason: refund.reason } });
      key.current = newIdempotencyKey();
      setRefund({ orderId: '', amount: '', reason: '' });
      setMsg('Refund queued');
      refunds.reload();
    } catch (e2) {
      setMsg(errorMessage(e2));
    }
  }

  return (
    <div>
      <h1 className="text-2xl">Payments &amp; refunds</h1>
      <div className="mt-4 flex gap-2">
        {(['payments', 'refunds', 'mismatches'] as const).map((t) => (
          <button key={t} className={t === tab ? 'btn-primary' : 'btn-secondary'} onClick={() => setTab(t)}>{t[0].toUpperCase() + t.slice(1)}</button>
        ))}
      </div>

      {tab === 'payments' && (
        <table className="table card mt-4">
          <thead><tr><th>Order</th><th>Razorpay order / payment</th><th>Amount</th><th>Status</th><th>Method</th><th>Captured</th></tr></thead>
          <tbody>{payments.data?.map((p) => (
            <tr key={p.id}><td><Link className="hover:text-clay-600" href={`/admin/orders/${p.orderId}`}>{p.orderNumber}</Link></td>
              <td className="font-mono text-xs">{p.gatewayOrderId}<br />{p.gatewayPaymentId}</td><td>{money(p.amountPaise)}</td>
              <td>{p.status}{p.errorCode && <span className="block text-xs text-red-700">{p.errorCode}</span>}</td><td>{p.method}</td><td>{istDate(p.capturedAt)}</td></tr>
          ))}</tbody>
        </table>
      )}

      {tab === 'refunds' && (
        <>
          <form onSubmit={createRefund} className="card mt-4 grid gap-3 p-4 sm:grid-cols-4">
            <p className="text-sm font-medium sm:col-span-4">Manual (partial) refund to the original payment method</p>
            <input className="input" placeholder="Order id" required value={refund.orderId} onChange={(e) => setRefund({ ...refund, orderId: e.target.value })} />
            <input className="input" placeholder="Amount ₹" required inputMode="decimal" value={refund.amount} onChange={(e) => setRefund({ ...refund, amount: e.target.value })} />
            <input className="input sm:col-span-2" placeholder="Reason" required value={refund.reason} onChange={(e) => setRefund({ ...refund, reason: e.target.value })} />
            <div className="flex items-center gap-3 sm:col-span-4"><button className="btn-primary">Refund</button>{msg && <span className="text-sm">{msg}</span>}</div>
          </form>
          <table className="table card mt-4">
            <thead><tr><th>Order</th><th>Amount</th><th>Status</th><th>Source</th><th>Reason</th><th>By</th><th>Created</th></tr></thead>
            <tbody>{refunds.data?.map((r) => (
              <tr key={r.id}><td><Link className="hover:text-clay-600" href={`/admin/orders/${r.orderId}`}>{r.orderNumber}</Link></td>
                <td>{money(r.amountPaise)}</td><td>{r.status}{r.error && <span className="block text-xs text-red-700">{r.error}</span>}</td>
                <td>{r.source}</td><td>{r.reason}</td><td>{r.initiatedByEmail || 'system'}</td><td>{istDate(r.createdAt)}</td></tr>
            ))}</tbody>
          </table>
        </>
      )}

      {tab === 'mismatches' && (
        <table className="table card mt-4">
          <thead><tr><th>Day</th><th>Kind</th><th>Payment</th><th>Order</th><th>Detail</th><th></th></tr></thead>
          <tbody>{mismatches.data?.map((m) => (
            <tr key={m.id}><td>{m.runDate}</td><td>{m.kind}</td><td className="font-mono text-xs">{m.gatewayPaymentId}</td>
              <td>{m.orderId ? <Link href={`/admin/orders/${m.orderId}`}>{m.orderNumber}</Link> : m.gatewayOrderId}</td><td>{m.detail}</td>
              <td><button className="btn-link" onClick={async () => {
                const note = prompt('How was this resolved?');
                if (note) { await api(`/admin/payments/mismatches/${m.id}/resolve?note=${encodeURIComponent(note)}`, { method: 'POST' }); mismatches.reload(); }
              }}>Resolve</button></td></tr>
          ))}</tbody>
        </table>
      )}
    </div>
  );
}
