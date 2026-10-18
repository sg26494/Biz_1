import Image from 'next/image';
import Link from 'next/link';
import { ProductGrid } from '@/components/ProductCard';
import { serverGet } from '@/lib/server-api';
import type { Category, Page, ProductCard } from '@/lib/types';

export const revalidate = 300;

type Banner = { id: number; title: string; subtitle?: string; imageUrl: string; linkUrl?: string; placement: string };

export default async function Home() {
  const [banners, categories, latest] = await Promise.all([
    serverGet<Banner[]>('/banners'),
    serverGet<Category[]>('/categories'),
    serverGet<Page<ProductCard>>('/products?size=8'),
  ]);
  const hero = banners?.find((b) => b.placement === 'HOME_HERO');

  return (
    <>
      <section className="relative overflow-hidden bg-indigo-900 text-white">
        {hero && (
          <Image src={hero.imageUrl} alt="" fill priority sizes="100vw" className="object-cover opacity-40" />
        )}
        <div className="container-page relative py-20 sm:py-28">
          <h1 className="max-w-xl text-4xl leading-tight text-white sm:text-5xl">
            {hero?.title || 'Jaipuri razai, block-printed by hand'}
          </h1>
          <p className="mt-4 max-w-md text-sand-100">
            {hero?.subtitle || 'Light, warm cotton quilts and bed sheets from Rajasthan. Delivered across India.'}
          </p>
          <Link href={hero?.linkUrl || '/c/jaipuri-razai'} className="btn-primary mt-8">Shop now</Link>
        </div>
      </section>

      <section className="container-page mt-12">
        <h2 className="text-2xl">Shop by category</h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          {(categories || []).filter((c) => !c.parentId).map((c) => (
            <Link key={c.id} href={`/c/${c.slug}`} className="card group flex items-end overflow-hidden p-6 hover:border-clay-300">
              <div>
                <p className="font-display text-xl text-indigo-900 group-hover:text-clay-600">{c.name}</p>
                {c.description && <p className="mt-1 text-sm text-sand-600">{c.description}</p>}
              </div>
            </Link>
          ))}
        </div>
      </section>

      <section className="container-page mt-12">
        <div className="mb-4 flex items-baseline justify-between">
          <h2 className="text-2xl">New arrivals</h2>
        </div>
        <ProductGrid items={latest?.items || []} />
      </section>
    </>
  );
}
