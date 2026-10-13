"""Probe a real local Redis without touching vault keys or user data."""
import os
import secrets
import redis

client = redis.Redis.from_url(os.environ.get('REDIS_URL', 'redis://127.0.0.1:6379/0'),
    socket_connect_timeout=2, socket_timeout=2, decode_responses=True)
assert client.ping(), 'Redis ping failed'
version = client.info('server')['redis_version']
assert tuple(int(n) for n in version.split('.')[:2]) >= (6, 2), 'Redis >=6.2 required'
probe = 'gw:setup-probe:' + secrets.token_hex(16)
assert client.set(probe, 'ok', ex=10, nx=True)
assert client.getdel(probe) == 'ok', 'GETDEL failed'
assert client.getdel(probe) is None, 'Nonce unexpectedly reused'
print(f'Redis {version}: PONG, GETDEL and one-use consumption verified')
