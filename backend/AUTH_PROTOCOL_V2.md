# Request authentication v2

Existing Python http.server bridge retained; no Express or FastAPI migration. Operational routes retain their existing paths. P-256 only, SHA-256, fixed 64-byte IEEE P1363 signature r||s encoded as standard base64.

1. Pair the intended phone through the existing explicit /api/pair enrollment flow over HTTPS. Its public JWK and fingerprint in the local bound-device registry remain authoritative. Pairing is separate from request authorization.
2. GET /auth/challenge over HTTPS with X-Key-ID (registered GW-HW fingerprint). Server returns nonce, ttl:45, issuedAt, protocol:GW_REQUEST_V2, algorithm:ECDSA_P256_SHA256_P1363.
3. Sign UTF-8: METHOD + newline + exact request path including query + newline + decimal issuedAt + newline + nonce + newline + lowercase SHA256(exact HTTP body bytes).
4. Send X-Key-ID, X-Auth-Nonce, X-Auth-Timestamp and X-Client-Signature. GET signs an empty body; empty bytes do not become '{}'. Serialize POST JSON once, sign those bytes and send the same bytes.
5. Barrier atomically consumes the key-scoped Redis nonce with GETDEL, resolves the registered key, verifies the P-256 signature and dispatches only on success. Redis >=6.2, bounded network timeouts; no memory-store fallback. Unknown keys cannot obtain operational challenges.

All operational /api/* GET routes (except public status and pairing challenge) and all POST routes except pairing execute the mandatory barrier before JSON parsing or business handlers. Security headers are mandatory; legacy nonce-only request signatures are rejected. Public status does not assert authentication. Pairing still requires a trusted enrollment procedure; this update does not add remote hardware-attestation verification or biometric Creator identification.

Install backend requirements into the desktop server's Python environment and configure REDIS_URL. Missing Redis/client dependency/provider results in 503, never authorization. Use the existing server launcher after coordinating the protocol cutover with Android. Restart is required for source changes to take effect; active processes are not restarted automatically by the patch.

Redis reference: https://redis.io/docs/latest/commands/getdel/
