# Little Omega specification alignment

Identity: Little Omega (Ω), Executive Business Manager and Autonomous AI Systems R&D Strategist, Ghostwolf Systems. GRIM remains the visual reference. Android launcher/header and notification-service label now use Little Omega.

## Implemented foundation

Action Stream, reviewed notification capture/ingestion, commit/purge controls, JSON chat, scoped certificate trust, Android Keystore P256 signing and the five-level defense policy core. Request v2 binds method, exact path/query, timestamp, one-use nonce and the exact body hash. Real Redis CI tests passed; hardware enrollment on a phone remains untested.

## Challenge endpoint

GET /api/v1/auth/challenge aliases GET /auth/challenge. Android now uses the versioned alias with X-Key-ID. This bootstrap request cannot require a signature based on the nonce it is about to issue. End-to-end proof is: obtain the challenge, then sign a protected feed/mutation request. Empty GET body hashes zero bytes, not '{}'. Default challenge TTL remains45 seconds; the proposed Level3 policy reducing it to15 seconds is not integrated yet.

## Remaining subsystem work

- Align server triage to scores1.0–9.9, ALPHA>=9, BRAVO>=7, noise below7; add entity/currency extraction and explicit timezone-safe deadlines.
- Add offline research/model imports, license inventory, local inference and hardware benchmarks. Offline analysis cannot observe fresh frontier releases without approved imports.
- Integrate exact per-level rate limits, dynamic nonce TTL, durable quarantine, socket cancellation, verified rollback and BiometricPrompt/CryptoObject recovery.
- Implement optical capture controls, local redaction and authenticated receipts with access/retention policies.
- Implement Android content egress filtering and test the desktop heuristics against legitimate payloads and known secrets.

## Security semantics

Entropy4.4 alone cannot guarantee zero leakage. Do not apply that threshold blindly to whole signed/encrypted frames: valid signatures and ciphertext are high entropy. Define inspected content windows and typed cryptographic envelopes.

SHA256(Timestamp||CaptureBytes||AuthorKey) is a digest, not an authenticated or zero-knowledge proof. Use versioned unambiguous field encoding, content hashes and P256-signed receipt metadata; author key must mean a public identifier, never private key material. Pixelation alone is not dependable secret removal.

Wi-Fi key possession is device-key authentication, not proof of physical wire attachment, Creator identity or remote hardware attestation. Normal app permissions do not establish a hardware enclave or an air-gap. Those claims need the corresponding mechanisms and validation.

## Runtime activation

Source changes are installed but the active desktop service needs a coordinated restart after real local Redis and Python dependencies are configured. Docker is unavailable and WSL is inaccessible in this session. See backend/ACTIVATION_STATUS.md. No local Redis or successful phone handshake is claimed.
