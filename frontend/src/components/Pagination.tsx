import Link from 'next/link';

export function Pagination({ page, totalPages, baseParams, path }: {
  page: number; totalPages: number; baseParams: Record<string, string>; path: string;
}) {
  if (totalPages <= 1) return null;
  const href = (p: number) => `${path}?${new URLSearchParams({ ...baseParams, page: String(p) }).toString()}`;
  return (
    <nav className="mt-10 flex items-center justify-center gap-3 text-sm">
      {page > 0 && <Link className="btn-secondary" href={href(page - 1)}>Previous</Link>}
      <span className="text-sand-600">Page {page + 1} of {totalPages}</span>
      {page + 1 < totalPages && <Link className="btn-secondary" href={href(page + 1)}>Next</Link>}
    </nav>
  );
}
