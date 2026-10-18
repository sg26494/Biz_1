'use client';

import Image from 'next/image';
import { useState } from 'react';
import type { ImageView } from '@/lib/types';

export function Gallery({ images, title }: { images: ImageView[]; title: string }) {
  const [i, setI] = useState(0);
  if (images.length === 0) {
    return <div className="gm-print-a aspect-[4/5] rounded-b-lg rounded-t-[220px]" />;
  }
  const img = images[Math.min(i, images.length - 1)];
  return (
    <div>
      <div className="gm-print-a relative aspect-[4/5] overflow-hidden rounded-b-lg rounded-t-[220px]">
        <Image src={img.url} alt={img.alt || title} fill priority sizes="(max-width: 768px) 100vw, 50vw" className="object-cover" />
      </div>
      {images.length > 1 && (
        <div className="mt-3 flex gap-2 overflow-x-auto">
          {images.map((im, idx) => (
            <button key={im.id} onClick={() => setI(idx)} aria-label={`Photo ${idx + 1}`}
              className={`relative h-20 w-16 flex-none overflow-hidden rounded border-2 ${idx === i ? 'border-clay-600' : 'border-transparent'}`}>
              <Image src={im.url} alt="" fill sizes="64px" className="object-cover" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
