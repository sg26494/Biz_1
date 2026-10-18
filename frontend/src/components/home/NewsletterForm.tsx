'use client';

import { useState } from 'react';
import { api, errorMessage } from '@/lib/api';

export function NewsletterForm() {
  const [email, setEmail] = useState('');
  const [msg, setMsg] = useState('');
  return (
    <>
      <form
        className="gm-news-form"
        onSubmit={async (e) => {
          e.preventDefault();
          try {
            await api('/newsletter', { method: 'POST', body: { email } });
            setMsg('Dhanyavaad! You are now part of our circle.');
            setEmail('');
          } catch (err) {
            setMsg(errorMessage(err));
          }
        }}
      >
        <input type="email" placeholder="Your email address" required aria-label="Email address" value={email}
          onChange={(e) => setEmail(e.target.value)} />
        <button type="submit" className="gm-btn">Subscribe</button>
      </form>
      <div className="gm-news-msg">{msg}</div>
    </>
  );
}
