#!/usr/bin/env python3
import hashlib
import hmac
import json
import os
import threading
import time
import uuid
from collections import defaultdict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


HOST = "0.0.0.0"
PORT = 8787
SHARED_SECRET = os.environ.get("AUFAIT_RELAY_SHARED_SECRET", "").strip()
MAX_PUSH_BODY_BYTES = 64 * 1024
MAX_PULL_BATCH = 100
MAX_QUEUE_PER_DEST = 500
MAX_TOTAL_QUEUES_EVENTS = 10_000
MAX_CLOCK_SKEW_MS = 5 * 60 * 1000
NONCE_TTL_MS = 10 * 60 * 1000
RATE_LIMIT_WINDOW_MS = 60 * 1000
RATE_LIMIT_MAX_REQUESTS = 240

queues = defaultdict(deque)  # key: destination nodeId (toRef), value: events
seen_nonces = {}  # key: client_ip:nonce -> first seen timestamp ms
rate_windows = defaultdict(deque)  # key: client ip -> deque[timestamp_ms]
state_lock = threading.Lock()


class RelayHandler(BaseHTTPRequestHandler):
    server_version = "AufaitMiniRelay/0.1"

    def _json(self, status, payload):
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _client_ip(self):
        return self.client_address[0] if self.client_address else "unknown"

    def _cleanup_nonce_cache(self, now_ms):
        expired_before = now_ms - NONCE_TTL_MS
        with state_lock:
            stale_keys = [k for k, ts in seen_nonces.items() if ts < expired_before]
            for k in stale_keys:
                seen_nonces.pop(k, None)

    def _check_rate_limit(self):
        now_ms = int(time.time() * 1000)
        with state_lock:
            dq = rate_windows[self._client_ip()]
            while dq and (now_ms - dq[0]) > RATE_LIMIT_WINDOW_MS:
                dq.popleft()
            if len(dq) >= RATE_LIMIT_MAX_REQUESTS:
                return False
            dq.append(now_ms)
            return True

    def _verify_request_signature(self, body_bytes=b""):
        if not SHARED_SECRET:
            return True, None
        ts_raw = (self.headers.get("X-AF-TS") or "").strip()
        nonce = (self.headers.get("X-AF-NONCE") or "").strip()
        sig = (self.headers.get("X-AF-SIG") or "").strip().lower()
        alg = (self.headers.get("X-AF-ALG") or "").strip().upper()
        if alg and alg != "HMAC-SHA256":
            return False, "bad_alg"
        if not ts_raw or not nonce or not sig:
            return False, "missing_auth"
        if len(nonce) > 128 or len(sig) != 64:
            return False, "bad_auth_format"
        try:
            ts_ms = int(ts_raw)
        except ValueError:
            return False, "bad_ts"
        now_ms = int(time.time() * 1000)
        if abs(now_ms - ts_ms) > MAX_CLOCK_SKEW_MS:
            return False, "stale_ts"
        self._cleanup_nonce_cache(now_ms)
        nonce_key = f"{self._client_ip()}:{nonce}"
        path_q = self.path or "/"
        body_hash = hashlib.sha256(body_bytes or b"").hexdigest()
        canonical = "\n".join([
            self.command.upper(),
            path_q,
            str(ts_ms),
            nonce,
            body_hash,
        ]).encode("utf-8")
        expected = hmac.new(SHARED_SECRET.encode("utf-8"), canonical, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, expected):
            return False, "bad_sig"
        with state_lock:
            if nonce_key in seen_nonces:
                return False, "replay_nonce"
            seen_nonces[nonce_key] = now_ms
        return True, None

    def _require_rate_limit(self):
        if self._check_rate_limit():
            return True
        self._json(429, {"error": "rate_limited"})
        return False

    def _require_auth(self, body_bytes=b""):
        ok, error = self._verify_request_signature(body_bytes)
        if ok:
            return True
        self._json(401, {"error": error or "auth_failed"})
        return False

    def do_GET(self):
        if not self._require_rate_limit():
            return
        parsed = urlparse(self.path)
        if parsed.path == "/healthz":
            return self._json(200, {"ok": True, "ts": int(time.time() * 1000)})
        if parsed.path != "/v1/pull":
            return self._json(404, {"error": "not_found"})
        if not self._require_auth():
            return

        node_id = parse_qs(parsed.query).get("nodeId", [""])[0].strip()
        if not node_id or len(node_id) > 128:
            return self._json(400, {"error": "missing_nodeId"})

        events = []
        with state_lock:
            bucket = queues[node_id]
            while bucket and len(events) < MAX_PULL_BATCH:
                events.append(bucket.popleft())
        return self._json(200, {"events": events})

    def do_POST(self):
        if not self._require_rate_limit():
            return
        if self.path != "/v1/push":
            return self._json(404, {"error": "not_found"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > MAX_PUSH_BODY_BYTES:
                return self._json(413, {"error": "body_too_large"})
            body_bytes = self.rfile.read(length)
            if not self._require_auth(body_bytes):
                return
            body = body_bytes.decode("utf-8")
            payload = json.loads(body)
        except Exception:
            return self._json(400, {"error": "bad_json"})

        to_ref = str(payload.get("toRef", "")).strip()
        msg_type = str(payload.get("type", "")).strip()
        message_id = str(payload.get("messageId", "")).strip()
        from_node_id = str(payload.get("fromNodeId", "")).strip()
        from_alias = str(payload.get("fromAlias", "")).strip()
        if (
            not to_ref or len(to_ref) > 128 or
            msg_type not in {"msg", "receipt"} or
            not message_id or len(message_id) > 128 or
            len(from_node_id) > 128 or
            len(from_alias) > 64
        ):
            return self._json(400, {"error": "invalid_event"})

        event = {
            "eventId": str(uuid.uuid4()),
            "type": msg_type,
            "messageId": message_id,
            "fromNodeId": from_node_id,
            "fromAlias": from_alias,
            "timestampMs": int(time.time() * 1000),
        }
        if msg_type == "msg":
            body_text = str(payload.get("body", ""))
            if len(body_text) > 16000:
                return self._json(400, {"error": "msg_too_large"})
            event["body"] = body_text
        else:
            receipt_kind = str(payload.get("receiptKind", "")).strip().lower()
            if receipt_kind not in {"delivered", "read"}:
                return self._json(400, {"error": "invalid_receipt"})
            event["receiptKind"] = receipt_kind

        with state_lock:
            self._trim_total_queues_if_needed_locked()
            queues[to_ref].append(event)
            while len(queues[to_ref]) > MAX_QUEUE_PER_DEST:
                queues[to_ref].popleft()
        return self._json(200, {"ok": True, "queuedFor": to_ref, "eventId": event["eventId"]})

    def _trim_total_queues_if_needed_locked(self):
        total = sum(len(q) for q in queues.values())
        if total < MAX_TOTAL_QUEUES_EVENTS:
            return
        overflow = total - MAX_TOTAL_QUEUES_EVENTS + 1
        for q in queues.values():
            while q and overflow > 0:
                q.popleft()
                overflow -= 1
            if overflow <= 0:
                return

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
