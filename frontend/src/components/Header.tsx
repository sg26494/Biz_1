'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { SITE_NAME } from '@/lib/format';
import type { CartView } from '@/lib/types';

export function Header({ categories }: { categories: { name: string; slug: string }[] }) {
  const { user, ready } = useAuth();
  const [count, setCount] = useState(0);
  const [q, setQ] = useState('');
  const router = useRouter();

  useEffect(() => {
    if (!ready) return;
    const load = () =>
      api<CartView>('/cart')
        .then((c) => setCount(c.items.reduce((n, i) => n + i.line.qty, 0)))
        .catch(() => setCount(0));
    load();
    window.addEventListener('cart:changed', load);
    return () => window.removeEventListener('cart:changed', load);
  }, [ready, user]);

  return (
    <header className="sticky top-0 z-30 border-b border-sand-200 bg-sand-50/95 backdrop-blur">
      <div className="container-page flex h-16 items-center gap-4">
        <Link href="/" className="font-display text-xl font-semibold text-indigo-900">
          {SITE_NAME}
        </Link>
        <nav className="hidden gap-5 text-sm md:flex">
          {categories.map((c) => (
            <Link key={c.slug} href={`/c/${c.slug}`} className="hover:text-clay-600">
              {c.name}
            </Link>
          ))}
        </nav>
        <form
          className="ml-auto hidden max-w-xs flex-1 sm:block"
          onSubmit={(e) => {
            e.preventDefault();
            if (q.trim()) router.push(`/search?q=${encodeURIComponent(q.trim())}`);
          }}
        >
          <input className="input" placeholder="Search razai, sheets…" value={q} onChange={(e) => setQ(e.target.value)}
            aria-label="Search" />
        </form>
        <div className="ml-auto flex items-center gap-4 text-sm sm:ml-0">
          {user?.role === 'ADMIN' && <Link href="/admin" className="hover:text-clay-600">Admin</Link>}
          <Link href={user ? '/account' : '/login'} className="hover:text-clay-600">
            {user ? 'Account' : 'Log in'}
          </Link>
          <Link href="/cart" className="relative hover:text-clay-600" aria-label={`Cart, ${count} items`}>
            Cart
            {count > 0 && (
              <span className="absolute -right-4 -top-2 rounded-full bg-clay-600 px-1.5 text-[11px] text-white">{count}</span>
            )}
          </Link>
        </div>
      </div>
      <div className="container-page flex gap-4 overflow-x-auto pb-2 text-sm md:hidden">
        {categories.map((c) => (
          <Link key={c.slug} href={`/c/${c.slug}`} className="whitespace-nowrap hover:text-clay-600">{c.name}</Link>
        ))}
        <Link href="/search" className="whitespace-nowrap hover:text-clay-600">Search</Link>
      </div>
    </header>
  );
}
