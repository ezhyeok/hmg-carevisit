"""Real HTTP, virtual TTL clock and concurrent-client regression tests."""
import concurrent.futures
import http.client
import json
import threading
import unittest
from relay import Store, make_server, TTL, MAX_BODY

TOKEN = 'ab' * 16
OTHER = 'cd' * 16
MESSAGE = {'id': 'm', 'kind': 'memo', 'text': '물어볼 것: 산책'}


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.now = 0
        self.store = Store(lambda: self.now)

    def test_only_approved_shape(self):
        for body in [dict(MESSAGE, location='here'), dict(MESSAGE, kind='transcript'),
                     dict(MESSAGE, text=[]), dict(MESSAGE, kind=[]), dict(MESSAGE, id=''),
                     dict(MESSAGE, id='x'*65), dict(MESSAGE, text='x'*16001), [], None]:
            with self.assertRaises(ValueError): self.store.message(TOKEN, body)
        self.assertEqual({}, self.store.sessions)

    def test_status_allowlist(self):
        for text in ['정문 도착', '주차 중', '가족 연결됨']:
            self.store.message(TOKEN, dict(MESSAGE, id=text, kind='status', text=text))
        with self.assertRaises(ValueError):
            self.store.message(TOKEN, dict(MESSAGE, kind='status', text='위치'))

    def test_absolute_expiry(self):
        self.store.message(TOKEN, MESSAGE)
        self.now = TTL-1
        self.store.message(TOKEN, dict(MESSAGE, id='new'))
        self.store.reply(TOKEN, {'text': '나왔어'})
        self.assertEqual(1, self.store.session(TOKEN)['remainingSeconds'])
        self.now = TTL
        with self.assertRaises(KeyError): self.store.session(TOKEN)
        with self.assertRaises(PermissionError): self.store.message(TOKEN, MESSAGE)
        self.assertEqual({}, self.store.sessions)

    def test_bad_tokens(self):
        for token in ['', '1234', 'a'*31, 'x'*32, 'AB'*16, None]:
            with self.assertRaises(ValueError): self.store.session(token)

    def test_reply_allowlist_and_cursor(self):
        self.store.message(TOKEN, MESSAGE)
        for body in [{'text': '위치'}, {'text': '나왔어', 'location': 1}, [], {'text': []}]:
            with self.assertRaises(ValueError): self.store.reply(TOKEN, body)
        self.store.reply(TOKEN, {'text': '나왔어'})
        self.store.reply(TOKEN, {'text': '잘 받았어'})
        self.assertEqual([{'id': '2', 'text': '잘 받았어'}], self.store.replies(TOKEN, 1))
        self.assertEqual([], self.store.replies(TOKEN, 2))

    def test_idempotency_and_revoke(self):
        self.store.message(TOKEN, MESSAGE)
        self.store.message(TOKEN, MESSAGE)
        self.assertEqual(1, len(self.store.session(TOKEN)['messages']))
        self.store.delete(TOKEN)
        self.store.delete(TOKEN)
        with self.assertRaises(KeyError): self.store.session(TOKEN)
        with self.assertRaises(PermissionError): self.store.message(TOKEN, MESSAGE)

    def test_unknown_delete_does_not_allocate(self):
        for i in range(600): self.store.delete('%032x' % i)
        self.assertEqual({}, self.store.revoked)

    def test_isolation_and_defensive_copy(self):
        self.store.message(TOKEN, MESSAGE)
        with self.assertRaises(KeyError): self.store.session(OTHER)
        snapshot = self.store.session(TOKEN)
        snapshot['messages'][0]['text'] = 'changed'
        self.assertEqual(MESSAGE, self.store.session(TOKEN)['messages'][0])

    def test_bounded_queues(self):
        for i in range(40): self.store.message(TOKEN, dict(MESSAGE, id=str(i), text='가'*8000))
        self.assertLess(len(json.dumps(self.store.session(TOKEN), ensure_ascii=False).encode()), 128*1024)
        self.assertEqual('39', self.store.session(TOKEN)['messages'][-1]['id'])
        for i in range(120): self.store.reply(TOKEN, {'text': '나왔어'})
        replies = self.store.replies(TOKEN, 0)
        self.assertEqual(100, len(replies))
        self.assertEqual('120', replies[-1]['id'])

    def test_capacity_and_tombstone_expiry(self):
        for i in range(512): self.store.message('%032x' % i, MESSAGE)
        self.store.delete('%032x' % 0)
        with self.assertRaises(ValueError): self.store.message(TOKEN, MESSAGE)
        self.now = TTL*2
        self.store.purge()
        self.assertEqual({}, self.store.sessions)
        self.assertEqual({}, self.store.revoked)


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.now = 0
        self.server = make_server('127.0.0.1', 0)
        self.server.store = Store(lambda: self.now)
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={'poll_interval': .02}, daemon=True)
        self.thread.start()
        self.host = '127.0.0.1:' + str(self.server.server_port)

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join()

    def request(self, path, method='GET', body=None, token=TOKEN, headers=None, raw=None):
        h = {'Authorization': 'Bearer '+token, 'Content-Type': 'application/json'}
        h.update(headers or {})
        payload = raw if raw is not None else json.dumps(body, ensure_ascii=False).encode() if body is not None else None
        c = http.client.HTTPConnection(self.host, timeout=5)
        try:
            c.request(method, path, body=payload, headers=h)
            r = c.getresponse()
            return r.status, dict(r.getheaders()), r.read()
        finally: c.close()

    def seed(self): self.assertEqual(200, self.request('/api/message', 'POST', MESSAGE)[0])

    def test_page_without_auth(self):
        status, headers, payload = self.request('/', token='')
        self.assertEqual(200, status)
        page = payload.decode()
        for text in ['textContent', '연결 코드', '나왔어', '잘 받았어']: self.assertIn(text, page)
        self.assertNotIn('innerHTML', page)
        self.assertNotIn('https://', page)
        self.assertEqual('no-referrer', headers['Referrer-Policy'])

    def test_post_message_and_session(self):
        self.seed()
        status, headers, payload = self.request('/api/session')
        self.assertEqual(200, status)
        self.assertEqual([MESSAGE], json.loads(payload)['messages'])
        self.assertEqual('no-store', headers['Cache-Control'])
        self.assertEqual('nosniff', headers['X-Content-Type-Options'])
        self.assertNotIn('Access-Control-Allow-Origin', headers)

    def test_reply_and_poll(self):
        self.seed()
        for text in ['나왔어', '잘 받았어']:
            self.assertEqual(200, self.request('/api/reply', 'POST', {'text': text})[0])
        self.assertEqual([{'id': '2', 'text': '잘 받았어'}], json.loads(self.request('/api/replies?after=1')[2])['replies'])

    def test_delete(self):
        self.seed()
        self.assertEqual(200, self.request('/api/session', 'DELETE')[0])
        self.assertEqual(404, self.request('/api/session')[0])
        self.assertEqual(404, self.request('/api/replies')[0])
        self.assertEqual(410, self.request('/api/message', 'POST', MESSAGE)[0])
        self.assertEqual(200, self.request('/api/session', 'DELETE')[0])

    def test_expiry_all_endpoints(self):
        self.seed(); self.now = TTL
        self.assertEqual(404, self.request('/api/session')[0])
        self.assertEqual(404, self.request('/api/replies')[0])
        self.assertEqual(404, self.request('/api/reply', 'POST', {'text': '나왔어'})[0])
        self.assertEqual(410, self.request('/api/message', 'POST', MESSAGE)[0])

    def test_background_purge_without_client_poll(self):
        self.seed()
        self.now = TTL
        self.server.last_purge = 0
        self.server.service_actions()
        self.assertEqual({}, self.server.store.sessions)

    def test_escaped_unicode_and_deep_json_are_rejected(self):
        self.assertEqual(400, self.request('/api/message', 'POST', raw=b'{"id":"x","kind":"memo","text":"\\ud800"}')[0])
        self.assertEqual(400, self.request('/api/message', 'POST', raw=b'['*1500+b']'*1500)[0])

    def test_token_mismatch(self):
        self.seed()
        for path, method, body in [('/api/session', 'GET', None), ('/api/replies', 'GET', None), ('/api/reply', 'POST', {'text': '나왔어'})]:
            self.assertEqual(404, self.request(path, method, body, token=OTHER)[0])
        self.assertEqual(200, self.request('/api/session', 'DELETE', token=OTHER)[0])
        self.assertEqual(200, self.request('/api/session')[0])

    def test_invalid_auth(self):
        for path, method, body in [('/api/message', 'POST', MESSAGE), ('/api/session', 'GET', None), ('/api/replies', 'GET', None), ('/api/reply', 'POST', {'text': '나왔어'}), ('/api/session', 'DELETE', None)]:
            self.assertEqual(401, self.request(path, method, body, token='')[0])
        self.assertEqual(401, self.request('/api/session', headers={'Authorization': 'Basic '+TOKEN})[0])

    def test_origin(self):
        self.seed()
        for origin, expected in [('http://'+self.host, 200), ('http://evil.invalid', 403), ('null', 403)]:
            self.assertEqual(expected, self.request('/api/session', headers={'Origin': origin})[0])

    def test_oversized_body_before_read(self):
        self.assertEqual(413, self.request('/api/message', 'POST', raw=b'', headers={'Content-Length': str(MAX_BODY+1)})[0])
        self.assertEqual(413, self.request('/api/reply', 'POST', raw=b'x'*(MAX_BODY+1))[0])

    def test_malformed_input(self):
        for raw in [b'{', b'[]', b'null', b'{}', b'\xff', b'{"id":"m","kind":[],"text":"x"}']:
            self.assertEqual(400, self.request('/api/message', 'POST', raw=raw)[0])
        self.assertEqual(400, self.request('/api/message', 'POST', MESSAGE, headers={'Content-Type': 'text/plain'})[0])
        self.assertEqual(400, self.request('/api/message', 'POST', raw=b'', headers={'Content-Length': '-1'})[0])
        self.assertEqual(400, self.request('/api/message', 'POST', raw=b'', headers={'Transfer-Encoding': 'chunked'})[0])

    def test_invalid_cursor(self):
        self.seed()
        for query in ['after=bad', 'after=-1', 'after=1&after=2', 'after=', 'location=here']:
            self.assertEqual(400, self.request('/api/replies?'+query)[0])

    def test_unknown_route(self):
        self.assertEqual(404, self.request('/api/location')[0])
        self.assertEqual(404, self.request('/api/session', 'POST', {})[0])

    def test_concurrent_requests(self):
        self.seed()
        def client(i):
            return (self.request('/api/message', 'POST', dict(MESSAGE, id='same'))[0],
                    self.request('/api/reply', 'POST', {'text': '나왔어'})[0])
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(client, range(40)))
        self.assertEqual([(200, 200)]*40, results)
        self.assertEqual(2, len(json.loads(self.request('/api/session')[2])['messages']))
        replies = json.loads(self.request('/api/replies')[2])['replies']
        self.assertEqual([str(i) for i in range(1, 41)], [r['id'] for r in replies])


if __name__ == '__main__': unittest.main()
