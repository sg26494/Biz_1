import Link from 'next/link';

/** Gulmohar footer. CHANGE address, phone, email and social links here. */
export function Footer() {
  return (
    <footer className="gm-footer gm-root mt-16" id="contact">
      <div className="gm-foot-grid">
        <div className="gm-foot-about">
          <Link href="/" className="gm-logo-container gm-logo-lockup">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="gm-logo-mark" src="/images/logo-mark.png" alt="" />
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img className="gm-logo-word" src="/images/logo-wordmark-light.png" alt="Gulmohar Fabrics" />
          </Link>
          <p>Hand block printed rajai, dohars and bed linen, crafted with love by artisans of Jaipur, Rajasthan.</p>
          <div className="gm-socials">
            {/* Add your real profile links */}
            <a href="#" aria-label="Instagram"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><rect x="3" y="3" width="18" height="18" rx="5" /><circle cx="12" cy="12" r="4" /><circle cx="17.5" cy="6.5" r="1" fill="currentColor" /></svg></a>
            <a href="#" aria-label="Facebook"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M14 8h3V4h-3c-2.8 0-4 1.8-4 4v2H7v4h3v8h4v-8h3l1-4h-4V8.5c0-.3.2-.5.5-.5z" /></svg></a>
            <a href="#" aria-label="WhatsApp"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M4 20l1.3-4A8 8 0 1 1 8 18.7z" /></svg></a>
          </div>
        </div>
        <div>
          <h5>Shop</h5>
          <ul>
            <li><Link href="/c/jaipuri-rajai">Jaipuri Rajai</Link></li>
            <li><Link href="/c/mulmul-dohar">Mulmul Dohars</Link></li>
            <li><Link href="/c/bed-sheets">Bed Sheets</Link></li>
            <li><Link href="/c/cushions-and-more">Cushions &amp; More</Link></li>
          </ul>
        </div>
        <div>
          <h5>Help</h5>
          <ul>
            <li><Link href="/account/orders">Track Order</Link></li>
            <li><Link href="/pages/shipping">Shipping Policy</Link></li>
            <li><Link href="/pages/returns">Returns &amp; Refunds</Link></li>
            <li><Link href="/pages/privacy">Privacy Policy</Link></li>
            <li><Link href="/pages/terms">Terms</Link></li>
          </ul>
        </div>
        <div>
          <h5>Visit Us</h5>
          <ul>
            <li>Johari Bazaar, Jaipur,<br />Rajasthan 302003</li>
            <li><a href="tel:+919999999999">+91 99999 99999</a></li>
            <li><a href="mailto:hello@yourbrand.com">hello@yourbrand.com</a></li>
            <li>Mon to Sat, 10am to 8pm</li>
          </ul>
        </div>
      </div>
      <div className="gm-copyright">© {new Date().getFullYear()} Gulmohar Fabrics · Handcrafted in the Pink City</div>
    </footer>
  );
}
