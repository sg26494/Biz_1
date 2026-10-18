'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { AddressForm } from '@/components/AddressForm';
import { RequireAuth } from '@/components/RequireAuth';
import { api, ApiError, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import type { Address } from '@/lib/types';

function Account() {
  const { user, logout, reload } = useAuth();
  const [name, setName] = useState(user?.name || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [msg, setMsg] = useState<string | null>(null);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [editing, setEditing] = useState<Address | 'new' | null>(null);

  const loadAddresses = () => api<Address[]>('/me/addresses').then(setAddresses);
  useEffect(() => {
    loadAddresses();
  }, []);

  async function saveProfile(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    try {
      await api('/me', { method: 'PATCH', body: { name, phone } });
      await reload();
      setMsg('Saved');
    } catch (e2) {
      setMsg(e2 instanceof ApiError ? Object.values(e2.fieldErrors())[0] || e2.message : errorMessage(e2));
    }
  }

  return (
    <div className="container-page max-w-3xl py-8">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl">My account</h1>
        <button className="btn-secondary" onClick={logout}>Log out</button>
      </div>
      <Link href="/account/orders" className="card mt-6 block p-4 hover:border-clay-300">My orders →</Link>

      <section className="card mt-6 p-5">
        <h2 className="text-xl">Profile</h2>
        <form onSubmit={saveProfile} className="mt-4 grid gap-3 sm:grid-cols-2">
          <div><label className="label">Email</label><input className="input bg-sand-100" value={user?.email || ''} readOnly /></div>
          <div><label className="label" htmlFor="n">Name</label><input id="n" className="input" value={name} onChange={(e) => setName(e.target.value)} /></div>
          <div><label className="label" htmlFor="p">Mobile</label><input id="p" className="input" inputMode="numeric" maxLength={10} value={phone} onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))} /></div>
          <div className="flex items-end gap-3"><button className="btn-primary">Save</button>{msg && <span className="text-sm text-sand-600">{msg}</span>}</div>
        </form>
      </section>

      <section className="card mt-6 p-5">
        <div className="flex items-center justify-between">
          <h2 className="text-xl">Addresses</h2>
          {editing === null && <button className="btn-secondary" onClick={() => setEditing('new')}>Add address</button>}
        </div>
        {editing !== null && (
          <div className="mt-4">
            <AddressForm initial={editing === 'new' ? undefined : editing}
              onSaved={() => { setEditing(null); loadAddresses(); }} onCancel={() => setEditing(null)} />
          </div>
        )}
        <ul className="mt-4 space-y-3">
          {addresses.map((a) => (
            <li key={a.id} className="flex justify-between gap-3 rounded-md bg-sand-50 p-3 text-sm">
              <span><strong>{a.name}</strong> · {a.phone}{a.isDefault && <span className="badge ml-2 bg-clay-50 text-clay-700">Default</span>}<br />
                {a.line1}{a.line2 ? `, ${a.line2}` : ''}, {a.city}, {a.state} {a.pincode}</span>
              <span className="flex flex-none gap-3">
                <button className="btn-link" onClick={() => setEditing(a)}>Edit</button>
                <button className="btn-link" onClick={async () => { await api(`/me/addresses/${a.id}`, { method: 'DELETE' }); loadAddresses(); }}>Delete</button>
              </span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

export default function AccountPage() {
  return <RequireAuth><Account /></RequireAuth>;
}
