import { revalidatePath } from 'next/cache';
import { NextRequest, NextResponse } from 'next/server';

/** Called by the API's outbox worker after admin edits, so ISR pages refresh immediately. */
export async function POST(req: NextRequest) {
  if (req.headers.get('x-revalidate-secret') !== process.env.REVALIDATE_SECRET) {
    return NextResponse.json({ code: 'FORBIDDEN', message: 'Bad secret' }, { status: 403 });
  }
  const { paths } = (await req.json()) as { paths?: string[] };
  for (const p of paths || []) {
    if (typeof p === 'string' && p.startsWith('/')) revalidatePath(p);
  }
  return NextResponse.json({ revalidated: paths?.length || 0 });
}
