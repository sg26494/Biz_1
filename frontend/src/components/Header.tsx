'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import type { CartView } from '@/lib/types';

/** Gulmohar header (from the founders' homepage design) with live cart count and login state. */
export function Header() {
  const { user, ready } = useAuth();
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);

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

  const close = () => setOpen(false);

  return (
    <>
      {/* ANNOUNCEMENT BAR: change the offer text here */}
      <div className="gm-announce">
        Free shipping across India above ₹2,999 &nbsp;·&nbsp; Use code <b>PINKCITY10</b> for 10% off your first order
      </div>
      <header className="gm-header">
        <Link href="/" className="gm-logo-container gm-logo-lockup" aria-label="Gulmohar Fabrics home">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img className="gm-logo-mark" src="/images/logo-mark.png" alt="" />
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img className="gm-logo-word" src="/images/logo-wordmark.png" alt="Gulmohar Fabrics" />
        </Link>

        <div className="gm-header-right">
          <nav className={`gm-nav${open ? ' gm-open' : ''}`}>
            <Link href="/c/jaipuri-rajai" onClick={close}>Shop Rajais</Link>
            <Link href="/#products" onClick={close}>Dohars &amp; Sheets</Link>
            <Link href="/#craft" onClick={close}>Our Heritage</Link>
            <Link href="/#craft" onClick={close}>Block Printing</Link>
            <a href="#contact" onClick={close}>Contact</a>
            {user?.role === 'ADMIN' && <Link href="/admin" onClick={close}>Admin</Link>}
          </nav>
          <Link href="/search" className="gm-icon-btn gm-icon-search" aria-label="Search">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4"><circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" /></svg>
          </Link>
          <Link href={user ? '/account' : '/login'} className="gm-icon-btn" aria-label={user ? 'My account' : 'Log in'}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4"><circle cx="12" cy="8" r="4" /><path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" /></svg>
          </Link>
          <Link href="/cart" className="gm-icon-btn" aria-label={`Bag, ${count} items`}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M5 8h14l-1.5 12h-11z" /><path d="M9 8V6a3 3 0 016 0v2" /></svg>
            <span className="gm-cart-count">{count}</span>
          </Link>
          <button className="gm-icon-btn gm-menu-toggle" aria-label="Open menu" aria-expanded={open} onClick={() => setOpen(!open)}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4"><path d="M3 7h18M3 12h18M3 17h18" /></svg>
          </button>
        </div>
      </header>
    </>
  );
}
