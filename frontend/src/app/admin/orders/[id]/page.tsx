'use client';

import { use, useRef, useState } from 'react';
import { OrderBody } from '@/components/OrderBody';
import { api, errorMessage, newIdempotencyKey } from '@/lib/api';
import type { OrderDetail } from '@/lib/types';
import { useApi } from '@/lib/useApi';

export default function AdminOrder({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { data: o, setData } = useApi<OrderDetail>(`/admin/orders/${id}`);
  const [reason, setReason] = useState('');
  const [open, setOpen] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const key = useRef(newIdempotencyKey());

  if (!o) return <p className="text-sand-600">Loading…</p>;
  const cancellable = ['PENDING_PAYMENT', 'PAYMENT_FAILED', 'PAID', 'PACKED'].includes(o.status);

  async function cancel() {
    setErr(null);
    try {
      setData(await api<OrderDetail>(`/admin/orders/${id}/cancel`, { method: 'POST', idempotencyKey: key.current, body: { reason } }));
      setOpen(false);
    } catch (e) {
      setErr(errorMessage(e));
    }
  }

  return (
    <div className="max-w-4xl">
      <OrderBody order={o} admin />
      <div className="mt-6 flex flex-wrap gap-2">
        {o.status === 'PAID' && <button className="btn-primary" disabled title="Arrives with the Shiprocket module">Pack &amp; ship</button>}
        {cancellable && <button className="btn-danger" onClick={() => setOpen(true)}>Cancel order</button>}
      </div>
      {open && (
        <div className="card mt-4 space-y-3 p-4">
          <p className="text-sm">{['PAID', 'PACKED'].includes(o.status)
            ? 'This cancels the order, puts the stock back and refunds the full amount to the customer.'
            : 'This cancels the unpaid order and releases the reserved stock.'}</p>
          <input className="input" placeholder="Reason (required, shown in the audit log)" value={reason} onChange={(e) => setReason(e.target.value)} />
          <div className="flex gap-2">
            <button className="btn-danger" disabled={reason.trim().length < 3} onClick={cancel}>Confirm cancel</button>
            <button className="btn-secondary" onClick={() => setOpen(false)}>Back</button>
          </div>
          {err && <p className="text-sm text-red-700">{err}</p>}
        </div>
      )}
    </div>
  );
}
