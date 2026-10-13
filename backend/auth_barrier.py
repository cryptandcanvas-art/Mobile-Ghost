"""P-256 request barrier for the existing synchronous OmegaVault bridge."""
import base64
import hashlib
import os
import re
import secrets
import time

try:
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.asymmetric.utils import encode_dss_signature
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.serialization import load_der_public_key, load_pem_public_key
    CRYPTO_AVAILABLE = True
except ImportError:
    CRYPTO_AVAILABLE = False

TTL = 45
MAX_BODY = 1024 * 1024
P256_ORDER = int('FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551', 16)
HEADERS = ('X-Auth-Nonce', 'X-Auth-Timestamp', 'X-Key-ID', 'X-Client-Signature')

class AuthFailure(Exception):
    def __init__(self, status, code):
        super().__init__(code)
        self.status, self.code = status, code

def canonical_request(method, target, timestamp, nonce, body):
    if not target.startswith('/') or any(c in target for c in '\r\n'):
        raise AuthFailure(401, 'INVALID_REQUEST_TARGET')
    return f'{method.upper()}\n{target}\n{timestamp}\n{nonce}\n{hashlib.sha256(body).hexdigest()}'.encode('utf-8')

class CryptoSubsystem:
    @staticmethod
    def is_available():
        if not CRYPTO_AVAILABLE:
            return False
        try:
            key = ec.generate_private_key(ec.SECP256R1())
            probe = b'GW_CRYPTO_HEALTH'
            signature = key.sign(probe, ec.ECDSA(hashes.SHA256()))
            key.public_key().verify(signature, probe, ec.ECDSA(hashes.SHA256()))
            return True
        except Exception:
            return False

    @staticmethod
    def parse_public_key(data):
        for loader in (load_der_public_key, load_pem_public_key):
            try:
                key = loader(data)
                break
            except (ValueError, TypeError):
                pass
        else:
            key = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), data)
        if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(key.curve, ec.SECP256R1):
            raise ValueError('Only P-256 EC keys are accepted')
        return key

    @staticmethod
    def verify_signature(jwk, message, signature):
        if not CRYPTO_AVAILABLE or len(signature) != 64:
            return False
        try:
            if jwk.get('kty') != 'EC' or jwk.get('crv') != 'P-256':
                return False
            coords = []
            for name in ('x', 'y'):
                value = jwk[name]
                if not isinstance(value, str) or not re.fullmatch(r'[A-Za-z0-9_-]{43}', value):
                    return False
                decoded = base64.b64decode(value + '=', altchars=b'-_', validate=True)
                if len(decoded) != 32:
                    return False
                coords.append(decoded)
            key = CryptoSubsystem.parse_public_key(b'\x04' + b''.join(coords))
            r, s = int.from_bytes(signature[:32], 'big'), int.from_bytes(signature[32:], 'big')
            if not (1 <= r < P256_ORDER and 1 <= s < P256_ORDER):
                return False
            key.verify(encode_dss_signature(r, s), message, ec.ECDSA(hashes.SHA256()))
            return True
        except Exception:
            return False

class RedisNonceStore:
    """No in-memory fallback. Redis >=6.2 required; outage rejects dispatch."""
    def __init__(self):
        self.client = None
    def _client(self):
        if self.client is None:
            import redis
            self.client = redis.Redis.from_url(os.environ.get('REDIS_URL', 'redis://127.0.0.1:6379/0'),
                socket_connect_timeout=2, socket_timeout=2, decode_responses=True)
        return self.client
    def put(self, key_id, nonce):
        # SET NX avoids accidental reuse; GETDEL is checked before enabling challenges.
        client = self._client()
        if not client.ping():
            raise RuntimeError('Redis unavailable')
        version = tuple(int(n) for n in client.info('server')['redis_version'].split('.')[:2])
        if version < (6, 2):
            raise RuntimeError('Redis >=6.2 required')
        if not client.set(f'gw:challenge:{key_id}:{nonce}', '1', ex=TTL, nx=True):
            raise RuntimeError('Nonce collision')
    def consume(self, key_id, nonce):
        return self._client().getdel(f'gw:challenge:{key_id}:{nonce}') == '1'

class RequestAuthBarrier:
    def __init__(self, resolve_key, store=None, crypto=None, clock=None):
        self.resolve_key = resolve_key
        self.store = store if store is not None else RedisNonceStore()
        self.crypto = crypto if crypto is not None else CryptoSubsystem()
        self.clock = clock if clock is not None else time.time
    def _key(self, key_id):
        if not re.fullmatch(r'GW-HW-[A-F0-9]{16}', key_id or ''):
            raise AuthFailure(401, 'INVALID_KEY_ID')
        key = self.resolve_key(key_id)
        if key is None:
            raise AuthFailure(401, 'KEY_NOT_RECOGNIZED')
        return key
    def issue(self, key_id):
        try:
            self._key(key_id)
            if not self.crypto.is_available():
                raise AuthFailure(503, 'CRYPTO_ENGINE_UNAVAILABLE')
            nonce = secrets.token_hex(32)
            self.store.put(key_id, nonce)
            return {'nonce': nonce, 'ttl': TTL, 'issuedAt': int(self.clock()),
                'protocol': 'GW_REQUEST_V2', 'algorithm': 'ECDSA_P256_SHA256_P1363'}
        except AuthFailure:
            raise
        except Exception:
            raise AuthFailure(503, 'SECURITY_STORE_UNAVAILABLE') from None
    def authorize(self, method, target, headers, body=b''):
        try:
            values = []
            for name in HEADERS:
                # Duplicate security headers are ambiguous and must not be accepted.
                if hasattr(headers, 'get_all') and len(headers.get_all(name, [])) != 1:
                    raise AuthFailure(401, 'AUTH_HEADERS_MISSING_OR_DUPLICATE')
                value = headers.get(name)
                if not value:
                    raise AuthFailure(401, 'AUTH_HEADERS_MISSING')
                values.append(value)
            nonce, timestamp, key_id, encoded_signature = values
            if not re.fullmatch(r'[0-9a-f]{64}', nonce):
                raise AuthFailure(401, 'INVALID_NONCE_FORMAT')
            if not re.fullmatch(r'[0-9]{1,12}', timestamp):
                raise AuthFailure(401, 'INVALID_TIMESTAMP_FORMAT')
            if abs(int(self.clock()) - int(timestamp)) > TTL:
                raise AuthFailure(401, 'TIMESTAMP_DRIFT_EXCEEDED')
            key = self._key(key_id)
            if len(body) > MAX_BODY:
                raise AuthFailure(413, 'BODY_TOO_LARGE')
            if len(encoded_signature) != 88:
                raise AuthFailure(401, 'MALFORMED_SIGNATURE')
            try:
                signature = base64.b64decode(encoded_signature, validate=True)
            except Exception:
                raise AuthFailure(401, 'MALFORMED_SIGNATURE') from None
            if len(signature) != 64:
                raise AuthFailure(401, 'MALFORMED_SIGNATURE')
            if not self.store.consume(key_id, nonce):
                raise AuthFailure(401, 'INVALID_OR_CONSUMED_NONCE')
            if not self.crypto.is_available():
                raise AuthFailure(503, 'CRYPTO_ENGINE_UNAVAILABLE')
            if not self.crypto.verify_signature(key, canonical_request(method, target, timestamp, nonce, body), signature):
                raise AuthFailure(401, 'SIGNATURE_VERIFICATION_FAILED')
            return key_id
        except AuthFailure:
            raise
        except Exception:
            raise AuthFailure(503, 'SECURITY_GATE_FAILURE') from None
