import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import { AddToCart } from '@/components/AddToCart';
import { Gallery } from '@/components/Gallery';
import { SITE_URL } from '@/lib/format';
import { serverGet } from '@/lib/server-api';
import type { ProductDetail } from '@/lib/types';

// ISR: rebuilt on demand when an admin edits the product (see /api/revalidate), else every 5 minutes.
export const revalidate = 300;

type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const p = await serverGet<ProductDetail>(`/products/${slug}`);
  if (!p) return {};
  const description = p.seoDescription || p.description.replace(/<[^>]+>/g, '').slice(0, 160);
  return {
    title: p.seoTitle || p.title,
    description,
    alternates: { canonical: `/products/${p.slug}` },
    openGraph: { title: p.title, description, images: p.images.slice(0, 1).map((i) => ({ url: i.url })) },
  };
}

export default async function ProductPage({ params }: Props) {
  const { slug } = await params;
  const p = await serverGet<ProductDetail>(`/products/${slug}`);
  if (!p) notFound();

  const prices = p.variants.map((v) => v.pricePaise / 100);
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: p.title,
    description: p.description.replace(/<[^>]+>/g, ''),
    image: p.images.map((i) => i.url),
    sku: p.variants[0]?.sku,
    category: p.category.name,
    offers: {
      '@type': 'AggregateOffer',
      priceCurrency: 'INR',
      lowPrice: prices.length ? Math.min(...prices) : undefined,
      highPrice: prices.length ? Math.max(...prices) : undefined,
      offerCount: p.variants.length,
      availability: p.variants.some((v) => v.inStock) ? 'https://schema.org/InStock' : 'https://schema.org/OutOfStock',
      url: `${SITE_URL}/products/${p.slug}`,
    },
  };

  return (
    <div className="container-page py-8">
      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, '\\u003c') }} />
      <nav className="mb-4 text-sm text-sand-600">
        <Link href="/" className="hover:text-clay-600">Home</Link> /{' '}
        <Link href={`/c/${p.category.slug}`} className="hover:text-clay-600">{p.category.name}</Link>
      </nav>
      <div className="grid gap-10 md:grid-cols-2">
        <Gallery images={p.images} title={p.title} />
        <div>
          <h1 className="text-3xl leading-tight">{p.title}</h1>
          <div className="mt-6">
            <AddToCart variants={p.variants} defs={p.category.attributeDefs} />
          </div>
          <div className="mt-8 space-y-4 text-sm leading-relaxed">
            <p className="whitespace-pre-line">{p.description}</p>
            {Object.keys(p.attributes || {}).length > 0 && (
              <dl className="grid grid-cols-[auto,1fr] gap-x-6 gap-y-1">
                {Object.entries(p.attributes).map(([k, v]) => (
                  <div key={k} className="contents">
                    <dt className="capitalize text-sand-600">{k.replace(/_/g, ' ')}</dt>
                    <dd>{String(v)}</dd>
                  </div>
                ))}
              </dl>
            )}
            <p className="text-sand-600">
              {p.returnable ? 'Easy returns within the return window. ' : 'This item cannot be returned. '}
              <Link href="/pages/returns" className="btn-link">Returns policy</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
