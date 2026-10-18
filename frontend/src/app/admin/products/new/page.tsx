'use client';

import { useRouter } from 'next/navigation';
import { ProductForm } from '@/components/admin/ProductForm';
import type { Category } from '@/lib/types';
import { useApi } from '@/lib/useApi';

export default function NewProduct() {
  const router = useRouter();
  const { data: categories } = useApi<Category[]>('/admin/categories');
  if (!categories) return <p className="text-sand-600">Loading…</p>;
  return (
    <div className="max-w-3xl">
      <h1 className="mb-4 text-2xl">New product</h1>
      <p className="mb-4 text-sm text-sand-600">Products start as drafts. Add variants and photos, then publish.</p>
      <ProductForm categories={categories} onSaved={(p) => router.replace(`/admin/products/${p.id}`)} />
    </div>
  );
}
