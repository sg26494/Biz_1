'use client';

import { Fragment, useState } from 'react';
import { istDate } from '@/lib/format';
import { useApi } from '@/lib/useApi';

type Row = { id: number; actorEmail?: string; action: string; entityType: string; entityId?: string;
  beforeState?: string; afterState?: string; reason?: string; ip?: string; createdAt: string };

export default function AdminAudit() {
  const [entityType, setEntityType] = useState('');
  const [page, setPage] = useState(0);
  const { data } = useApi<Row[]>(`/admin/audit-logs?page=${page}${entityType ? `&entityType=${entityType}` : ''}`);
  const [open, setOpen] = useState<number | null>(null);
  return (
    <div>
      <h1 className="text-2xl">Audit log</h1>
      <select className="input mt-4 w-auto" value={entityType} onChange={(e) => { setEntityType(e.target.value); setPage(0); }}>
        <option value="">Everything</option>
        {['order', 'product', 'variant', 'category', 'coupon', 'user', 'app_setting', 'warehouse', 'cms_page', 'banner'].map((t) => <option key={t}>{t}</option>)}
      </select>
      <table className="table card mt-4">
        <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Entity</th><th>Reason</th><th></th></tr></thead>
        <tbody>{data?.map((r) => (
          <Fragment key={r.id}>
            <tr><td className="whitespace-nowrap">{istDate(r.createdAt)}</td><td>{r.actorEmail || 'system'}</td>
              <td>{r.action}</td><td>{r.entityType} {r.entityId}</td><td>{r.reason}</td>
              <td><button className="btn-link" onClick={() => setOpen(open === r.id ? null : r.id)}>{open === r.id ? 'Hide' : 'Diff'}</button></td></tr>
            {open === r.id && (
              <tr><td colSpan={6}>
                <div className="grid gap-3 sm:grid-cols-2 text-xs">
                  <pre className="overflow-x-auto rounded bg-sand-100 p-2">{r.beforeState || '—'}</pre>
                  <pre className="overflow-x-auto rounded bg-sand-100 p-2">{r.afterState || '—'}</pre>
                </div>
              </td></tr>
            )}
          </Fragment>
        ))}</tbody>
      </table>
      <div className="mt-4 flex gap-2">
        <button className="btn-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
        <button className="btn-secondary" disabled={(data?.length || 0) < 50} onClick={() => setPage(page + 1)}>Older</button>
      </div>
    </div>
  );
}
