'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import { AddressForm } from '@/components/AddressForm';
import { RequireAuth } from '@/components/RequireAuth';
import { api, ApiError, errorMessage, newIdempotencyKey } from '@/lib/api';
import { money, SITE_NAME } from '@/lib/format';
import { openRazorpay } from '@/lib/razorpay';
import type { Address, CartView, CheckoutPayment, Warning } from '@/lib/types';

function Checkout() {
  const router = useRouter();
  const [addresses, setAddresses] = useState<Address[] | null>(null);
  const [addressId, setAddressId] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);
  const [cart, setCart] = useState<CartView | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [warnings, setWarnings] = useState<Warning[]>([]);
  const [pending, setPending] = useState<CheckoutPayment | null>(null);
  // One key per checkout attempt: a double click or network retry returns the same order.
  const keyRef = useRef<string>(newIdempotencyKey());

  useEffect(() => {
    api<Address[]>('/me/addresses').then((a) => {
      setAddresses(a);
      setAddressId(a.find((x) => x.isDefault)?.id ?? a[0]?.id ?? null);
      if (a.length === 0) setAdding(true);
    });
    api<CartView>('/cart').then(setCart);
  }, []);

  useEffect(() => {
    keyRef.current = newIdempotencyKey(); // new address = new attempt
  }, [addressId]);

  function pay(p: CheckoutPayment) {
    setPending(p);
    openRazorpay(p, SITE_NAME, {
      onSuccess: async (r) => {
        try {
          await api('/payments/verify', { method: 'POST', body: {
            razorpayOrderId: r.razorpay_order_id, razorpayPaymentId: r.razorpay_payment_id,
            razorpaySignature: r.razorpay_signature } });
        } catch {
          /* the webhook is the source of truth; the processing page will show the result */
        }
        window.dispatchEvent(new Event('cart:changed'));
        router.push(`/checkout/processing/${p.orderId}`);
      },
      onDismiss: () => setErr('Payment was not completed. Your items are reserved for a few minutes; you can try again.'),
    }).catch((e) => setErr(errorMessage(e)));
  }

  async function placeOrder() {
    if (!addressId || !cart) return;
    setBusy(true);
    setErr(null);
    setWarnings([]);
    try {
      const p = await api<CheckoutPayment>('/checkout', {
        method: 'POST', idempotencyKey: keyRef.current,
        body: { addressId, expectedTotalPaise: cart.pricing.totalPaise },
      });
      window.dispatchEvent(new Event('cart:changed'));
      pay(p);
    } catch (e) {
      if (e instanceof ApiError && (e.code === 'CART_CHANGED' || e.code === 'PRICE_CHANGED')) {
        setWarnings((e.details?.warnings as Warning[]) || []);
        setCart(await api<CartView>('/cart'));
        keyRef.current = newIdempotencyKey();
      }
      setErr(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function retry() {
    if (!pending) return;
    setErr(null);
    try {
      pay(await api<CheckoutPayment>(`/orders/${pending.orderId}/retry-payment`, { method: 'POST' }));
    } catch (e) {
      setErr(errorMessage(e));
    }
  }

  if (!addresses || !cart) return <div className="container-page py-16 text-sand-600">Loading…</div>;
  const q = cart.pricing;

  if (cart.items.length === 0 && !pending) {
    return <div className="container-page py-16 text-center"><h1 className="text-2xl">Your cart is empty</h1></div>;
  }

  return (
    <div className="container-page py-8">
      <h1 className="text-3xl">Checkout</h1>
      <div className="mt-6 grid gap-8 lg:grid-cols-[1fr,360px]">
        <section>
          <h2 className="text-xl">Delivery address</h2>
          <div className="mt-4 space-y-3">
            {addresses.map((a) => (
              <label key={a.id} className={`card flex cursor-pointer gap-3 p-4 ${addressId === a.id ? 'border-clay-500 ring-2 ring-clay-100' : ''}`}>
                <input type="radio" name="address" checked={addressId === a.id} onChange={() => setAddressId(a.id)} className="mt-1" />
                <span className="text-sm">
                  <strong>{a.name}</strong> · {a.phone}<br />
                  {a.line1}{a.line2 ? `, ${a.line2}` : ''}<br />
                  {a.city}, {a.state} {a.pincode}
                </span>
              </label>
            ))}
            {adding ? (
              <div className="card p-4">
                <AddressForm
                  onSaved={(a) => {
                    setAddresses([...addresses, a]);
                    setAddressId(a.id);
                    setAdding(false);
                  }}
                  onCancel={addresses.length ? () => setAdding(false) : undefined}
                />
              </div>
            ) : (
              <button className="btn-secondary" onClick={() => setAdding(true)}>Add a new address</button>
            )}
          </div>
        </section>

        <aside className="card h-fit p-5">
          <h2 className="text-xl">Order summary</h2>
          <ul className="mt-3 space-y-2 text-sm">
            {q.lines.map((l) => (
              <li key={l.variantId} className="flex justify-between gap-3">
                <span>{l.productTitle} <span className="text-sand-600">({l.variantTitle}) × {l.qty}</span></span>
                <span>{money(l.lineTotalPaise)}</span>
              </li>
            ))}
          </ul>
          <dl className="mt-4 space-y-2 border-t border-sand-200 pt-3 text-sm">
            <div className="flex justify-between"><dt>Subtotal</dt><dd>{money(q.subtotalPaise)}</dd></div>
            {q.discountPaise > 0 && <div className="flex justify-between text-green-700"><dt>Discount ({q.couponCode})</dt><dd>−{money(q.discountPaise)}</dd></div>}
            <div className="flex justify-between"><dt>Shipping</dt><dd>{q.shippingPaise === 0 ? 'Free' : money(q.shippingPaise)}</dd></div>
            <div className="flex justify-between text-base font-semibold"><dt>Total</dt><dd>{money(q.totalPaise)}</dd></div>
            <p className="text-xs text-sand-600">Includes GST of {money(q.taxPaise)}</p>
          </dl>
          {warnings.map((w, i) => <p key={i} className="mt-3 text-sm text-clay-700">{w.message}</p>)}
          {err && <p className="mt-3 text-sm text-red-700">{err}</p>}
          {pending ? (
            <button className="btn-primary mt-5 w-full" onClick={retry}>Try payment again</button>
          ) : (
            <button className="btn-primary mt-5 w-full" disabled={busy || !addressId || q.lines.length === 0} onClick={placeOrder}>
              {busy ? 'Placing order…' : `Pay ${money(q.totalPaise)}`}
            </button>
          )}
          <p className="mt-3 text-xs text-sand-600">UPI, cards and net banking via Razorpay. Prepaid orders only.</p>
        </aside>
      </div>
    </div>
  );
}

export default function CheckoutPage() {
  return <RequireAuth><Checkout /></RequireAuth>;
}
