'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useState } from 'react';
import { GoogleButton } from '@/components/GoogleButton';
import { api, ApiError, errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import type { LoginResponse } from '@/lib/types';

function LoginInner() {
  const router = useRouter();
  const next = useSearchParams().get('next') || '/';
  const { signedIn } = useAuth();
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [step, setStep] = useState<'email' | 'code'>('email');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);

  const handle = useCallback((r: LoginResponse) => {
    if (r.status === 'LOGGED_IN') {
      signedIn(r);
      router.replace(next.startsWith('/') ? next : '/');
    } else {
      sessionStorage.setItem('signup', JSON.stringify({ token: r.signupToken, email: r.email, name: r.name || '', next }));
      router.replace('/signup');
    }
  }, [next, router, signedIn]);

  const onGoogle = useCallback(async (idToken: string) => {
    setErr(null);
    try {
      handle(await api<LoginResponse>('/auth/google', { method: 'POST', body: { idToken } }));
    } catch (e) {
      setErr(errorMessage(e));
    }
  }, [handle]);

  function startCooldown() {
    setCooldown(60);
    const t = setInterval(() => setCooldown((c) => {
      if (c <= 1) clearInterval(t);
      return c - 1;
    }), 1000);
  }

  async function requestCode(e?: React.FormEvent) {
    e?.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      await api('/auth/otp/request', { method: 'POST', body: { email } });
      setStep('code');
      startCooldown();
    } catch (e2) {
      const retry = e2 instanceof ApiError ? (e2.details?.retryAfterSeconds as number | undefined) : undefined;
      setErr(retry ? `Please wait ${retry}s before asking for another code` : errorMessage(e2));
    } finally {
      setBusy(false);
    }
  }

  async function verify(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      handle(await api<LoginResponse>('/auth/otp/verify', { method: 'POST', body: { email, code } }));
    } catch (e2) {
      setErr(errorMessage(e2));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="container-page max-w-sm py-14">
      <h1 className="text-3xl">Log in or sign up</h1>
      <div className="mt-8"><GoogleButton onCredential={onGoogle} /></div>
      <div className="my-6 flex items-center gap-3 text-xs text-sand-400"><span className="h-px flex-1 bg-sand-200" />or<span className="h-px flex-1 bg-sand-200" /></div>
      {step === 'email' ? (
        <form onSubmit={requestCode} className="space-y-3">
          <label className="label" htmlFor="email">Email</label>
          <input id="email" type="email" required className="input" value={email} onChange={(e) => setEmail(e.target.value)}
            autoComplete="email" />
          <button className="btn-primary w-full" disabled={busy}>{busy ? 'Sending…' : 'Email me a code'}</button>
        </form>
      ) : (
        <form onSubmit={verify} className="space-y-3">
          <p className="text-sm text-sand-600">We sent a 6-digit code to <strong>{email}</strong>.</p>
          <input className="input text-center text-xl tracking-[0.5em]" inputMode="numeric" maxLength={6} required
            autoComplete="one-time-code" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            aria-label="Code" />
          <button className="btn-primary w-full" disabled={busy || code.length !== 6}>{busy ? 'Checking…' : 'Continue'}</button>
          <div className="flex justify-between text-sm">
            <button type="button" className="btn-link" onClick={() => setStep('email')}>Change email</button>
            <button type="button" className="btn-link" disabled={cooldown > 0} onClick={() => requestCode()}>
              {cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend code'}
            </button>
          </div>
        </form>
      )}
      {err && <p className="mt-4 text-sm text-red-700">{err}</p>}
    </div>
  );
}

export default function LoginPage() {
  return <Suspense><LoginInner /></Suspense>;
}
