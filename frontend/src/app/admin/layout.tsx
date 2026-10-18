'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { RequireAuth } from '@/components/RequireAuth';
import { SITE_NAME } from '@/lib/format';

const NAV: [string, string][] = [
  ['/admin', 'Dashboard'], ['/admin/orders', 'Orders'], ['/admin/products', 'Products'],
  ['/admin/categories', 'Categories'], ['/admin/inventory', 'Inventory'], ['/admin/payments', 'Payments & refunds'],
  ['/admin/coupons', 'Coupons'], ['/admin/customers', 'Customers'], ['/admin/content', 'Content'],
  ['/admin/settings', 'Settings'], ['/admin/admins', 'Admins'], ['/admin/audit', 'Audit log'],
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  return (
    <RequireAuth admin>
      <div className="min-h-screen bg-sand-50 md:flex">
        <aside className="border-b border-sand-200 bg-white md:w-56 md:flex-none md:border-b-0 md:border-r">
          <Link href="/" className="block px-4 py-4 font-display text-lg text-indigo-900">{SITE_NAME} admin</Link>
          <nav className="flex gap-1 overflow-x-auto px-2 pb-2 text-sm md:flex-col md:pb-4">
            {NAV.map(([href, label]) => {
              const active = href === '/admin' ? path === href : path.startsWith(href);
              return (
                <Link key={href} href={href}
                  className={`whitespace-nowrap rounded px-3 py-2 ${active ? 'bg-clay-50 font-medium text-clay-700' : 'hover:bg-sand-100'}`}>
                  {label}
                </Link>
              );
            })}
          </nav>
        </aside>
        <main className="min-w-0 flex-1 p-4 md:p-8">{children}</main>
      </div>
    </RequireAuth>
  );
}
