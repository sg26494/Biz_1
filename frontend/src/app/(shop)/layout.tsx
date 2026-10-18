import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { serverGet } from '@/lib/server-api';
import type { Category } from '@/lib/types';

export default async function ShopLayout({ children }: { children: React.ReactNode }) {
  const categories = (await serverGet<Category[]>('/categories', 600)) || [];
  return (
    <>
      <Header categories={categories.filter((c) => !c.parentId).map((c) => ({ name: c.name, slug: c.slug }))} />
      <main className="min-h-[60vh]">{children}</main>
      <Footer />
    </>
  );
}
