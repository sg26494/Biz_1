'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { api, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { money } from '@/lib/format';
import type { CartView } from '@/lib/types';

export default function CartPage() {
  const { user, ready } = useAuth();
  const [cart, setCart] = useState<CartView | null>(null);
  const [coupon, setCoupon] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => api<CartView>('/cart').then(setCart).catch((e) => setErr(errorMessage(e))), []);
  useEffect(() => {
    if (ready) load();
  }, [ready, user, load]);

  async function run(fn: () => Promise<CartView>) {
    setBusy(true);
    setErr(null);
    try {
      setCart(await fn());
      window.dispatchEvent(new Event('cart:changed'));
    } catch (e) {
      setErr(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  if (!cart) return <div className="container-page py-16 text-sand-600">Loading cart…</div>;
  const q = cart.pricing;

  if (cart.items.length === 0) {
    return (
      <div className="container-page py-16 text-center">
        <h1 className="text-2xl">Your cart is empty</h1>
        {q.warnings.length > 0 && <p className="mt-2 text-sm text-red-700">{q.warnings[0].message}</p>}
        <Link href="/" className="btn-primary mt-6">Continue shopping</Link>
      </div>
    );
  }

  return (
    <div className="container-page py-8">
      <h1 className="text-3xl">Cart</h1>
      {q.warnings.filter((w) => w.type !== 'LOW_STOCK').map((w, i) => (
        <p key={i} className="mt-3 rounded-md bg-clay-50 px-3 py-2 text-sm text-clay-700">{w.message}</p>
      ))}
      <div className="mt-6 grid gap-8 lg:grid-cols-[1fr,360px]">
        <ul className="divide-y divide-sand-200 border-y border-sand-200">
          {cart.items.map(({ itemId, line }) => (
            <li key={itemId} className="flex gap-4 py-4">
              <div className="relative h-28 w-24 flex-none overflow-hidden rounded bg-sand-100">
                {line.imageUrl && <Image src={line.imageUrl} alt="" fill sizes="96px" className="object-cover" />}
              </div>
              <div className="flex flex-1 flex-col">
                <Link href={`/products/${line.productSlug}`} className="font-medium hover:text-clay-600">{line.productTitle}</Link>
                <span className="text-sm text-sand-600">{line.variantTitle}</span>
                <span className="mt-1 text-sm">{money(line.unitPricePaise)}</span>
                <div className="mt-auto flex items-center gap-3">
                  <select className="input w-20" value={line.qty} disabled={busy} aria-label="Quantity"
                    onChange={(e) => run(() => api<CartView>(`/cart/items/${itemId}`, { method: 'PATCH', body: { qty: Number(e.target.value) } }))}>
                    {Array.from({ length: Math.max(line.qty, Math.min(10, line.available)) }, (_, i) => i + 1).map((n) => (
                      <option key={n} value={n}>{n}</option>
                    ))}
                  </select>
                  <button className="btn-link" disabled={busy}
                    onClick={() => run(() => api<CartView>(`/cart/items/${itemId}`, { method: 'DELETE' }))}>Remove</button>
                </div>
              </div>
              <div className="text-right font-medium">{money(line.lineTotalPaise)}</div>
            </li>
          ))}
        </ul>

        <aside className="card h-fit p-5">
          <form className="flex gap-2" onSubmit={(e) => {
            e.preventDefault();
            if (coupon.trim()) run(() => api<CartView>('/cart/coupon', { method: 'POST', body: { code: coupon.trim() } }));
          }}>
            <input className="input" placeholder="Coupon code" value={coupon} onChange={(e) => setCoupon(e.target.value)} />
            <button className="btn-secondary" disabled={busy}>Apply</button>
          </form>
          {q.couponCode && !q.couponError && (
            <p className="mt-2 flex justify-between text-sm text-green-700">
              <span>{q.couponCode} applied</span>
              <button className="btn-link" onClick={() => run(() => api<CartView>('/cart/coupon', { method: 'DELETE' }))}>Remove</button>
            </p>
          )}
          {err && <p className="mt-2 text-sm text-red-700">{err}</p>}
          <dl className="mt-5 space-y-2 text-sm">
            <div className="flex justify-between"><dt>Subtotal</dt><dd>{money(q.subtotalPaise)}</dd></div>
            {q.discountPaise > 0 && <div className="flex justify-between text-green-700"><dt>Discount</dt><dd>−{money(q.discountPaise)}</dd></div>}
            <div className="flex justify-between"><dt>Shipping</dt><dd>{q.shippingPaise === 0 ? 'Free' : money(q.shippingPaise)}</dd></div>
            {q.freeShippingRemainingPaise != null && q.freeShippingRemainingPaise > 0 && (
              <p className="text-xs text-sand-600">Add {money(q.freeShippingRemainingPaise)} more for free shipping</p>
            )}
            <div className="flex justify-between border-t border-sand-200 pt-2 text-base font-semibold">
              <dt>Total</dt><dd>{money(q.totalPaise)}</dd>
            </div>
            <p className="text-xs text-sand-600">Includes GST of {money(q.taxPaise)}</p>
          </dl>
          <Link href={user ? '/checkout' : '/login?next=/checkout'} className="btn-primary mt-5 w-full">
            {user ? 'Checkout' : 'Log in to checkout'}
          </Link>
        </aside>
      </div>
    </div>
  );
}
