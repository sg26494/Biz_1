'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { api, ApiError, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import type { LoginResponse } from '@/lib/types';

type Pending = { token: string; email: string; name: string; next: string };

/** Step two of signup: nothing exists in the DB until this form is submitted. */
export default function SignupPage() {
  const router = useRouter();
  const { signedIn } = useAuth();
  const [pending, setPending] = useState<Pending | null>(null);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [terms, setTerms] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});

  useEffect(() => {
    const raw = sessionStorage.getItem('signup');
    if (!raw) {
      router.replace('/login');
      return;
    }
    const p = JSON.parse(raw) as Pending;
    setPending(p);
    setName(p.name);
  }, [router]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!pending) return;
    setBusy(true);
    setErr(null);
    setFields({});
    try {
      const r = await api<LoginResponse>('/auth/signup/complete', {
        method: 'POST', body: { signupToken: pending.token, name, phone, acceptTerms: terms },
      });
      sessionStorage.removeItem('signup');
      signedIn(r);
      router.replace(pending.next || '/');
    } catch (e2) {
      if (e2 instanceof ApiError) {
        setFields(e2.code === 'PHONE_TAKEN' ? { phone: e2.message } : e2.fieldErrors());
        if (e2.code === 'SIGNUP_TOKEN_INVALID' || e2.code === 'SIGNUP_TOKEN_USED') {
          sessionStorage.removeItem('signup');
        }
      }
      setErr(errorMessage(e2));
    } finally {
      setBusy(false);
    }
  }

  if (!pending) return null;
  return (
    <div className="container-page max-w-sm py-14">
      <h1 className="text-3xl">Almost there</h1>
      <p className="mt-2 text-sm text-sand-600">Tell us who you are so we can deliver your orders.</p>
      <form onSubmit={submit} className="mt-8 space-y-4">
        <div>
          <label className="label">Email</label>
          <input className="input bg-sand-100" value={pending.email} readOnly />
        </div>
        <div>
          <label className="label" htmlFor="name">Full name</label>
          <input id="name" className="input" required minLength={2} maxLength={80} value={name}
            onChange={(e) => setName(e.target.value)} autoComplete="name" />
          {fields.name && <p className="field-error">{fields.name}</p>}
        </div>
        <div>
          <label className="label" htmlFor="phone">Mobile number</label>
          <div className="flex">
            <span className="rounded-l-md border border-r-0 border-sand-200 bg-sand-100 px-3 py-2 text-sm">+91</span>
            <input id="phone" className="input rounded-l-none" required inputMode="numeric" pattern="[6-9][0-9]{9}"
              maxLength={10} value={phone} onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))} autoComplete="tel-national" />
          </div>
          {fields.phone && <p className="field-error">{fields.phone}</p>}
        </div>
        <label className="flex items-start gap-2 text-sm">
          <input type="checkbox" checked={terms} onChange={(e) => setTerms(e.target.checked)} className="mt-1" />
          <span>I agree to the <Link href="/pages/terms" className="btn-link">terms</Link> and{' '}
            <Link href="/pages/privacy" className="btn-link">privacy policy</Link>.</span>
        </label>
        <button className="btn-primary w-full" disabled={busy || !terms}>{busy ? 'Creating account…' : 'Create account'}</button>
        {err && <p className="text-sm text-red-700">{err}</p>}
      </form>
    </div>
  );
}
