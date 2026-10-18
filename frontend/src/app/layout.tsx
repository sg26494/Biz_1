import type { Metadata, Viewport } from 'next';
import './globals.css';
import { Providers } from './providers';
import { SITE_NAME, SITE_URL } from '@/lib/format';

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: { default: `${SITE_NAME} | Jaipuri razai and bed sheets`, template: `%s | ${SITE_NAME}` },
  description: 'Hand-block printed Jaipuri razais and cotton bed sheets from Rajasthan, delivered across India.',
  openGraph: { siteName: SITE_NAME, type: 'website', locale: 'en_IN' },
};

export const viewport: Viewport = { width: 'device-width', initialScale: 1, themeColor: '#9a5230' };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-IN">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
