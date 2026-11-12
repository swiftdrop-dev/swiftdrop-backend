"""Local mock merchant receiver; run from any directory with Python 3."""
import hashlib
import hmac
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


def load_secret():
    env_file = Path(__file__).resolve().parents[1] / '.env'
    for line in env_file.read_text(encoding='utf-8-sig').splitlines():
        key, separator, value = line.partition('=')
        if separator and key.strip() == 'WEBHOOK_LOCAL_SIGNING_SECRET':
            secret = value.strip().strip('\"\'')
            if len(secret) >= 32:
                return secret.encode('utf-8')
    raise SystemExit('Set WEBHOOK_LOCAL_SIGNING_SECRET in the backend .env first (at least 32 characters).')


class Receiver(BaseHTTPRequestHandler):
    secret = b''

    def reply(self, status, message):
        data = json.dumps({'message': message}).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.reply(200 if self.path == '/health' else 404,
                   'Mock receiver ready' if self.path == '/health' else 'Not found')

    def do_POST(self):
        self.connection.settimeout(5)
        if self.path != '/webhooks/swiftdrop':
            return self.reply(404, 'Not found')
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 16384:
                return self.reply(413, 'Invalid payload size')
            body = self.rfile.read(length)
            expected = 'sha256=' + hmac.new(self.secret, body, hashlib.sha256).hexdigest()
            if not hmac.compare_digest(expected, self.headers.get('X-SwiftDrop-Signature', '')):
                return self.reply(401, 'Invalid signature')
            event = json.loads(body)
            if not isinstance(event, dict) or event.get('test') is not True:
                return self.reply(400, 'Only test events are accepted')
        except (ValueError, TimeoutError):
            return self.reply(400, 'Invalid request')
        print('Verified signed test event received.', flush=True)
        self.reply(200, 'Signature verified; test event accepted')

    def log_message(self, format, *args):
        pass  # Never log request headers, secrets, or arbitrary payloads.


if __name__ == '__main__':
    Receiver.secret = load_secret()
    server = ThreadingHTTPServer(('127.0.0.1', 9091), Receiver)
    print('Mock receiver: http://127.0.0.1:9091/webhooks/swiftdrop', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
