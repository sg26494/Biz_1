'use client';

import { useEffect, useRef } from 'react';

type GoogleId = {
  accounts: { id: {
    initialize: (o: { client_id: string; callback: (r: { credential: string }) => void }) => void;
    renderButton: (el: HTMLElement, o: Record<string, unknown>) => void;
  } };
};

/** Google Identity Services button. Hands the ID token to onCredential; the API verifies it. */
export function GoogleButton({ onCredential }: { onCredential: (idToken: string) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

  useEffect(() => {
    if (!clientId) return;
    const render = () => {
      const g = (window as unknown as { google?: GoogleId }).google;
      if (!g || !ref.current) return;
      g.accounts.id.initialize({ client_id: clientId, callback: (r) => onCredential(r.credential) });
      g.accounts.id.renderButton(ref.current, { theme: 'outline', size: 'large', width: 320, text: 'continue_with' });
    };
    if ((window as unknown as { google?: GoogleId }).google) return render();
    const s = document.createElement('script');
    s.src = 'https://accounts.google.com/gsi/client';
    s.async = true;
    s.onload = render;
    document.head.appendChild(s);
  }, [clientId, onCredential]);

  if (!clientId) return <p className="text-xs text-sand-400">Google sign-in is not configured.</p>;
  return <div ref={ref} className="min-h-[44px]" />;
}
