'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';
import { api, errorMessage } from '@/lib/api';
import { discountPercent, money } from '@/lib/format';
import type { AttributeDef, DeliveryCheck, Variant } from '@/lib/types';

/** Variant picker (by attribute), quantity, add to cart, pincode check. Prices shown come from the server. */
export function AddToCart({ variants, defs }: { variants: Variant[]; defs: AttributeDef[] }) {
  const keys = useMemo(() => {
    const present = new Set(variants.flatMap((v) => Object.keys(v.attributes || {})));
    const ordered = defs.map((d) => d.key).filter((k) => present.has(k));
    return [...ordered, ...[...present].filter((k) => !ordered.includes(k))];
  }, [variants, defs]);
  const firstInStock = variants.find((v) => v.inStock) || variants[0];
  const [selected, setSelected] = useState<Record<string, string>>({ ...(firstInStock?.attributes || {}) });
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [pincode, setPincode] = useState('');
  const [delivery, setDelivery] = useState<DeliveryCheck | null>(null);

  const variant =
    variants.find((v) => keys.every((k) => (v.attributes || {})[k] === selected[k])) ||
    (variants.length === 1 ? variants[0] : undefined);

  const label = (k: string) => defs.find((d) => d.key === k)?.label || k;
  const values = (k: string) => [...new Set(variants.map((v) => (v.attributes || {})[k]).filter(Boolean))];

  async function add() {
    if (!variant) return;
    setBusy(true);
    setMsg(null);
    try {
      await api('/cart/items', { method: 'POST', body: { variantId: variant.id, qty } });
      window.dispatchEvent(new Event('cart:changed'));
      setMsg({ ok: true, text: 'Added to cart' });
    } catch (e) {
      setMsg({ ok: false, text: errorMessage(e) });
    } finally {
      setBusy(false);
    }
  }

  async function checkPincode(e: React.FormEvent) {
    e.preventDefault();
    try {
      setDelivery(await api<DeliveryCheck>(`/delivery/check?pincode=${pincode}${variant ? `&variantId=${variant.id}` : ''}`));
    } catch (err) {
      setDelivery({ pincode, serviceable: false, message: errorMessage(err) });
    }
  }

  return (
    <div className="space-y-5">
      {variant && (
        <p className="text-2xl">
          <span className="font-semibold text-indigo-900">{money(variant.pricePaise)}</span>
          {discountPercent(variant.mrpPaise, variant.pricePaise) > 0 && (
            <>
              <span className="ml-3 text-base text-sand-400 line-through">{money(variant.mrpPaise)}</span>
              <span className="ml-2 text-base text-clay-600">{discountPercent(variant.mrpPaise, variant.pricePaise)}% off</span>
            </>
          )}
          <span className="block text-xs text-sand-600">Inclusive of all taxes</span>
        </p>
      )}

      {keys.map((k) => (
        <div key={k}>
          <p className="label">{label(k)}</p>
          <div className="flex flex-wrap gap-2">
            {values(k).map((val) => {
              const candidate = variants.find((v) => keys.every((kk) =>
                kk === k ? v.attributes[kk] === val : v.attributes[kk] === selected[kk]));
              const active = selected[k] === val;
              return (
                <button key={val} type="button" onClick={() => setSelected({ ...selected, [k]: val })}
                  className={`rounded-md border px-3 py-1.5 text-sm ${active ? 'border-clay-600 bg-clay-50 text-clay-700' : 'border-sand-200 bg-white'} ${candidate && !candidate.inStock ? 'text-sand-400 line-through' : ''}`}>
                  {val}
                </button>
              );
            })}
          </div>
        </div>
      ))}

      {variant && !variant.inStock && <p className="text-sm text-red-700">Out of stock</p>}
      {variant && variant.inStock && variant.available <= 3 && (
        <p className="text-sm text-clay-600">Only {variant.available} left</p>
      )}
      {!variant && <p className="text-sm text-sand-600">This combination isn't available.</p>}

      <div className="flex gap-3">
        <select className="input w-20" value={qty} onChange={(e) => setQty(Number(e.target.value))} aria-label="Quantity">
          {Array.from({ length: Math.max(1, Math.min(10, variant?.available || 1)) }, (_, i) => i + 1).map((n) => (
            <option key={n} value={n}>{n}</option>
          ))}
        </select>
        <button className="btn-primary flex-1" disabled={!variant || !variant.inStock || busy} onClick={add}>
          {busy ? 'Adding…' : 'Add to cart'}
        </button>
      </div>
      {msg && (
        <p className={`text-sm ${msg.ok ? 'text-green-700' : 'text-red-700'}`}>
          {msg.text} {msg.ok && <Link href="/cart" className="btn-link ml-2">View cart</Link>}
        </p>
      )}

      <form onSubmit={checkPincode} className="rounded-md bg-sand-100 p-3">
        <label className="label" htmlFor="pin">Check delivery</label>
        <div className="flex gap-2">
          <input id="pin" className="input" inputMode="numeric" maxLength={6} placeholder="6-digit pincode"
            value={pincode} onChange={(e) => setPincode(e.target.value.replace(/\D/g, ''))} />
          <button className="btn-secondary" disabled={pincode.length !== 6}>Check</button>
        </div>
        {delivery && (
          <p className={`mt-2 text-sm ${delivery.serviceable ? 'text-green-700' : 'text-red-700'}`}>
            {delivery.serviceable && delivery.estimatedDelivery
              ? `Delivery by ${new Date(delivery.estimatedDelivery).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })}`
              : delivery.message}
          </p>
        )}
      </form>
    </div>
  );
}
