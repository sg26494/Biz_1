'use client';

import { useState } from 'react';
import { api, errorMessage } from '@/lib/api';
import { istDate } from '@/lib/format';
import type { Page } from '@/lib/types';
import { useApi } from '@/lib/useApi';

type Customer = { id: number; name: string; email: string; phone?: string; role: string; status: string; createdAt: string; lastLoginAt?: string };

export default function AdminCustomers() {
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const { data, reload } = useApi<Page<Customer>>(`/admin/customers?q=${encodeURIComponent(q)}&page=${page}`);
  const [err, setErr] = useState<string | null>(null);

  async function toggle(c: Customer) {
    const blocked = c.status !== 'BLOCKED';
    const reason = prompt(blocked ? `Block ${c.email}? Reason:` : `Unblock ${c.email}? Reason:`);
    if (!reason) return;
    try {
      await api(`/admin/customers/${c.id}/block`, { method: 'POST', body: { blocked, reason } });
      reload();
    } catch (e) {
      setErr(errorMessage(e));
    }
  }

  return (
    <div>
      <h1 className="text-2xl">Customers</h1>
      <input className="input mt-4 max-w-xs" placeholder="Name, email or phone" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
      {err && <p className="mt-2 text-sm text-red-700">{err}</p>}
      <table className="table card mt-4">
        <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>Joined</th><th>Last login</th><th>Status</th><th></th></tr></thead>
        <tbody>{data?.items.map((c) => (
          <tr key={c.id}><td>{c.name}{c.role === 'ADMIN' && <span className="badge ml-2 bg-indigo-50 text-indigo-700">admin</span>}</td>
            <td>{c.email}</td><td>{c.phone}</td><td>{istDate(c.createdAt, false)}</td><td>{istDate(c.lastLoginAt)}</td>
            <td>{c.status}</td><td>{c.role !== 'ADMIN' && <button className="btn-link" onClick={() => toggle(c)}>{c.status === 'BLOCKED' ? 'Unblock' : 'Block'}</button>}</td></tr>
        ))}</tbody>
      </table>
      {data && data.totalPages > 1 && (
        <div className="mt-4 flex gap-2"><button className="btn-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <button className="btn-secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button></div>
      )}
    </div>
  );
}
