import Link from 'next/link';
import { SITE_NAME } from '@/lib/format';

const LINKS = [
  ['About', '/pages/about'], ['Contact', '/pages/contact'], ['Shipping', '/pages/shipping'],
  ['Returns & refunds', '/pages/returns'], ['Privacy', '/pages/privacy'], ['Terms', '/pages/terms'],
];

export function Footer() {
  return (
    <footer className="mt-16 border-t border-sand-200 bg-sand-100">
      <div className="container-page flex flex-col gap-6 py-10 text-sm sm:flex-row sm:justify-between">
        <div>
          <p className="font-display text-lg text-indigo-900">{SITE_NAME}</p>
          <p className="mt-1 max-w-xs text-sand-600">Hand-block printed textiles from Rajasthan. Prices include GST.</p>
        </div>
        <ul className="grid grid-cols-2 gap-x-8 gap-y-2">
          {LINKS.map(([label, href]) => (
            <li key={href}><Link href={href} className="hover:text-clay-600">{label}</Link></li>
          ))}
        </ul>
      </div>
      <p className="container-page pb-6 text-xs text-sand-600">© {new Date().getFullYear()} {SITE_NAME}</p>
    </footer>
  );
}
