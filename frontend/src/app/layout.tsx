import type { Metadata, Viewport } from 'next';
import './globals.css';
import './gulmohar.css';
import { Providers } from './providers';
import { SITE_NAME, SITE_URL } from '@/lib/format';

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: { default: `${SITE_NAME} | Premium Jaipur Rajai, Dohars & Bed Sheets`, template: `%s | ${SITE_NAME}` },
  description: 'Hand block printed Jaipuri rajai, dohars and bed sheets, made by artisans in Sanganer and Bagru, Rajasthan.',
  icons: { icon: '/images/logo-mark.png' },
  openGraph: { siteName: SITE_NAME, type: 'website', locale: 'en_IN' },
};

export const viewport: Viewport = { width: 'device-width', initialScale: 1, themeColor: '#CD7362' };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-IN">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        {/* eslint-disable-next-line @next/next/no-page-custom-font */}
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Montserrat:wght@300;400;500;600&family=Playfair+Display:ital,wght@0,400;0,600;1,400&family=Tiro+Devanagari+Hindi&display=swap"
        />
      </head>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
