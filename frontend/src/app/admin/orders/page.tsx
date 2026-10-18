'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useState } from 'react';
import { StatusBadge } from '@/components/StatusBadge';
import { istDate, money, ORDER_STATUS_LABEL } from '@/lib/format';
import type { Page } from '@/lib/types';
import { useApi } from '@/lib/useApi';

type Row = { id: number; orderNumber: string; status: string; totalPaise: number; customerName: string; email: string;
  phone: string; city: string; state: string; itemCount: number; paymentStatus?: string; createdAt: string };

function Orders() {
  const params = useSearchParams();
  const [status, setStatus] = useState(params.get('status') || '');
  const [q, setQ] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const qs = new URLSearchParams({ status, q, page: String(page) });
  if (from) qs.set('from', from);
  if (to) qs.set('to', to);
  const { data } = useApi<Page<Row>>(`/admin/orders?${qs.toString()}`);
  return (
    <div>
      <h1 className="text-2xl">Orders</h1>
      <div className="mt-4 flex flex-wrap gap-2">
        <select className="input w-auto" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All statuses</option>
          {Object.entries(ORDER_STATUS_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <input className="input max-w-xs" placeholder="Order no., phone or email" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        <input className="input w-auto" type="date" value={from} onChange={(e) => setFrom(e.target.value)} aria-label="From" />
        <input className="input w-auto" type="date" value={to} onChange={(e) => setTo(e.target.value)} aria-label="To" />
      </div>
      <div className="card mt-4 overflow-x-auto">
        <table className="table">
          <thead><tr><th>Order</th><th>Placed</th><th>Customer</th><th>Ship to</th><th>Items</th><th>Total</th><th>Payment</th><th>Status</th></tr></thead>
          <tbody>{data?.items.map((o) => (
            <tr key={o.id}>
              <td><Link className="font-medium hover:text-clay-600" href={`/admin/orders/${o.id}`}>{o.orderNumber}</Link></td>
              <td className="whitespace-nowrap">{istDate(o.createdAt)}</td>
              <td>{o.customerName}<br /><span className="text-xs text-sand-600">{o.phone}</span></td>
              <td>{o.city}, {o.state}</td><td>{o.itemCount}</td><td>{money(o.totalPaise)}</td>
              <td>{o.paymentStatus}</td><td><StatusBadge status={o.status} /></td>
            </tr>
          ))}</tbody>
        </table>
      </div>
      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center gap-2 text-sm">
          <button className="btn-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <span>{page + 1} / {data.totalPages}</span>
          <button className="btn-secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </div>
  );
}

export default function AdminOrders() {
  return <Suspense><Orders /></Suspense>;
}
