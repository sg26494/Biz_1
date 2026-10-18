import Image from 'next/image';
import Link from 'next/link';
import { discountPercent, money } from '@/lib/format';
import type { ProductCard as Card } from '@/lib/types';

export function ProductCard({ p }: { p: Card }) {
  const off = discountPercent(p.mrpPaise, p.pricePaise);
  return (
    <Link href={`/products/${p.slug}`} className="group block">
      <div className="relative aspect-[4/5] overflow-hidden rounded-lg bg-sand-100">
        {p.imageUrl ? (
          <Image src={p.imageUrl} alt={p.imageAlt || p.title} fill sizes="(max-width: 640px) 50vw, 25vw"
            className="object-cover transition duration-300 group-hover:scale-[1.03]" />
        ) : (
          <div className="flex h-full items-center justify-center text-xs text-sand-400">No photo yet</div>
        )}
        {!p.inStock && (
          <span className="badge absolute left-2 top-2 bg-white/90 text-sand-800">Sold out</span>
        )}
      </div>
      <h3 className="mt-2 line-clamp-2 font-sans text-sm text-sand-800">{p.title}</h3>
      <p className="mt-1 text-sm">
        <span className="font-semibold text-indigo-900">{money(p.pricePaise)}</span>
        {off > 0 && (
          <>
            <span className="ml-2 text-xs text-sand-400 line-through">{money(p.mrpPaise)}</span>
            <span className="ml-1 text-xs text-clay-600">{off}% off</span>
          </>
        )}
      </p>
    </Link>
  );
}

export function ProductGrid({ items }: { items: Card[] }) {
  if (items.length === 0) return <p className="py-12 text-center text-sand-600">No products match these filters.</p>;
  return (
    <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 lg:grid-cols-4">
      {items.map((p) => <ProductCard key={p.id} p={p} />)}
    </div>
  );
}
