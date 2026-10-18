const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });
const inrWhole = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });

/** Paise -> "₹2,499" (drops .00) */
export function money(paise: number | undefined | null): string {
  if (paise == null) return '';
  return paise % 100 === 0 ? inrWhole.format(paise / 100) : inr.format(paise / 100);
}

/** Stored in UTC, shown in IST. */
export function istDate(iso?: string | null, withTime = true): string {
  if (!iso) return '';
  return new Intl.DateTimeFormat('en-IN', {
    timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', year: 'numeric',
    ...(withTime ? { hour: 'numeric', minute: '2-digit' } : {}),
  }).format(new Date(iso));
}

export function rupeesToPaise(v: string): number {
  return Math.round(parseFloat(v || '0') * 100);
}

export function paiseToRupees(p?: number | null): string {
  return p == null ? '' : (p / 100).toFixed(2).replace(/\.00$/, '');
}

export function discountPercent(mrp: number, price: number): number {
  return mrp > price ? Math.round(((mrp - price) / mrp) * 100) : 0;
}

export const ORDER_STATUS_LABEL: Record<string, string> = {
  PENDING_PAYMENT: 'Awaiting payment',
  PAYMENT_FAILED: 'Payment failed',
  PAID: 'Confirmed',
  PACKED: 'Packed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  RETURN_IN_PROGRESS: 'Return in progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  RTO: 'Returned to us',
};

export const SITE_NAME = process.env.NEXT_PUBLIC_SITE_NAME || 'Gulmohar Fabrics';
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000';

export const INDIAN_STATES = [
  'Andaman and Nicobar Islands', 'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chandigarh',
  'Chhattisgarh', 'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Goa', 'Gujarat', 'Haryana',
  'Himachal Pradesh', 'Jammu and Kashmir', 'Jharkhand', 'Karnataka', 'Kerala', 'Ladakh', 'Lakshadweep',
  'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Puducherry',
  'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh', 'Uttarakhand',
  'West Bengal',
];
