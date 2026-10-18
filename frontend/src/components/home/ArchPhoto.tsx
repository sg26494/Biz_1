'use client';

import { useEffect, useRef, useState } from 'react';

/**
 * Hero arch photo. Save the HD photo of Shri Govind Dev Ji as public/images/govind-dev-ji.jpg
 * (portrait works best). Until then a rajai photo is shown and the caption is hidden.
 */
export function ArchPhoto() {
  const [fallback, setFallback] = useState(false);
  const img = useRef<HTMLImageElement>(null);
  // The photo may fail before React is ready to hear about it, so check once on mount too.
  useEffect(() => {
    const el = img.current;
    if (el && el.complete && el.naturalWidth === 0) setFallback(true);
  }, []);
  return (
    <figure className={`gm-hero-arch${fallback ? ' gm-fallback' : ''}`}>
      <svg className="gm-arch-finial" viewBox="0 0 14 22" aria-hidden="true">
        <path d="M7 0V8M7 8C3 8 2 12 2 14C2 17 4.5 19 7 19C9.5 19 12 17 12 14C12 12 11 8 7 8ZM3 21H11" fill="none" stroke="#A9564A" strokeWidth="1" />
      </svg>
      <div className="gm-arch-wrap">
        <div className="gm-arch-photo">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            ref={img}
            src={fallback ? '/images/rajai-1-1.jpg' : '/images/govind-dev-ji.jpg'}
            alt={fallback ? 'Hand block printed rajai' : 'Shri Govind Dev Ji, Jaipur'}
            onError={() => setFallback(true)}
          />
        </div>
        <svg className="gm-arch-frame" viewBox="0 0 100 130" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0 130V46C0 31 18 26 33 16C42 10 48 5 50 0C52 5 58 10 67 16C82 26 100 31 100 46V130Z" />
        </svg>
      </div>
      <figcaption className="gm-arch-caption"><span lang="hi">श्री गोविंद देव जी</span></figcaption>
    </figure>
  );
}
