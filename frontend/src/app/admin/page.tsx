'use client';

import Link from 'next/link';
import { money } from '@/lib/format';
import { useApi } from '@/lib/useApi';

type Dashboard = {
  ordersToday: number; revenueTodayPaise: number; revenueLast30DaysPaise: number; pendingToPack: number;
  awaitingPayment: number; openMismatches: number; failedRefunds: number; failedWebhooks: number;
  lowStock: { variantId: number; sku: string; productTitle: string; variantTitle: string; available: number }[];
};

export default function AdminDashboard() {
  const { data: d, error } = useApi<Dashboard>('/admin/dashboard');
  if (error) return <p className="text-red-700">{error}</p>;
  if (!d) return <p className="text-sand-600">Loading…</p>;
  const tiles: [string, string, string?][] = [
    ['Orders today', String(d.ordersToday)],
    ['Revenue today', money(d.revenueTodayPaise)],
    ['Revenue, last 30 days', money(d.revenueLast30DaysPaise)],
    ['Ready to pack', String(d.pendingToPack), '/admin/orders?status=PAID'],
    ['Awaiting payment', String(d.awaitingPayment), '/admin/orders?status=PENDING_PAYMENT'],
    ['Payment mismatches', String(d.openMismatches), '/admin/payments'],
    ['Failed refunds', String(d.failedRefunds), '/admin/payments'],
    ['Failed webhooks', String(d.failedWebhooks)],
  ];
  return (
    <div>
      <h1 className="text-2xl">Dashboard</h1>
      <div className="mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {tiles.map(([label, value, href]) => {
          const body = (
            <div className="card p-4">
              <p className="text-xs text-sand-600">{label}</p>
              <p className="mt-1 text-2xl font-semibold text-indigo-900">{value}</p>
            </div>
          );
          return href ? <Link key={label} href={href} className="hover:opacity-80">{body}</Link> : <div key={label}>{body}</div>;
        })}
      </div>
      <h2 className="mt-10 text-lg">Low stock</h2>
      {d.lowStock.length === 0 ? <p className="mt-2 text-sm text-sand-600">Nothing low.</p> : (
        <table className="table card mt-3">
          <thead><tr><th>SKU</th><th>Product</th><th>Variant</th><th>Available</th></tr></thead>
          <tbody>{d.lowStock.map((s) => (
            <tr key={s.variantId}><td>{s.sku}</td><td>{s.productTitle}</td><td>{s.variantTitle}</td><td>{s.available}</td></tr>
          ))}</tbody>
        </table>
      )}
    </div>
  );
}
