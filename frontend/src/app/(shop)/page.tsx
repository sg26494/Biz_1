import Link from 'next/link';
import { ArchPhoto } from '@/components/home/ArchPhoto';
import { HAWA_MAHAL_GATE, HAWA_MAHAL_PATH, SKYLINE_SVG_INNER } from '@/components/home/art';
import { HomeProducts, type HomeProduct } from '@/components/home/HomeProducts';
import { NewsletterForm } from '@/components/home/NewsletterForm';
import { serverGet } from '@/lib/server-api';
import type { Page, ProductCard, ProductDetail } from '@/lib/types';

/*
 * HOME PAGE: Gulmohar Fabrics (Pink City edition), ported from the founders' index2.html.
 * Text lives right here in the JSX; colours live in src/app/gulmohar.css (:root).
 * The "Heritage Collection" products, prices and photos come from Admin -> Products.
 */
// Rendered per request (data itself is cached ~5 min), so the page never gets stuck empty
// if the API was down while the site was being built.
export const dynamic = 'force-dynamic';

const CATEGORY_KEY: Record<string, HomeProduct['category']> = {
  'jaipuri-rajai': 'rajai', 'mulmul-dohar': 'dohar', 'bed-sheets': 'sheet',
};

async function loadProducts(): Promise<HomeProduct[]> {
  const list = await serverGet<Page<ProductCard>>('/products?size=12');
  const details = await Promise.all((list?.items || []).map((p) => serverGet<ProductDetail>(`/products/${p.slug}`)));
  return details.filter((d): d is ProductDetail => !!d).map((d) => {
    const inStock = d.variants.filter((v) => v.inStock);
    const cheapest = [...d.variants].sort((a, b) => a.pricePaise - b.pricePaise)[0];
    const size = d.variants.length === 1 ? d.variants[0].attributes?.size : undefined;
    const badge = (d.attributes as Record<string, unknown>)?.badge;
    return {
      slug: d.slug,
      title: d.title,
      category: CATEGORY_KEY[d.category.slug] || 'other',
      typeLabel: [d.category.name, size].filter(Boolean).join(' · '),
      pricePaise: cheapest?.pricePaise || 0,
      mrpPaise: cheapest?.mrpPaise || 0,
      images: d.images.map((i) => ({ url: i.url, alt: i.alt })),
      badge: typeof badge === 'string' ? badge : undefined,
      quickAddVariantId: d.variants.length === 1 && inStock.length === 1 ? inStock[0].id : undefined,
      inStock: inStock.length > 0,
    };
  });
}

/*
 * Testimonials stay hidden until you have real customer reviews. Add them here as
 * { text, name, city } (with the customer's permission) and the section appears.
 */
const REVIEWS: { text: string; name: string; city: string }[] = [];

export default async function Home() {
  const products = await loadProducts();

  return (
    <div className="gm-root">
      {/* SECTION: HERO, part 1 (ivory band with the Hawa Mahal line drawing) */}
      <section className="gm-skyline-band">
        <div className="gm-band-text">
          <span className="gm-eyebrow">Hand block printed in Jaipur</span>
          <h2>Royal comfort from the <i>Pink City</i></h2>
          <span className="gm-band-hindi" lang="hi">गुलाबी नगरी की शाही रज़ाई</span>
        </div>
        <svg className="gm-hawa-art" viewBox="-5 -5 370 240" role="img" aria-label="Line drawing of Hawa Mahal, Jaipur">
          <path d={HAWA_MAHAL_PATH} />
          <path className="gm-gate" d={HAWA_MAHAL_GATE} />
        </svg>
      </section>
      <div className="gm-ground" />

      {/* SECTION: HERO, part 2 (terracotta band with the arched photo) */}
      <section className="gm-hero">
        <div className="gm-hero-text">
          <span className="gm-eyebrow">Sanganeri &amp; Bagru block prints</span>
          <h1>The softest embrace of the <i>Pink City</i>.</h1>
          <span className="gm-rule" />
          <p>Experience the luxury of authentic Sanganeri block prints. Hand-carved, naturally dyed, and crafted with pure breathable Mulmul cotton for a perfect night&apos;s sleep.</p>
          <a href="#products" className="gm-btn">Explore the Collection</a>
        </div>
        <ArchPhoto />
        <div className="gm-hero-side">
          <h3>Blessed by the Pink City, made by its artisans</h3>
          <ul>
            <li>Hand-carved teak blocks</li>
            <li>Natural, skin-safe dyes</li>
            <li>Pure cotton &amp; mulmul</li>
          </ul>
          <a href="#craft">Our Story</a>
        </div>
      </section>

      <Divider />

      {/* SECTION: CATEGORIES */}
      <section className="gm-categories" id="categories">
        <div className="gm-section-title">
          <span className="gm-eyebrow">Shop by Category</span>
          <h2>Crafted for every <i>season</i></h2>
        </div>
        <div className="gm-cat-grid">
          <CategoryCard href="/c/jaipuri-rajai" title="Jaipuri Rajai" print="gm-print-a" image="/images/rajai-1-1.jpg" />
          <CategoryCard href="/c/mulmul-dohar" title="Mulmul Dohars" print="gm-print-b"
            image="https://images.unsplash.com/photo-1522771739844-6a9f6d5f14af?q=80&w=800&auto=format&fit=crop" />
          <CategoryCard href="/c/bed-sheets" title="Bed Sheets" print="gm-print-c"
            image="https://images.unsplash.com/photo-1616486029423-aaa4789e8c9a?q=80&w=800&auto=format&fit=crop" />
          <CategoryCard href="/c/cushions-and-more" title="Cushions &amp; More" print="gm-print-b" />
        </div>
      </section>

      {/* SECTION: CRAFT STORY */}
      <section className="gm-craft" id="craft">
        <div className="gm-craft-grid">
          <div className="gm-craft-visual">
            <svg viewBox="0 0 200 220" fill="none" stroke="#CD7362" strokeWidth="1.6">
              <rect x="82" y="18" width="36" height="40" rx="3" fill="#F0E3DF" />
              <rect x="34" y="58" width="132" height="32" rx="4" fill="#F0E3DF" />
              <path d="M44 74 h112" strokeDasharray="4 5" />
              <g transform="translate(100 158)">
                <path d="M0 -44 C12 -26 26 -20 44 0 C26 20 12 26 0 44 C-12 26 -26 20 -44 0 C-26 -20 -12 -26 0 -44Z" />
                <path d="M0 -26 C7 -15 15 -11 26 0 C15 11 7 15 0 26 C-7 15 -15 11 -26 0 C-15 -11 -7 -15 0 -26Z" fill="#CD7362" fillOpacity=".18" />
                <circle r="6" fill="#CD7362" />
                <circle cx="-62" r="4" /><circle cx="62" r="4" />
              </g>
            </svg>
          </div>
          <div className="gm-craft-text">
            <span className="gm-eyebrow">The Art of Block Printing</span>
            <h2>Four centuries of craft, <i>one block</i> at a time</h2>
            <p>Every piece begins with a hand-carved teak wood block. Chhipa artisan families in Sanganer and Bagru press each colour by hand, block after block, using natural dyes from indigo, pomegranate and turmeric.</p>
            <p>The fabric is then washed and sun-dried under the Rajasthan sky. Tiny irregularities are the signature of a true handmade piece.</p>
            <div className="gm-steps">
              <div className="gm-step"><b>01</b><span>Hand-carved teak blocks</span></div>
              <div className="gm-step"><b>02</b><span>Natural, skin-safe dyes</span></div>
              <div className="gm-step"><b>03</b><span>Pure cotton &amp; mulmul</span></div>
            </div>
            <a href="#products" className="gm-btn">Discover the Craft</a>
          </div>
        </div>
        <svg className="gm-skyline" viewBox="0 0 1440 200" preserveAspectRatio="xMidYMax slice" aria-hidden="true"
          dangerouslySetInnerHTML={{ __html: SKYLINE_SVG_INNER }} />
      </section>

      {/* SECTION: PRODUCTS (from Admin -> Products) */}
      <section className="gm-products" id="products">
        <div className="gm-section-title" style={{ marginBottom: '1.5rem' }}>
          <span className="gm-eyebrow">Bestsellers</span>
          <h2>Heritage Collection</h2>
        </div>
        <HomeProducts products={products} />
        <div className="gm-center"><Link href="/c/jaipuri-rajai" className="gm-btn gm-btn-light">View All Products</Link></div>
      </section>

      {/* SECTION: TRUST BADGES */}
      <section className="gm-trust">
        <div className="gm-trust-grid">
          <div className="gm-trust-item">
            <svg viewBox="0 0 48 48"><path d="M24 4l5 10 11 2-8 8 2 11-10-5-10 5 2-11-8-8 11-2z" /></svg>
            <h4>100% Handmade</h4><p>Printed by Jaipur artisans</p>
          </div>
          <div className="gm-trust-item">
            <svg viewBox="0 0 48 48"><path d="M4 14h26v20H4zM30 20h8l6 7v7H30z" /><circle cx="12" cy="36" r="4" /><circle cx="36" cy="36" r="4" /></svg>
            <h4>Free Shipping</h4><p>Across India above ₹2,999</p>
          </div>
          <div className="gm-trust-item">
            <svg viewBox="0 0 48 48"><path d="M8 24a16 16 0 1 0 5-11.6" /><path d="M8 6v8h8" /></svg>
            <h4>Easy Returns</h4><p>7-day hassle-free returns</p>
          </div>
          <div className="gm-trust-item">
            <svg viewBox="0 0 48 48"><rect x="8" y="20" width="32" height="22" rx="3" /><path d="M15 20v-6a9 9 0 0118 0v6" /><circle cx="24" cy="31" r="3" /></svg>
            <h4>Secure Payment</h4><p>UPI, cards &amp; net banking</p>
          </div>
        </div>
      </section>

      {/* SECTION: TESTIMONIALS (shown once REVIEWS above has real reviews) */}
      {REVIEWS.length > 0 && (
        <section className="gm-reviews">
          <div className="gm-section-title">
            <span className="gm-eyebrow">Kind Words</span>
            <h2>Loved across <i>India</i></h2>
          </div>
          <div className="gm-review-grid">
            {REVIEWS.map((r) => (
              <div key={r.name + r.city} className="gm-review">
                <div className="gm-stars">★★★★★</div>
                <p>&ldquo;{r.text}&rdquo;</p>
                <b>{r.name}</b><small>{r.city}</small>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* SECTION: NEWSLETTER (sign-ups are saved in the database) */}
      <section className="gm-newsletter">
        <h2>Join our Pink City circle</h2>
        <p>Be the first to see new prints, festive offers and stories from our artisans. Get 10% off your first order with code PINKCITY10.</p>
        <NewsletterForm />
      </section>
    </div>
  );
}

function Divider() {
  return (
    <div className="gm-divider">
      <svg viewBox="0 0 24 24">
        <path d="M12 22C12 22 4 16 4 10C4 6.5 6.5 4 10 4C11.5 4 12 5 12 5C12 5 12.5 4 14 4C17.5 4 20 6.5 20 10C20 16 12 22 12 22Z" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M12 22V12" strokeLinecap="round" />
        <path d="M8 14C8 14 10 12 12 12C14 12 16 14 16 14" strokeLinecap="round" />
      </svg>
    </div>
  );
}

function CategoryCard({ href, title, print, image }: { href: string; title: string; print: string; image?: string }) {
  return (
    <Link className="gm-cat-card" href={href}>
      <div className={`gm-cat-img ${print}`}>
        <div className="gm-photo" style={image ? { backgroundImage: `url('${image}')` } : undefined} />
      </div>
      <h3 dangerouslySetInnerHTML={{ __html: title }} />
      <span>Shop now</span>
    </Link>
  );
}
