#!/usr/bin/env python3
import argparse
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Stats:
    def __init__(self):
        self._lock = threading.Lock()
        self.requests = 0
        self.bytes = 0

    def add(self, size):
        with self._lock:
            self.requests += 1
            self.bytes += size

    def reset(self):
        with self._lock:
            self.requests = 0
            self.bytes = 0

    def snapshot(self):
        with self._lock:
            return {"requests": self.requests, "bytes": self.bytes}


STATS = Stats()


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == "/reset":
            STATS.reset()
        else:
            length = int(self.headers.get("Content-Length", "0"))
            if length:
                self.rfile.read(length)
            STATS.add(length)
        self._json_response({"ok": True})

    def do_GET(self):
        if self.path == "/stats":
            self._json_response(STATS.snapshot())
        else:
            self._json_response({"ok": True})

    def _json_response(self, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, _format, *_args):
        return


def main():
    parser = argparse.ArgumentParser(description="DeepCover benchmark HTTP receiver")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18081)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print("receiver listening on http://%s:%s" % (args.host, args.port), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
