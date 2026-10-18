'use client';

import { useState } from 'react';
import { api, errorMessage } from '@/lib/api';
import { istDate } from '@/lib/format';
import { useApi } from '@/lib/useApi';

type Admin = { id: number; name: string; email: string; createdAt: string; lastLoginAt?: string };

export default function AdminAdmins() {
  const { data, reload } = useApi<Admin[]>('/admin/admins');
  const [email, setEmail] = useState('');
  const [err, setErr] = useState<string | null>(null);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    setErr(null);
    try {
      await api('/admin/admins', { method: 'POST', body: { email } });
      setEmail('');
      reload();
    } catch (e2) {
      setErr(errorMessage(e2));
    }
  }

  return (
    <div className="max-w-3xl">
      <h1 className="text-2xl">Admins</h1>
      <p className="mt-1 text-sm text-sand-600">Admins log in with Google or an email code using the address added here.</p>
      <form onSubmit={add} className="mt-4 flex gap-2">
        <input className="input max-w-sm" type="email" required placeholder="email@yourcompany.com" value={email} onChange={(e) => setEmail(e.target.value)} />
        <button className="btn-primary">Add admin</button>
      </form>
      {err && <p className="mt-2 text-sm text-red-700">{err}</p>}
      <table className="table card mt-4">
        <thead><tr><th>Name</th><th>Email</th><th>Last login</th><th></th></tr></thead>
        <tbody>{data?.map((a) => (
          <tr key={a.id}><td>{a.name}</td><td>{a.email}</td><td>{istDate(a.lastLoginAt) || 'never'}</td>
            <td><button className="btn-link text-red-700" onClick={async () => {
              if (!confirm(`Remove admin access for ${a.email}?`)) return;
              try { await api(`/admin/admins/${a.id}`, { method: 'DELETE' }); reload(); } catch (e) { setErr(errorMessage(e)); }
            }}>Remove</button></td></tr>
        ))}</tbody>
      </table>
    </div>
  );
}
