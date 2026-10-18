'use client';

import { useState } from 'react';
import { api, errorMessage } from '@/lib/api';
import { istDate } from '@/lib/format';
import { useApi } from '@/lib/useApi';

type CmsPage = { id: number; slug: string; title: string; bodyHtml: string; isPublished: boolean; updatedAt: string };
type Banner = { id: number; title: string; subtitle?: string; imageUrl: string; linkUrl?: string; placement: string;
  sortOrder: number; isActive: boolean };

export default function AdminContent() {
  const pages = useApi<CmsPage[]>('/admin/pages');
  const banners = useApi<Banner[]>('/admin/banners');
  const [page, setPage] = useState<Partial<CmsPage> | null>(null);
  const [banner, setBanner] = useState<Partial<Banner> | null>(null);
  const [err, setErr] = useState<string | null>(null);

  async function savePage(e: React.FormEvent) {
    e.preventDefault();
    if (!page) return;
    try {
      const body = { slug: page.slug, title: page.title, bodyHtml: page.bodyHtml || '', isPublished: !!page.isPublished };
      if (page.id) await api(`/admin/pages/${page.id}`, { method: 'PUT', body });
      else await api('/admin/pages', { method: 'POST', body });
      setPage(null);
      pages.reload();
    } catch (e2) {
      setErr(errorMessage(e2));
    }
  }

  async function saveBanner(e: React.FormEvent) {
    e.preventDefault();
    if (!banner) return;
    try {
      const body = { ...banner, placement: banner.placement || 'HOME_HERO' };
      if (banner.id) await api(`/admin/banners/${banner.id}`, { method: 'PUT', body });
      else await api('/admin/banners', { method: 'POST', body });
      setBanner(null);
      banners.reload();
    } catch (e2) {
      setErr(errorMessage(e2));
    }
  }

  return (
    <div className="max-w-5xl space-y-10">
      {err && <p className="text-sm text-red-700">{err}</p>}
      <section>
        <div className="flex items-center justify-between"><h1 className="text-2xl">Pages</h1>
          <button className="btn-secondary" onClick={() => setPage({ isPublished: false })}>New page</button></div>
        <p className="mt-1 text-sm text-sand-600">Policy pages (privacy, terms, shipping, returns, contact) must be live before Razorpay KYC.</p>
        {page && (
          <form onSubmit={savePage} className="card mt-4 grid gap-3 p-4 sm:grid-cols-2">
            <div><label className="label">Title</label><input className="input" required value={page.title || ''} onChange={(e) => setPage({ ...page, title: e.target.value })} /></div>
            <div><label className="label">Slug</label><input className="input" required value={page.slug || ''} onChange={(e) => setPage({ ...page, slug: e.target.value })} /></div>
            <div className="sm:col-span-2"><label className="label">Body (HTML: &lt;p&gt;, &lt;h2&gt;, &lt;ul&gt;, &lt;a&gt;)</label>
              <textarea className="input min-h-64 font-mono text-xs" value={page.bodyHtml || ''} onChange={(e) => setPage({ ...page, bodyHtml: e.target.value })} /></div>
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={!!page.isPublished} onChange={(e) => setPage({ ...page, isPublished: e.target.checked })} /> Published</label>
            <div className="flex gap-2 sm:col-span-2"><button className="btn-primary">Save page</button><button type="button" className="btn-secondary" onClick={() => setPage(null)}>Cancel</button></div>
          </form>
        )}
        <table className="table card mt-4">
          <thead><tr><th>Title</th><th>URL</th><th>Published</th><th>Updated</th><th></th></tr></thead>
          <tbody>{pages.data?.map((p) => (
            <tr key={p.id}><td>{p.title}</td><td>/pages/{p.slug}</td><td>{p.isPublished ? 'Yes' : 'No'}</td><td>{istDate(p.updatedAt)}</td>
              <td><button className="btn-link" onClick={() => setPage(p)}>Edit</button></td></tr>
          ))}</tbody>
        </table>
      </section>

      <section>
        <div className="flex items-center justify-between"><h2 className="text-2xl">Banners</h2>
          <button className="btn-secondary" onClick={() => setBanner({ isActive: true, placement: 'HOME_HERO', sortOrder: 0 })}>New banner</button></div>
        {banner && (
          <form onSubmit={saveBanner} className="card mt-4 grid gap-3 p-4 sm:grid-cols-2">
            <div><label className="label">Title</label><input className="input" required value={banner.title || ''} onChange={(e) => setBanner({ ...banner, title: e.target.value })} /></div>
            <div><label className="label">Subtitle</label><input className="input" value={banner.subtitle || ''} onChange={(e) => setBanner({ ...banner, subtitle: e.target.value })} /></div>
            <div><label className="label">Image URL</label><input className="input" required value={banner.imageUrl || ''} onChange={(e) => setBanner({ ...banner, imageUrl: e.target.value })} /></div>
            <div><label className="label">Link</label><input className="input" value={banner.linkUrl || ''} onChange={(e) => setBanner({ ...banner, linkUrl: e.target.value })} placeholder="/c/jaipuri-razai" /></div>
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={!!banner.isActive} onChange={(e) => setBanner({ ...banner, isActive: e.target.checked })} /> Active</label>
            <div className="flex gap-2 sm:col-span-2"><button className="btn-primary">Save banner</button><button type="button" className="btn-secondary" onClick={() => setBanner(null)}>Cancel</button></div>
          </form>
        )}
        <table className="table card mt-4">
          <thead><tr><th>Title</th><th>Placement</th><th>Active</th><th></th></tr></thead>
          <tbody>{banners.data?.map((b) => (
            <tr key={b.id}><td>{b.title}</td><td>{b.placement}</td><td>{b.isActive ? 'Yes' : 'No'}</td>
              <td className="whitespace-nowrap"><button className="btn-link" onClick={() => setBanner(b)}>Edit</button>
                <button className="btn-link ml-3" onClick={async () => { if (confirm('Delete banner?')) { await api(`/admin/banners/${b.id}`, { method: 'DELETE' }); banners.reload(); } }}>Delete</button></td></tr>
          ))}</tbody>
        </table>
      </section>
    </div>
  );
}
