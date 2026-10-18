'use client';

import { useRouter } from 'next/navigation';
import { use, useEffect, useState } from 'react';
import { OrderBody } from '@/components/OrderBody';
import { RequireAuth } from '@/components/RequireAuth';
import { api, errorMessage } from '@/lib/api';
import { SITE_NAME } from '@/lib/format';
import { openRazorpay } from '@/lib/razorpay';
import type { CheckoutPayment, OrderDetail } from '@/lib/types';

function Order({ id }: { id: string }) {
  const router = useRouter();
  const [o, setO] = useState<OrderDetail | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    api<OrderDetail>(`/orders/${id}`).then(setO).catch((e) => setErr(errorMessage(e)));
  }, [id]);

  async function cancel() {
    setErr(null);
    try {
      setO(await api<OrderDetail>(`/orders/${id}/cancel`, { method: 'POST', body: { reason: reason || 'Cancelled by customer' } }));
      setConfirming(false);
    } catch (e) {
      setErr(errorMessage(e));
    }
  }

  async function retry() {
    try {
      const p = await api<CheckoutPayment>(`/orders/${id}/retry-payment`, { method: 'POST' });
      await openRazorpay(p, SITE_NAME, {
        onSuccess: async (r) => {
          await api('/payments/verify', { method: 'POST', body: { razorpayOrderId: r.razorpay_order_id,
            razorpayPaymentId: r.razorpay_payment_id, razorpaySignature: r.razorpay_signature } }).catch(() => undefined);
          router.push(`/checkout/processing/${id}`);
        },
        onDismiss: () => undefined,
      });
    } catch (e) {
      setErr(errorMessage(e));
    }
  }

  if (!o) return <div className="container-page py-16 text-sand-600">{err || 'Loading…'}</div>;
  return (
    <div className="container-page max-w-3xl py-8">
      <OrderBody order={o} />
      {err && <p className="mt-4 text-sm text-red-700">{err}</p>}
      <div className="mt-6 flex flex-wrap gap-3">
        {o.canRetryPayment && <button className="btn-primary" onClick={retry}>Complete payment</button>}
        {o.canCancel && !confirming && <button className="btn-secondary" onClick={() => setConfirming(true)}>Cancel order</button>}
      </div>
      {confirming && (
        <div className="card mt-4 space-y-3 p-4">
          <p className="text-sm">Cancel this order?{o.status === 'PAID' ? ' The full amount will be refunded to your original payment method.' : ''}</p>
          <input className="input" placeholder="Reason (optional)" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
          <div className="flex gap-2">
            <button className="btn-danger" onClick={cancel}>Yes, cancel</button>
            <button className="btn-secondary" onClick={() => setConfirming(false)}>Keep order</button>
          </div>
        </div>
      )}
    </div>
  );
}

export default function OrderPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <RequireAuth><Order id={id} /></RequireAuth>;
}
