'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useAuth } from '@/lib/auth';

export function RequireAuth({ children, admin = false }: { children: React.ReactNode; admin?: boolean }) {
  const { user, ready } = useAuth();
  const router = useRouter();
  const path = usePathname();
  useEffect(() => {
    if (ready && !user) router.replace(`/login?next=${encodeURIComponent(path)}`);
  }, [ready, user, router, path]);
  if (!ready || !user) return <div className="container-page py-16 text-sand-600">Loading…</div>;
  if (admin && user.role !== 'ADMIN') return <div className="container-page py-16">You don&apos;t have access to this page.</div>;
  return <>{children}</>;
}
