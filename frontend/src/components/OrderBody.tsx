import Image from 'next/image';
import { StatusBadge } from './StatusBadge';
import { istDate, money, ORDER_STATUS_LABEL } from '@/lib/format';
import type { OrderDetail } from '@/lib/types';

/** Shared by the customer order page and the admin order page. */
export function OrderBody({ order: o, admin = false }: { order: OrderDetail; admin?: boolean }) {
  const a = o.shippingAddress;
  return (
    <div>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl">{o.orderNumber}</h1>
        <StatusBadge status={o.status} />
      </div>
      <p className="mt-1 text-sm text-sand-600">Placed {istDate(o.createdAt)}{admin && o.customerName ? ` by ${o.customerName} (${o.email}, ${o.phone})` : ''}</p>
      {o.cancelReason && <p className="mt-2 text-sm text-sand-600">Cancelled: {o.cancelReason}</p>}

      <ul className="mt-6 divide-y divide-sand-200 border-y border-sand-200">
        {o.items.map((i) => (
          <li key={i.id} className="flex gap-4 py-3 text-sm">
            <div className="relative h-20 w-16 flex-none overflow-hidden rounded bg-sand-100">
              {i.imageUrl && <Image src={i.imageUrl} alt="" fill sizes="64px" className="object-cover" />}
            </div>
            <div className="flex-1">
              <p className="font-medium">{i.productTitle}</p>
              <p className="text-sand-600">{i.variantTitle} · {money(i.unitPricePaise)} × {i.qty}</p>
              {admin && <p className="text-xs text-sand-400">SKU {i.sku} · HSN {i.hsnCode} · GST {i.gstRateBp / 100}%</p>}
            </div>
            <p>{money(i.lineTotalPaise)}</p>
          </li>
        ))}
      </ul>

      <div className="mt-6 grid gap-6 sm:grid-cols-2">
        <div className="text-sm">
          <h2 className="mb-2 font-sans text-sm font-semibold text-sand-800">Delivery address</h2>
          <p>{a.name} · {a.phone}<br />{a.line1}{a.line2 ? `, ${a.line2}` : ''}<br />{a.city}, {a.state} {a.pincode}</p>
        </div>
        <dl className="space-y-1 text-sm">
          <div className="flex justify-between"><dt>Subtotal</dt><dd>{money(o.subtotalPaise)}</dd></div>
          {o.discountPaise > 0 && <div className="flex justify-between"><dt>Discount {o.couponCode && `(${o.couponCode})`}</dt><dd>−{money(o.discountPaise)}</dd></div>}
          <div className="flex justify-between"><dt>Shipping</dt><dd>{o.shippingPaise ? money(o.shippingPaise) : 'Free'}</dd></div>
          <div className="flex justify-between font-semibold"><dt>Total</dt><dd>{money(o.totalPaise)}</dd></div>
          <div className="flex justify-between text-xs text-sand-600"><dt>Taxable value</dt><dd>{money(o.taxablePaise)}</dd></div>
          <div className="flex justify-between text-xs text-sand-600">
            <dt>{o.igstPaise ? 'IGST' : 'CGST + SGST'}</dt>
            <dd>{o.igstPaise ? money(o.igstPaise) : `${money(o.cgstPaise)} + ${money(o.sgstPaise)}`}</dd>
          </div>
          {o.payment && <div className="flex justify-between text-xs text-sand-600"><dt>Payment</dt><dd>{o.payment.status}{o.payment.method ? ` · ${o.payment.method}` : ''}</dd></div>}
        </dl>
      </div>

      {o.refunds.length > 0 && (
        <div className="mt-6 text-sm">
          <h2 className="mb-2 font-sans text-sm font-semibold">Refunds</h2>
          <ul className="space-y-1">
            {o.refunds.map((r) => (
              <li key={r.id}>{money(r.amountPaise)} · {r.status.toLowerCase()} · {istDate(r.createdAt)}{admin && ` · ${r.reason}${r.error ? ` (${r.error})` : ''}`}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-6 text-sm">
        <h2 className="mb-2 font-sans text-sm font-semibold">Timeline</h2>
        <ol className="space-y-2 border-l-2 border-sand-200 pl-4">
          {o.timeline.map((h) => (
            <li key={h.id}>
              <span className="font-medium">{ORDER_STATUS_LABEL[h.toStatus] || h.toStatus}</span>
              <span className="text-sand-600"> · {istDate(h.createdAt)}</span>
              {admin && h.reason && <span className="block text-xs text-sand-600">{h.reason} ({h.actorType}{h.actorEmail ? `: ${h.actorEmail}` : ''})</span>}
            </li>
          ))}
        </ol>
      </div>
    </div>
  );
}
