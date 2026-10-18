'use client';

import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import type { AttributeDef } from '@/lib/types';

type Facets = Record<string, { value: string; count: number }[]>;

/** Filters live in the URL (?f.size=Double,Single&sort=price_asc&inStock=true) so pages stay shareable and SSR. */
export function Filters({ defs, facets }: { defs: AttributeDef[]; facets: Facets }) {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  function update(mutator: (p: URLSearchParams) => void) {
    const p = new URLSearchParams(params.toString());
    mutator(p);
    p.delete('page');
    router.push(`${pathname}?${p.toString()}`, { scroll: false });
  }

  function toggle(key: string, value: string) {
    update((p) => {
      const current = (p.get(`f.${key}`) || '').split(',').filter(Boolean);
      const next = current.includes(value) ? current.filter((v) => v !== value) : [...current, value];
      if (next.length) p.set(`f.${key}`, next.join(','));
      else p.delete(`f.${key}`);
    });
  }

  const filterable = defs.filter((d) => d.filterable !== false && facets[d.key]?.length);

  return (
    <div className="flex flex-wrap items-center gap-3 text-sm">
      {filterable.map((d) => {
        const selected = (params.get(`f.${d.key}`) || '').split(',').filter(Boolean);
        return (
          <details key={d.key} className="relative">
            <summary className="btn-secondary cursor-pointer list-none">
              {d.label}{selected.length ? ` (${selected.length})` : ''}
            </summary>
            <div className="absolute z-20 mt-1 w-56 rounded-md border border-sand-200 bg-white p-2 shadow-lg">
              {facets[d.key].map((f) => (
                <label key={f.value} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 hover:bg-sand-100">
                  <input type="checkbox" checked={selected.includes(f.value)} onChange={() => toggle(d.key, f.value)} />
                  <span>{f.value}</span>
                  <span className="ml-auto text-xs text-sand-400">{f.count}</span>
                </label>
              ))}
            </div>
          </details>
        );
      })}
      <label className="flex items-center gap-2">
        <input type="checkbox" checked={params.get('inStock') === 'true'}
          onChange={(e) => update((p) => (e.target.checked ? p.set('inStock', 'true') : p.delete('inStock')))} />
        In stock only
      </label>
      <select className="input ml-auto w-auto" value={params.get('sort') || ''} aria-label="Sort"
        onChange={(e) => update((p) => (e.target.value ? p.set('sort', e.target.value) : p.delete('sort')))}>
        <option value="">Featured</option>
        <option value="price_asc">Price: low to high</option>
        <option value="price_desc">Price: high to low</option>
      </select>
    </div>
  );
}
