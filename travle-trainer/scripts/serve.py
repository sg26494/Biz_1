#!/usr/bin/env python3
"""Serve the app locally with caching switched off, so the browser always gets the latest files.

Run from anywhere:  python3 scripts/serve.py          (port 8000, opens the browser)
                    python3 scripts/serve.py 9000     (another port)
                    python3 scripts/serve.py --no-open
"""
import functools
import http.server
import os
import socketserver
import sys
import webbrowser

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ARGS = [a for a in sys.argv[1:] if not a.startswith("--")]
PORT = int(ARGS[0]) if ARGS else 8000


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def send_head(self):
        # Drop the conditional headers so the browser can never get a "304 Not Modified" for an old copy.
        for h in ("If-Modified-Since", "If-None-Match"):
            if h in self.headers:
                del self.headers[h]
        return super().send_head()

    def log_message(self, fmt, *args):
        pass  # keep the terminal quiet


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True


handler = functools.partial(NoCacheHandler, directory=ROOT)
try:
    httpd = Server(("127.0.0.1", PORT), handler)
except OSError:
    sys.exit(f"Port {PORT} is already in use, probably by an older server still running.\n"
             f"Stop it (Ctrl+C in its terminal) or pick another port: python3 scripts/serve.py 8001")

url = f"http://localhost:{PORT}/"
print(f"Serving {ROOT}\nOpen {url}  (Ctrl+C to stop)")
if "--no-open" not in sys.argv:
    webbrowser.open(url)
try:
    httpd.serve_forever()
except KeyboardInterrupt:
    print("\nStopped.")
