# Local activation

Python bridge source and auth_barrier.py have been installed at C:\Users\crypt\OmegaVolt\mobile_vault. A pre-update backup is retained in the Codex workspace. The active server process was not restarted and therefore does not yet run these changes.

Local Redis is not listening on 127.0.0.1:6379. Docker is not installed/on PATH. WSL invocation from this session fails with 'The file cannot be accessed by the system.' Windows listener/process inspection did not expose the running bridge process to this session, although certificate-validated network access to its status endpoint still works.

Once Docker Desktop or a functioning WSL environment is available on the host, use real Redis >=6.2. The supplied compose.redis.yml binds Redis only to localhost. Example from this backend directory:

    docker compose -f compose.redis.yml up -d
    python -m pip install -r requirements.txt
    python verify_redis.py

Install requirements into the Python interpreter that actually launches the desktop bridge, then restart that bridge using its existing launcher. Do not use uvicorn: the existing server is http.server, not FastAPI. Source entry point is vault_service.py. The replacement Android APK requires request protocol v2; do not pair it with an unrestarted v1 service.

The barrier rejects operations when its Redis/client dependency or cryptographic provider is unavailable. Redis storage is intentionally ephemeral: a restart invalidates issued challenges. No local mock or permissive fallback is substituted.

Cloud tests for the v2 update passed all 17 checks with real Redis, including GETDEL, expiry, concurrent replay, payload binding, and degraded-subsystem rejection. Local runtime activation remains pending; no claim is made that Redis has been started here.
