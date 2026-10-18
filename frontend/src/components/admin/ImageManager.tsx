'use client';

import Image from 'next/image';
import { useState } from 'react';
import { api, errorMessage } from '@/lib/api';

type Img = { id: number; variantId?: number; url: string; alt?: string };
type Presign = { uploadUrl: string; storageKey: string; publicUrl: string; headers: Record<string, string> };

/** Upload goes browser -> storage via a presigned PUT; the API only records the key. */
export function ImageManager({ productId, images, onChange }: { productId: number; images: Img[]; onChange: () => void }) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function upload(files: FileList | null) {
    if (!files?.length) return;
    setBusy(true);
    setErr(null);
    try {
      for (const file of Array.from(files)) {
        if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw new Error(`${file.name}: use JPEG, PNG or WebP`);
        if (file.size > 10 * 1024 * 1024) throw new Error(`${file.name}: max 10 MB`);
        const dims = await new Promise<{ w: number; h: number }>((resolve) => {
          const im = new window.Image();
          im.onload = () => resolve({ w: im.naturalWidth, h: im.naturalHeight });
          im.onerror = () => resolve({ w: 0, h: 0 });
          im.src = URL.createObjectURL(file);
        });
        const p = await api<Presign>('/admin/uploads/presign', { method: 'POST',
          body: { productId, contentType: file.type, contentLength: file.size } });
        const put = await fetch(p.uploadUrl, { method: 'PUT', headers: p.headers, body: file });
        if (!put.ok) throw new Error(`Upload failed (${put.status})`);
        await api(`/admin/products/${productId}/images`, { method: 'POST',
          body: { storageKey: p.storageKey, width: dims.w || undefined, height: dims.h || undefined } });
      }
      onChange();
    } catch (e) {
      setErr(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function move(idx: number, dir: -1 | 1) {
    const ids = images.map((i) => i.id);
    const j = idx + dir;
    if (j < 0 || j >= ids.length) return;
    [ids[idx], ids[j]] = [ids[j], ids[idx]];
    await api(`/admin/products/${productId}/images/order`, { method: 'PUT', body: { imageIds: ids } });
    onChange();
  }

  return (
    <section className="card p-5">
      <div className="flex items-center justify-between">
        <h2 className="text-lg">Photos</h2>
        <label className="btn-secondary cursor-pointer">
          {busy ? 'Uploading…' : 'Upload photos'}
          <input type="file" multiple accept="image/jpeg,image/png,image/webp" className="hidden" disabled={busy}
            onChange={(e) => upload(e.target.files)} />
        </label>
      </div>
      {err && <p className="mt-2 text-sm text-red-700">{err}</p>}
      <div className="mt-4 grid grid-cols-3 gap-3 sm:grid-cols-5">
        {images.map((im, idx) => (
          <div key={im.id} className="text-xs">
            <div className="relative aspect-[4/5] overflow-hidden rounded bg-sand-100">
              <Image src={im.url} alt={im.alt || ''} fill sizes="150px" className="object-cover" />
              {idx === 0 && <span className="badge absolute left-1 top-1 bg-white/90">Cover</span>}
            </div>
            <div className="mt-1 flex justify-between">
              <span className="flex gap-2">
                <button className="btn-link" onClick={() => move(idx, -1)} aria-label="Move left">←</button>
                <button className="btn-link" onClick={() => move(idx, 1)} aria-label="Move right">→</button>
              </span>
              <button className="btn-link text-red-700" onClick={async () => {
                if (confirm('Remove this photo?')) {
                  await api(`/admin/products/${productId}/images/${im.id}`, { method: 'DELETE' });
                  onChange();
                }
              }}>Remove</button>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
