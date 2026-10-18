'use client';

import { useState } from 'react';
import { api, ApiError, errorMessage } from '@/lib/api';
import { INDIAN_STATES } from '@/lib/format';
import type { Address } from '@/lib/types';

type Props = { initial?: Address; onSaved: (a: Address) => void; onCancel?: () => void };

export function AddressForm({ initial, onSaved, onCancel }: Props) {
  const [f, setF] = useState({
    name: initial?.name || '', phone: initial?.phone || '', line1: initial?.line1 || '', line2: initial?.line2 || '',
    landmark: initial?.landmark || '', city: initial?.city || '', state: initial?.state || '',
    pincode: initial?.pincode || '', isDefault: initial?.isDefault ?? false,
  });
  const [busy, setBusy] = useState(false);
  const [errs, setErrs] = useState<Record<string, string>>({});
  const [err, setErr] = useState<string | null>(null);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF({ ...f, [k]: e.target.type === 'checkbox' ? (e.target as HTMLInputElement).checked : e.target.value });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErrs({});
    setErr(null);
    try {
      const saved = initial
        ? await api<Address>(`/me/addresses/${initial.id}`, { method: 'PATCH', body: f })
        : await api<Address>('/me/addresses', { method: 'POST', body: f });
      onSaved(saved);
    } catch (e2) {
      if (e2 instanceof ApiError) setErrs(e2.fieldErrors());
      setErr(errorMessage(e2));
    } finally {
      setBusy(false);
    }
  }

  const field = (k: keyof typeof f, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <div>
      <label className="label" htmlFor={`addr-${k}`}>{label}</label>
      <input id={`addr-${k}`} className="input" value={f[k] as string} onChange={set(k)} {...props} />
      {errs[k] && <p className="field-error">{errs[k]}</p>}
    </div>
  );

  return (
    <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
      {field('name', 'Full name', { required: true, autoComplete: 'name' })}
      {field('phone', 'Mobile number', { required: true, inputMode: 'numeric', maxLength: 10, pattern: '[6-9][0-9]{9}', autoComplete: 'tel-national' })}
      <div className="sm:col-span-2">{field('line1', 'House / flat, street', { required: true, autoComplete: 'address-line1' })}</div>
      <div className="sm:col-span-2">{field('line2', 'Area, locality (optional)', { autoComplete: 'address-line2' })}</div>
      {field('landmark', 'Landmark (optional)')}
      {field('pincode', 'Pincode', { required: true, inputMode: 'numeric', maxLength: 6, pattern: '[1-9][0-9]{5}', autoComplete: 'postal-code' })}
      {field('city', 'City', { required: true, autoComplete: 'address-level2' })}
      <div>
        <label className="label" htmlFor="addr-state">State</label>
        <select id="addr-state" className="input" required value={f.state} onChange={set('state')}>
          <option value="">Choose state</option>
          {INDIAN_STATES.map((s) => <option key={s}>{s}</option>)}
        </select>
        {errs.state && <p className="field-error">{errs.state}</p>}
      </div>
      <label className="flex items-center gap-2 text-sm sm:col-span-2">
        <input type="checkbox" checked={f.isDefault} onChange={set('isDefault')} /> Make this my default address
      </label>
      {err && <p className="text-sm text-red-700 sm:col-span-2">{err}</p>}
      <div className="flex gap-2 sm:col-span-2">
        <button className="btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save address'}</button>
        {onCancel && <button type="button" className="btn-secondary" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  );
}
