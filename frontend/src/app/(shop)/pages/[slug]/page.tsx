import type { Metadata } from 'next';
import { notFound } from 'next/navigation';
import sanitizeHtml from 'sanitize-html';
import { serverGet } from '@/lib/server-api';

type CmsPage = { slug: string; title: string; bodyHtml: string; updatedAt: string };
type Props = { params: Promise<{ slug: string }> };

export const revalidate = 3600;

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const p = await serverGet<CmsPage>(`/pages/${slug}`);
  return p ? { title: p.title } : {};
}

export default async function CmsPageView({ params }: Props) {
  const { slug } = await params;
  const p = await serverGet<CmsPage>(`/pages/${slug}`);
  if (!p) notFound();
  const html = sanitizeHtml(p.bodyHtml, {
    allowedTags: sanitizeHtml.defaults.allowedTags.concat(['h1', 'h2', 'img']),
    allowedAttributes: { ...sanitizeHtml.defaults.allowedAttributes, img: ['src', 'alt'] },
  });
  return (
    <article className="container-page max-w-3xl py-10">
      <h1 className="text-3xl">{p.title}</h1>
      <div className="prose-cms mt-6" dangerouslySetInnerHTML={{ __html: html }} />
    </article>
  );
}
