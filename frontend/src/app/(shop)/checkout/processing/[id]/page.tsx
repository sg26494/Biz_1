'use client';

import Link from 'next/link';
import { use, useEffect, useState } from 'react';
import { RequireAuth } from '@/components/RequireAuth';
import { api } from '@/lib/api';
import { money } from '@/lib/format';
import type { OrderDetail } from '@/lib/types';

const FINAL = ['PAID', 'PACKED', 'SHIPPED', 'DELIVERED', 'COMPLETED', 'CANCELLED'];

/** Polls the order until the webhook marks it PAID (or it is cancelled). */
function Processing({ id }: { id: string }) {
  const [order, setOrder] = useState<OrderDetail | null>(null);
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    let stop = false;
    const started = Date.now();
    const tick = async () => {
      try {
        const o = await api<OrderDetail>(`/orders/${id}`);
        setOrder(o);
        if (FINAL.includes(o.status)) return;
      } catch {
        /* keep polling */
      }
      if (Date.now() - started > 120_000) {
        setTimedOut(true);
        return;
      }
      if (!stop) setTimeout(tick, 2000);
    };
    tick();
    return () => {
      stop = true;
    };
  }, [id]);

  const paid = order && ['PAID', 'PACKED', 'SHIPPED', 'DELIVERED', 'COMPLETED'].includes(order.status);
  return (
    <div className="container-page max-w-lg py-16 text-center">
      {paid ? (
        <>
          <h1 className="text-3xl">Thank you! Order confirmed</h1>
          <p className="mt-3 text-sand-600">Order <strong>{order.orderNumber}</strong> · {money(order.totalPaise)}. A confirmation email is on its way.</p>
          <Link href={`/account/orders/${order.id}`} className="btn-primary mt-8">View order</Link>
        </>
      ) : order && (order.status === 'CANCELLED' || order.status === 'PAYMENT_FAILED') ? (
        <>
          <h1 className="text-3xl">Payment not completed</h1>
          <p className="mt-3 text-sand-600">If money was deducted, it will be refunded automatically.</p>
          <Link href={order.canRetryPayment ? `/account/orders/${order.id}` : '/cart'} className="btn-primary mt-8">
            {order.canRetryPayment ? 'Try again' : 'Back to cart'}
          </Link>
        </>
      ) : timedOut ? (
        <>
          <h1 className="text-3xl">Still confirming your payment</h1>
          <p className="mt-3 text-sand-600">This can take a few minutes with some banks. We&apos;ll email you as soon as it&apos;s confirmed.</p>
          <Link href={`/account/orders/${id}`} className="btn-secondary mt-8">Check order status</Link>
        </>
      ) : (
        <>
          <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-sand-200 border-t-clay-600" />
          <h1 className="mt-6 text-2xl">Confirming your payment…</h1>
          <p className="mt-2 text-sm text-sand-600">Please don&apos;t close this page.</p>
        </>
      )}
    </div>
  );
}

export default function ProcessingPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  return <RequireAuth><Processing id={id} /></RequireAuth>;
}
