#!/usr/bin/env python3
"""Local demo relay, Python 3.9+ standard library, memory only.
Production requires HTTPS and real authentication; do not expose this HTTP demo to the Internet.
"""
import argparse
import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

TTL = 24 * 60 * 60
MAX_BODY = 32768
MAX_MESSAGES_BYTES = 96 * 1024  # HttpFamilyLink reads at most 128 KiB per response.
MAX_SESSIONS = 512


def json_bytes(value):
    return json.dumps(value, ensure_ascii=False, allow_nan=False).encode('utf-8')


class Store:
    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.sessions = {}
        self.revoked = {}
        self.lock = threading.RLock()

    @staticmethod
    def _token(token):
        if not isinstance(token, str) or not re.fullmatch('[0-9a-f]{32}', token):
            raise ValueError('invalid token')

    def purge(self):
        with self.lock:
            now = self.clock()
            for token, session in list(self.sessions.items()):
                if now >= session['expires']:
                    del self.sessions[token]
                    # Only token + expiry remain, never health text. No sliding TTL.
                    self.revoked[token] = session['expires'] + TTL
            for token, expires in list(self.revoked.items()):
                if now >= expires:
                    del self.revoked[token]

    def _get(self, token):
        self._token(token)
        self.purge()
        return self.sessions[token]

    def message(self, token, body):
        with self.lock:
            self._token(token)
            self.purge()
            if token in self.revoked:
                raise PermissionError('expired or revoked')
            if not isinstance(body, dict) or set(body) != {'id', 'kind', 'text'}:
                raise ValueError('invalid fields')
            if not isinstance(body['id'], str) or not 1 <= len(body['id']) <= 64:
                raise ValueError('invalid id')
            if body['kind'] not in ('memo', 'briefing', 'status'):
                raise ValueError('invalid kind')
            if not isinstance(body['text'], str) or not 1 <= len(body['text']) <= 16000:
                raise ValueError('invalid text')
            if body['kind'] == 'status' and body['text'] not in ('정문 도착', '주차 중', '가족 연결됨'):
                raise ValueError('invalid status')
            if len(json_bytes(body)) > MAX_BODY:
                raise ValueError('message too large')
            if token not in self.sessions:
                if len(self.sessions) + len(self.revoked) >= MAX_SESSIONS:
                    raise ValueError('capacity')
                self.sessions[token] = {'expires': self.clock() + TTL, 'messages': [], 'replies': [], 'sequence': 0}
            session = self.sessions[token]
            if not any(m['id'] == body['id'] for m in session['messages']):
                session['messages'].append(dict(body))
                while len(session['messages']) > 30 or len(json_bytes(session['messages'])) > MAX_MESSAGES_BYTES:
                    session['messages'].pop(0)

    def session(self, token):
        with self.lock:
            session = self._get(token)
            return {'messages': [dict(m) for m in session['messages']],
                    'remainingSeconds': max(0, int(session['expires'] - self.clock()))}

    def reply(self, token, body):
        with self.lock:
            session = self._get(token)
            if not isinstance(body, dict) or set(body) != {'text'} or body['text'] not in ('나왔어', '잘 받았어'):
                raise ValueError('invalid reply')
            session['sequence'] += 1
            session['replies'].append({'id': str(session['sequence']), 'text': body['text']})
            session['replies'] = session['replies'][-100:]

    def replies(self, token, after):
        with self.lock:
            return [dict(r) for r in self._get(token)['replies'] if int(r['id']) > after]

    def delete(self, token):
        with self.lock:
            self._token(token)
            self.purge()
            if self.sessions.pop(token, None) is not None:
                self.revoked[token] = self.clock() + TTL
            # Unknown deletes are idempotent and cannot allocate unlimited tombstones.


class Handler(BaseHTTPRequestHandler):
    server_version = 'FamilyRelay/1'

    def log_message(self, *_):
        pass  # Never log URLs, bearer tokens or health text.

    def respond(self, status, data, html=False):
        payload = data.encode('utf-8') if html else json_bytes(data)
        self.send_response(status)
        self.send_header('Content-Type', 'text/html; charset=utf-8' if html else 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(payload)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Security-Policy', "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
        self.end_headers()
        self.wfile.write(payload)

    def route(self):
        try:
            path = urlsplit(self.path)
            if self.command == 'GET' and path.path == '/':
                self.respond(200, Path(__file__).with_name('index.html').read_text(encoding='utf-8'), True)
                return
            # Browser and API share one origin; Android sends no Origin. No wildcard CORS.
            origin = self.headers.get('Origin')
            if origin and origin != 'http://' + self.headers.get('Host', ''):
                self.respond(403, {'error': 'origin'})
                return
            auth = self.headers.get('Authorization', '')
            token = auth[7:] if auth.startswith('Bearer ') else ''
            if not re.fullmatch('[0-9a-f]{32}', token):
                self.respond(401, {'error': 'pairing required'})
                return
            body = None
            if self.command == 'POST':
                if self.headers.get_content_type() != 'application/json' or self.headers.get('Transfer-Encoding'):
                    raise ValueError('content type or transfer encoding')
                lengths = self.headers.get_all('Content-Length', [])
                if len(lengths) != 1 or not re.fullmatch('[0-9]+', lengths[0]):
                    raise ValueError('length')
                length = int(lengths[0])
                if length > MAX_BODY:
                    self.respond(413, {'error': 'body too large'})
                    return
                if length == 0:
                    raise ValueError('length')
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise ValueError('incomplete body')
                body = json.loads(raw.decode('utf-8'))
            store = self.server.store
            if self.command == 'POST' and path.path == '/api/message':
                store.message(token, body)
                result = {'ok': True}
            elif self.command == 'GET' and path.path == '/api/session':
                result = store.session(token)
            elif self.command == 'POST' and path.path == '/api/reply':
                store.reply(token, body)
                result = {'ok': True}
            elif self.command == 'GET' and path.path == '/api/replies':
                query = parse_qs(path.query, keep_blank_values=True, strict_parsing=True, max_num_fields=1) if path.query else {}
                after = query.get('after', ['0'])[0]
                if set(query) - {'after'} or not re.fullmatch('[0-9]{1,19}', after):
                    raise ValueError('cursor')
                result = {'replies': store.replies(token, int(after))}
            elif self.command == 'DELETE' and path.path == '/api/session':
                store.delete(token)
                result = {'ok': True}
            else:
                self.respond(404, {'error': 'not found'})
                return
            self.respond(200, result)
        except KeyError:
            self.respond(404, {'error': 'session expired or not paired'})
        except PermissionError:
            self.respond(410, {'error': 'session revoked or expired'})
        except (ValueError, TypeError, UnicodeError, RecursionError):
            self.respond(400, {'error': 'invalid request'})
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            pass

    do_GET = route
    do_POST = route
    do_DELETE = route

    def setup(self):
        super().setup()
        self.connection.settimeout(5)


class RelayServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 32

    def __init__(self, address):
        super().__init__(address, Handler)
        self.store = Store()
        self.workers = threading.BoundedSemaphore(32)
        self.last_purge = time.monotonic()

    def process_request(self, request, client_address):
        if not self.workers.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except BaseException:
            self.workers.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.workers.release()

    def service_actions(self):
        # Purge even when nobody polls; access checks expire immediately at the boundary.
        if time.monotonic() - self.last_purge >= 30:
            self.store.purge()
            self.last_purge = time.monotonic()


def make_server(host='127.0.0.1', port=8765):
    return RelayServer((host, port))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='127.0.0.1', help='bind address; use LAN IP or 0.0.0.0 for a phone')
    parser.add_argument('--port', type=int, default=8765)
    args = parser.parse_args()
    server = make_server(args.host, args.port)
    print('Family relay on %s:%s; memory only, 24h TTL. Production needs HTTPS + authentication.' % server.server_address, flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        with server.store.lock:
            server.store.sessions.clear()
            server.store.revoked.clear()


if __name__ == '__main__':
    main()
