#!/usr/bin/env python3
import json
import time
import uuid
from collections import defaultdict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


HOST = "0.0.0.0"
PORT = 8787

queues = defaultdict(deque)  # key: destination nodeId (toRef), value: events


class RelayHandler(BaseHTTPRequestHandler):
    server_version = "AufaitMiniRelay/0.1"

    def _json(self, status, payload):
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/healthz":
            return self._json(200, {"ok": True, "ts": int(time.time() * 1000)})
        if parsed.path != "/v1/pull":
            return self._json(404, {"error": "not_found"})

        node_id = parse_qs(parsed.query).get("nodeId", [""])[0].strip()
        if not node_id:
            return self._json(400, {"error": "missing_nodeId"})

        bucket = queues[node_id]
        events = []
        while bucket and len(events) < 100:
            events.append(bucket.popleft())
        return self._json(200, {"events": events})

    def do_POST(self):
        if self.path != "/v1/push":
            return self._json(404, {"error": "not_found"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length).decode("utf-8")
            payload = json.loads(body)
        except Exception:
            return self._json(400, {"error": "bad_json"})

        to_ref = str(payload.get("toRef", "")).strip()
        msg_type = str(payload.get("type", "")).strip()
        message_id = str(payload.get("messageId", "")).strip()
        if not to_ref or msg_type not in {"msg", "receipt"} or not message_id:
            return self._json(400, {"error": "invalid_event"})

        event = {
            "eventId": str(uuid.uuid4()),
            "type": msg_type,
            "messageId": message_id,
            "fromNodeId": str(payload.get("fromNodeId", "")),
            "fromAlias": str(payload.get("fromAlias", "")),
            "timestampMs": int(time.time() * 1000),
        }
        if msg_type == "msg":
            event["body"] = str(payload.get("body", ""))
        else:
            event["receiptKind"] = str(payload.get("receiptKind", ""))

        queues[to_ref].append(event)
        return self._json(200, {"ok": True, "queuedFor": to_ref, "eventId": event["eventId"]})

    def log_message(self, fmt, *args):
        # concise logs for manual testing
        print(f"[relay] {self.address_string()} - {fmt % args}")


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), RelayHandler)
    print(f"Aufait mini relay listening on http://{HOST}:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
