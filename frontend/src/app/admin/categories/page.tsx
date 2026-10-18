'use client';

import { useState } from 'react';
import { api, errorMessage } from '@/lib/api';
import type { Category } from '@/lib/types';
import { useApi } from '@/lib/useApi';

const EXAMPLE = '[{"key":"size","label":"Size","type":"enum","options":["Single","Double"],"filterable":true}]';

/** New category types (e.g. antiques) only need a name and attribute definitions; no schema change. */
export default function AdminCategories() {
  const { data, reload } = useApi<Category[]>('/admin/categories');
  const [editing, setEditing] = useState<Partial<Category> | null>(null);
  const [defs, setDefs] = useState('');
  const [err, setErr] = useState<string | null>(null);

  function edit(c: Partial<Category>) {
    setEditing(c);
    setDefs(JSON.stringify(c.attributeDefs || [], null, 2));
    setErr(null);
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!editing) return;
    try {
      const body = { ...editing, attributeDefs: JSON.parse(defs || '[]') };
      if (editing.id) await api(`/admin/categories/${editing.id}`, { method: 'PUT', body });
      else await api('/admin/categories', { method: 'POST', body });
      setEditing(null);
      reload();
    } catch (e2) {
      setErr(e2 instanceof SyntaxError ? 'Attribute definitions must be valid JSON' : errorMessage(e2));
    }
  }

  return (
    <div className="max-w-4xl">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl">Categories</h1>
        <button className="btn-primary" onClick={() => edit({ name: '', active: true, sortOrder: 0 })}>New category</button>
      </div>
      {editing && (
        <form onSubmit={save} className="card mt-4 grid gap-3 p-5 sm:grid-cols-2">
          <div><label className="label">Name</label><input className="input" required value={editing.name || ''} onChange={(e) => setEditing({ ...editing, name: e.target.value })} /></div>
          <div><label className="label">Slug (blank = from name)</label><input className="input" value={editing.slug || ''} onChange={(e) => setEditing({ ...editing, slug: e.target.value || undefined })} /></div>
          <div className="sm:col-span-2"><label className="label">Description</label><input className="input" value={editing.description || ''} onChange={(e) => setEditing({ ...editing, description: e.target.value })} /></div>
          <div><label className="label">Sort order</label><input className="input" type="number" value={editing.sortOrder ?? 0} onChange={(e) => setEditing({ ...editing, sortOrder: Number(e.target.value) })} /></div>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={editing.active ?? true} onChange={(e) => setEditing({ ...editing, active: e.target.checked })} /> Active</label>
          <div className="sm:col-span-2"><label className="label">Variant attributes (JSON)</label>
            <textarea className="input min-h-40 font-mono text-xs" value={defs} onChange={(e) => setDefs(e.target.value)} placeholder={EXAMPLE} /></div>
          <div className="flex gap-2 sm:col-span-2"><button className="btn-primary">Save</button><button type="button" className="btn-secondary" onClick={() => setEditing(null)}>Cancel</button>
            {err && <span className="text-sm text-red-700">{err}</span>}</div>
        </form>
      )}
      <table className="table card mt-4">
        <thead><tr><th>Name</th><th>Slug</th><th>Attributes</th><th>Active</th><th></th></tr></thead>
        <tbody>{data?.map((c) => (
          <tr key={c.id}><td>{c.name}</td><td>{c.slug}</td><td>{c.attributeDefs.map((d) => d.label).join(', ')}</td>
            <td>{c.active ? 'Yes' : 'No'}</td><td><button className="btn-link" onClick={() => edit(c)}>Edit</button></td></tr>
        ))}</tbody>
      </table>
    </div>
  );
}
