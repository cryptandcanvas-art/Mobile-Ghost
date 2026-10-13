import base64
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import ssl
import tempfile
import threading
import time
import unittest
from email.message import Message
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import Mock, patch
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature
import auth_barrier as auth

KEY_ID = 'GW-HW-0123456789ABCDEF'

class MemoryStore:
    """Test double only; never used by the bridge."""
    def __init__(self, clock):
        self.clock, self.values, self.lock = clock, {}, threading.Lock()
    def put(self, key, nonce):
        with self.lock: self.values[key, nonce] = self.clock() + auth.TTL
    def consume(self, key, nonce):
        with self.lock: return self.values.pop((key, nonce), 0) > self.clock()

class AuthTests(unittest.TestCase):
    def setUp(self):
        self.now = time.time()
        self.private = ec.generate_private_key(ec.SECP256R1())
        point = self.private.public_key().public_numbers()
        enc = lambda n: base64.urlsafe_b64encode(n.to_bytes(32, 'big')).decode().rstrip('=')
        self.jwk = {'kty': 'EC', 'crv': 'P-256', 'x': enc(point.x), 'y': enc(point.y)}
        self.clock = lambda: self.now
        self.store = MemoryStore(self.clock)
        self.barrier = auth.RequestAuthBarrier(lambda key: self.jwk if key == KEY_ID else None, self.store, clock=self.clock)
    def signed(self, method='POST', target='/api/inbox/ingest?source=test', body=b'{"text":"hello"}'):
        challenge = self.barrier.issue(KEY_ID)
        stamp, nonce = str(challenge['issuedAt']), challenge['nonce']
        signature = self.private.sign(auth.canonical_request(method, target, stamp, nonce, body), ec.ECDSA(hashes.SHA256()))
        r, s = decode_dss_signature(signature)
        headers = {'X-Key-ID': KEY_ID, 'X-Auth-Nonce': nonce, 'X-Auth-Timestamp': stamp,
            'X-Client-Signature': base64.b64encode(r.to_bytes(32, 'big') + s.to_bytes(32, 'big')).decode()}
        return method, target, headers, body
    def reject(self, request, code):
        with self.assertRaises(auth.AuthFailure) as result: self.barrier.authorize(*request)
        self.assertEqual(code, result.exception.code)
    def test_valid_and_replay(self):
        request = self.signed()
        self.assertEqual(KEY_ID, self.barrier.authorize(*request))
        self.reject(request, 'INVALID_OR_CONSUMED_NONCE')
    def test_body_method_and_query_binding(self):
        for index, value in ((0, 'GET'), (1, '/api/inbox/ingest?source=changed'), (3, b'{ "text": "hello" }')):
            request = list(self.signed()); request[index] = value
            self.reject(request, 'SIGNATURE_VERIFICATION_FAILED')
    def test_empty_body_hash(self):
        request = self.signed('GET', '/api/inbox/feed', b'')
        self.assertEqual(KEY_ID, self.barrier.authorize(*request))
        self.assertNotEqual(auth.canonical_request('GET','/x','1','n',b''), auth.canonical_request('GET','/x','1','n',b'{}'))
    def test_expired(self):
        request = self.signed(); self.now += 46
        self.reject(request, 'TIMESTAMP_DRIFT_EXCEEDED')
    def test_nonce_expiry_independent_of_timestamp(self):
        request = list(self.signed()); self.now += 46; request[2]['X-Auth-Timestamp'] = str(int(self.now))
        self.reject(request, 'INVALID_OR_CONSUMED_NONCE')
    def test_missing_and_duplicate_headers(self):
        request = list(self.signed()); request[2].pop('X-Client-Signature'); self.reject(request, 'AUTH_HEADERS_MISSING')
        request = list(self.signed()); message = Message()
        for name, value in request[2].items(): message[name] = value
        message['X-Key-ID'] = KEY_ID; request[2] = message
        self.reject(request, 'AUTH_HEADERS_MISSING_OR_DUPLICATE')
    def test_timestamp_strict_format(self):
        request = self.signed(); request[2]['X-Auth-Timestamp'] += 'junk'
        self.reject(request, 'INVALID_TIMESTAMP_FORMAT')
    def test_signature_encoding(self):
        request = self.signed(); request[2]['X-Client-Signature'] = '!' * 88
        self.reject(request, 'MALFORMED_SIGNATURE')
    def test_crypto_outage(self):
        request = self.signed()
        with patch.object(self.barrier.crypto, 'is_available', return_value=False):
            self.reject(request, 'CRYPTO_ENGINE_UNAVAILABLE')
        self.reject(request, 'INVALID_OR_CONSUMED_NONCE')
    def test_real_provider_health_failure(self):
        with patch.object(auth.ec, 'generate_private_key', side_effect=RuntimeError('provider offline')):
            self.assertFalse(auth.CryptoSubsystem.is_available())
    def test_store_outage(self):
        request = self.signed()
        with patch.object(self.store, 'consume', side_effect=ConnectionError()): self.reject(request, 'SECURITY_GATE_FAILURE')
        with patch.object(self.store, 'put', side_effect=ConnectionError()):
            with self.assertRaises(auth.AuthFailure) as result: self.barrier.issue(KEY_ID)
            self.assertEqual(503, result.exception.status)
    def test_unknown_key(self):
        with self.assertRaises(auth.AuthFailure): self.barrier.issue('GW-HW-FFFFFFFFFFFFFFFF')
    def test_wrong_curve_and_integer_ranges(self):
        from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
        key = ec.generate_private_key(ec.SECP384R1()).public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)
        with self.assertRaises(ValueError): auth.CryptoSubsystem.parse_public_key(key)
        self.assertFalse(auth.CryptoSubsystem.verify_signature(self.jwk, b'test', b'\0' * 64))
        self.assertFalse(auth.CryptoSubsystem.verify_signature(self.jwk, b'test', auth.P256_ORDER.to_bytes(32,'big') * 2))
    def test_concurrent_replay(self):
        request = self.signed()
        def attempt(_):
            try: self.barrier.authorize(*request); return True
            except auth.AuthFailure: return False
        with ThreadPoolExecutor(max_workers=8) as pool: self.assertEqual(1, sum(pool.map(attempt, range(8))))
    def test_real_redis_getdel(self):
        if not os.environ.get('AUTH_TEST_REDIS'): self.skipTest('Real Redis integration runs in CI')
        self.barrier.store = auth.RedisNonceStore()
        request = self.signed()
        self.assertEqual(KEY_ID, self.barrier.authorize(*request))
        self.reject(request, 'INVALID_OR_CONSUMED_NONCE')
    def test_real_redis_expiry_and_concurrency(self):
        if not os.environ.get('AUTH_TEST_REDIS'): self.skipTest('Real Redis integration runs in CI')
        self.barrier.store = auth.RedisNonceStore()
        request = self.signed()
        nonce_key = f"gw:challenge:{KEY_ID}:{request[2]['X-Auth-Nonce']}"
        client = self.barrier.store._client()
        self.assertGreaterEqual(client.ttl(nonce_key), 43)
        self.assertLessEqual(client.ttl(nonce_key), 45)
        client.pexpire(nonce_key, 20)
        time.sleep(0.08)
        self.reject(request, 'INVALID_OR_CONSUMED_NONCE')
        request = self.signed()
        def attempt(_):
            try: self.barrier.authorize(*request); return True
            except auth.AuthFailure: return False
        with ThreadPoolExecutor(max_workers=8) as pool: self.assertEqual(1, sum(pool.map(attempt, range(8))))
    def test_handler_barrier_runs_before_payload_logic(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(__file__).with_name('vault_service.py')
            target = Path(directory) / 'vault_service.py'; shutil.copyfile(source, target)
            spec = importlib.util.spec_from_file_location('isolated_bridge', target)
            bridge = importlib.util.module_from_spec(spec); spec.loader.exec_module(bridge)
            bridge.AUTH_BARRIER = self.barrier
            bridge.THREAT_ENGINE = Mock(); bridge.THREAT_ENGINE.evaluate_request.return_value = (True, '')
            bridge.INBOX_ENGINE = Mock()
            for route in ('/api/inbox/ingest', '/api/inbox/commit', '/api/inbox/purge', '/api/omega/chat'):
                handler = bridge.MobileVaultHandler.__new__(bridge.MobileVaultHandler)
                handler.command, handler.path, handler.client_address = 'POST', route, ('127.0.0.1', 1234)
                handler.connection = Mock(spec=ssl.SSLSocket)
                handler.headers = Message(); handler.headers['Content-Length'] = '2'
                handler.rfile = io.BytesIO(b'{}'); handler._send_json = Mock()
                handler.do_POST()
                self.assertEqual(401, handler._send_json.call_args.args[0])
            bridge.INBOX_ENGINE.parse_and_ingest.assert_not_called()
            bridge.INBOX_ENGINE.commit.assert_not_called()
            bridge.INBOX_ENGINE.purge.assert_not_called()

if __name__ == '__main__': unittest.main()
