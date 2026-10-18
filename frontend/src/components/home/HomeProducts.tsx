'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { api, errorMessage } from '@/lib/api';
import { money } from '@/lib/format';

export type HomeProduct = {
  slug: string;
  title: string;
  category: 'rajai' | 'dohar' | 'sheet' | 'other';
  typeLabel: string;
  pricePaise: number;
  mrpPaise: number;
  images: { url: string; alt?: string }[];
  badge?: string;
  /** Set when there is exactly one in-stock variant, so "Add to Bag" can add it directly. */
  quickAddVariantId?: number;
  inStock: boolean;
};

const PRINTS = ['gm-print-a', 'gm-print-b', 'gm-print-c'];
const TABS: [HomeProduct['category'] | 'all', string][] = [['all', 'All'], ['rajai', 'Rajai'], ['dohar', 'Dohars'], ['sheet', 'Bed Sheets']];

/** "Heritage Collection": filter tabs, photo slideshows and Add to Bag wired to the real cart. */
export function HomeProducts({ products }: { products: HomeProduct[] }) {
  const [filter, setFilter] = useState<HomeProduct['category'] | 'all'>('all');

  // Category cards above set the filter through the URL hash (#products-rajai etc.).
  useEffect(() => {
    const sync = () => {
      const m = window.location.hash.match(/^#products-(rajai|dohar|sheet|all)$/);
      if (m) setFilter(m[1] as HomeProduct['category'] | 'all');
    };
    sync();
    window.addEventListener('hashchange', sync);
    return () => window.removeEventListener('hashchange', sync);
  }, []);

  return (
    <>
      <div className="gm-tabs">
        {TABS.map(([key, label]) => (
          <button key={key} className={`gm-tab${filter === key ? ' gm-active' : ''}`} onClick={() => setFilter(key)}>{label}</button>
        ))}
      </div>
      <div className="gm-product-grid">
        {products.map((p, i) => (
          <div key={p.slug} className={`gm-product-card${filter !== 'all' && p.category !== filter ? ' gm-hide' : ''}`}>
            <Gallery product={p} print={PRINTS[i % 3]} />
            <p className="gm-product-type">{p.typeLabel}</p>
            <h3 className="gm-product-title"><Link href={`/products/${p.slug}`}>{p.title}</Link></h3>
            <div className="gm-product-row">
              <p className="gm-product-price">
                {money(p.pricePaise)} {p.mrpPaise > p.pricePaise && <s>{money(p.mrpPaise)}</s>}
              </p>
              <AddToBag product={p} />
            </div>
          </div>
        ))}
      </div>
      {products.length === 0 && <p>New prints are on their way. Please check back soon.</p>}
    </>
  );
}

function AddToBag({ product }: { product: HomeProduct }) {
  const [label, setLabel] = useState('Add to Bag');
  if (!product.inStock) return <span className="gm-add-btn" style={{ opacity: 0.5, cursor: 'default' }}>Sold out</span>;
  if (!product.quickAddVariantId) return <Link className="gm-add-btn" href={`/products/${product.slug}`}>Choose size</Link>;
  return (
    <button
      className="gm-add-btn"
      onClick={async () => {
        try {
          await api('/cart/items', { method: 'POST', body: { variantId: product.quickAddVariantId, qty: 1 } });
          window.dispatchEvent(new Event('cart:changed'));
          setLabel('Added ✓');
        } catch (e) {
          setLabel(errorMessage(e).length > 24 ? 'Unavailable' : errorMessage(e));
        }
        setTimeout(() => setLabel('Add to Bag'), 1800);
      }}
    >
      {label}
    </button>
  );
}

/** Photo slideshow: arrows, dots, swipe on phones, auto-play (pauses on hover). Pattern shows if no photos. */
function Gallery({ product, print }: { product: HomeProduct; print: string }) {
  const [i, setI] = useState(0);
  const paused = useRef(false);
  const startX = useRef(0);
  const n = product.images.length;
  const show = (k: number) => setI(((k % n) + n) % n);

  useEffect(() => {
    if (n < 2) return;
    const t = setInterval(() => {
      if (!paused.current) setI((x) => (x + 1) % n);
    }, 4000);
    return () => clearInterval(t);
  }, [n]);

  return (
    <div
      className={`gm-product-img ${print}${n > 0 ? ' gm-gallery' : ''}`}
      onMouseEnter={() => (paused.current = true)}
      onMouseLeave={() => (paused.current = false)}
      onTouchStart={(e) => (startX.current = e.touches[0].clientX)}
      onTouchEnd={(e) => {
        const dx = e.changedTouches[0].clientX - startX.current;
        if (Math.abs(dx) > 40) show(dx < 0 ? i + 1 : i - 1);
      }}
    >
      {n === 0 && <div className="gm-photo" />}
      {product.images.map((img, k) => (
        // eslint-disable-next-line @next/next/no-img-element
        <img key={img.url} className={`gm-slide${k === i ? ' gm-active' : ''}`} src={img.url} alt={img.alt || product.title}
          loading={k === 0 ? 'eager' : 'lazy'} />
      ))}
      {n > 1 && (
        <>
          <button className="gm-g-arrow gm-g-prev" aria-label="Previous photo" onClick={() => show(i - 1)}>‹</button>
          <button className="gm-g-arrow gm-g-next" aria-label="Next photo" onClick={() => show(i + 1)}>›</button>
          <div className="gm-g-dots">
            {product.images.map((img, k) => (
              <button key={img.url} aria-label={`Photo ${k + 1}`} className={k === i ? 'gm-on' : ''} onClick={() => show(k)} />
            ))}
          </div>
        </>
      )}
      {product.badge && <span className="gm-tag">{product.badge}</span>}
    </div>
  );
}
