'use client';

import type { CheckoutPayment } from './types';

type RazorpayResponse = { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string };
type RazorpayCtor = new (options: Record<string, unknown>) => { open: () => void; on: (e: string, cb: (r: unknown) => void) => void };

function loadScript(): Promise<RazorpayCtor> {
  const w = window as unknown as { Razorpay?: RazorpayCtor };
  if (w.Razorpay) return Promise.resolve(w.Razorpay);
  return new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = 'https://checkout.razorpay.com/v1/checkout.js';
    s.onload = () => (w.Razorpay ? resolve(w.Razorpay) : reject(new Error('Razorpay failed to load')));
    s.onerror = () => reject(new Error('Could not load the payment window. Check your connection.'));
    document.body.appendChild(s);
  });
}

/** Opens Razorpay Standard Checkout. We never see card details. */
export async function openRazorpay(p: CheckoutPayment, storeName: string, handlers: {
  onSuccess: (r: RazorpayResponse) => void;
  onDismiss: () => void;
}) {
  const Razorpay = await loadScript();
  const rzp = new Razorpay({
    key: p.razorpayKeyId,
    amount: p.amountPaise,
    currency: p.currency,
    order_id: p.razorpayOrderId,
    name: storeName,
    description: `Order ${p.orderNumber}`,
    prefill: { name: p.customerName, email: p.customerEmail, contact: p.customerPhone },
    notes: { order_number: p.orderNumber },
    theme: { color: '#9a5230' },
    handler: handlers.onSuccess,
    modal: { ondismiss: handlers.onDismiss, confirm_close: true },
    retry: { enabled: true },
  });
  rzp.open();
}
