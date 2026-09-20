#!/usr/bin/env python3
import argparse
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Stats:
    def __init__(self, capture_records=False, max_records=10000):
        self._lock = threading.Lock()
        self.requests = 0
        self.bytes = 0
        self.capture_records = capture_records
        self.max_records = max_records
        self.records = []

    def add(self, size, payload=None):
        with self._lock:
            self.requests += 1
            self.bytes += size
            if self.capture_records and payload is not None and len(self.records) < self.max_records:
                self.records.append(payload)

    def reset(self):
        with self._lock:
            self.requests = 0
            self.bytes = 0
            self.records = []

    def snapshot(self):
        with self._lock:
            return {"requests": self.requests, "bytes": self.bytes}

    def records_snapshot(self):
        with self._lock:
            return list(self.records)


STATS = Stats()


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == "/reset":
            STATS.reset()
        else:
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length) if length else b""
            payload = None
            if STATS.capture_records and body:
                try:
                    payload = json.loads(body.decode("utf-8"))
                except (UnicodeDecodeError, json.JSONDecodeError):
                    self._json_response({"ok": False, "error": "invalid-json"}, status=400)
                    return
            STATS.add(length, payload)
        self._json_response({"ok": True})

    def do_GET(self):
        if self.path == "/stats":
            self._json_response(STATS.snapshot())
        elif self.path == "/records":
            self._json_response({"records": STATS.records_snapshot()})
        else:
            self._json_response({"ok": True})

    def _json_response(self, payload, status=200):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, _format, *_args):
        return


def main():
    global STATS
    parser = argparse.ArgumentParser(description="DeepCover benchmark HTTP receiver")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18081)
    parser.add_argument("--capture-records", action="store_true")
    parser.add_argument("--max-records", type=int, default=10000)
    args = parser.parse_args()
    STATS = Stats(capture_records=args.capture_records, max_records=args.max_records)
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print("receiver listening on http://%s:%s" % (args.host, args.port), flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
