'use client';

import Image from 'next/image';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { RequireAuth } from '@/components/RequireAuth';
import { StatusBadge } from '@/components/StatusBadge';
import { api } from '@/lib/api';
import { istDate, money } from '@/lib/format';
import type { OrderSummary, Page } from '@/lib/types';

function Orders() {
  const [data, setData] = useState<Page<OrderSummary> | null>(null);
  const [page, setPage] = useState(0);
  useEffect(() => {
    api<Page<OrderSummary>>(`/orders?page=${page}`).then(setData);
  }, [page]);
  if (!data) return <div className="container-page py-16 text-sand-600">Loading…</div>;
  return (
    <div className="container-page max-w-3xl py-8">
      <h1 className="text-3xl">My orders</h1>
      {data.items.length === 0 && <p className="mt-6 text-sand-600">No orders yet.</p>}
      <ul className="mt-6 space-y-3">
        {data.items.map((o) => (
          <li key={o.id}>
            <Link href={`/account/orders/${o.id}`} className="card flex gap-4 p-4 hover:border-clay-300">
              <div className="relative h-20 w-16 flex-none overflow-hidden rounded bg-sand-100">
                {o.firstItemImage && <Image src={o.firstItemImage} alt="" fill sizes="64px" className="object-cover" />}
              </div>
              <div className="flex-1 text-sm">
                <div className="flex items-center justify-between"><strong>{o.orderNumber}</strong><StatusBadge status={o.status} /></div>
                <p className="mt-1 text-sand-600">{o.firstItemTitle}{o.itemCount > 1 ? ` + ${o.itemCount - 1} more` : ''}</p>
                <p className="mt-1">{money(o.totalPaise)} · {istDate(o.createdAt, false)}</p>
              </div>
            </Link>
          </li>
        ))}
      </ul>
      {data.totalPages > 1 && (
        <div className="mt-6 flex justify-center gap-3">
          <button className="btn-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <button className="btn-secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </div>
  );
}

export default function OrdersPage() {
  return <RequireAuth><Orders /></RequireAuth>;
}
