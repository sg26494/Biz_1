'use client';

import { useEffect, useState } from 'react';
import { api, errorMessage } from '@/lib/api';
import { INDIAN_STATES } from '@/lib/format';
import { useApi } from '@/lib/useApi';

type Setting = { key: string; value: unknown; description?: string };
type Warehouse = { name: string; pickupLocationName?: string; contactName?: string; phone?: string; email?: string;
  line1: string; line2?: string; city: string; state: string; pincode: string; gstin?: string };

/** Money settings are in paise. Business rules here come from the founders' decisions list. */
export default function AdminSettings() {
  const { data: settings, reload } = useApi<Setting[]>('/admin/settings');
  const { data: wh } = useApi<Warehouse>('/admin/settings/warehouse');
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [w, setW] = useState<Warehouse | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  useEffect(() => { if (wh) setW(wh); }, [wh]);

  async function saveSetting(key: string) {
    setMsg(null);
    try {
      await api(`/admin/settings/${key}`, { method: 'PUT', body: { value: JSON.parse(edits[key]) } });
      setEdits((e) => { const n = { ...e }; delete n[key]; return n; });
      reload();
      setMsg(`Saved ${key}`);
    } catch (e) {
      setMsg(e instanceof SyntaxError ? 'Value must be valid JSON (numbers, true/false, "text" or null)' : errorMessage(e));
    }
  }

  async function saveWarehouse(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    try {
      setW(await api<Warehouse>('/admin/settings/warehouse', { method: 'PUT', body: w }));
      setMsg('Pickup address saved');
    } catch (e2) {
      setMsg(errorMessage(e2));
    }
  }

  return (
    <div className="max-w-4xl space-y-8">
      <h1 className="text-2xl">Settings</h1>
      {msg && <p className="text-sm">{msg}</p>}
      <table className="table card">
        <thead><tr><th>Setting</th><th>Value</th><th></th></tr></thead>
        <tbody>{settings?.map((s) => {
          const current = JSON.stringify(s.value);
          const v = edits[s.key] ?? current;
          return (
            <tr key={s.key}>
              <td><span className="font-mono text-xs">{s.key}</span><span className="block text-xs text-sand-600">{s.description}</span></td>
              <td><input className="input font-mono text-xs" value={v} onChange={(e) => setEdits({ ...edits, [s.key]: e.target.value })} /></td>
              <td>{v !== current && <button className="btn-link" onClick={() => saveSetting(s.key)}>Save</button>}</td>
            </tr>
          );
        })}</tbody>
      </table>

      {w && (
        <form onSubmit={saveWarehouse} className="card grid gap-3 p-5 sm:grid-cols-2">
          <h2 className="text-lg sm:col-span-2">Pickup address &amp; GSTIN</h2>
          {(['name', 'pickupLocationName', 'contactName', 'phone', 'email', 'line1', 'line2', 'city', 'pincode', 'gstin'] as const).map((k) => (
            <div key={k}><label className="label">{k}</label>
              <input className="input" value={w[k] || ''} onChange={(e) => setW({ ...w, [k]: e.target.value })} /></div>
          ))}
          <div><label className="label">state</label>
            <select className="input" value={w.state} onChange={(e) => setW({ ...w, state: e.target.value })}>
              {INDIAN_STATES.map((s) => <option key={s}>{s}</option>)}</select></div>
          <p className="text-xs text-sand-600 sm:col-span-2">The state decides CGST+SGST vs IGST. The pickup location name must match Shiprocket.</p>
          <div className="sm:col-span-2"><button className="btn-primary">Save pickup address</button></div>
        </form>
      )}
    </div>
  );
}
