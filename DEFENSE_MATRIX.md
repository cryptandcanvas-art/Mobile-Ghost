# Adaptive five-level defense matrix

Status: policy core added in DefenseMatrix.java. Not integrated into networking, telemetry, Keystore, snapshot storage, or UI. No production protection is claimed from this policy class alone. Existing client transport remains an initial foundation.

| Level | Required behavior | Enforcement owner |
| --- | --- | --- |
| 1 — Nominal | Authenticate state every 60 seconds and before dispatch when overdue; screen outbound application payloads | Android app and desktop bridge each protect their own state and egress |
| 2 — Guard | Detect measured request spikes or unexpected application headers; throttle; deepen secret inspection | Request telemetry and application protocol validator on both ends |
| 3 — Flood | Sliding-window detection plus token-bucket admission; per-peer and global quotas; bounded connection/work queues | Desktop listener/reverse proxy; host firewall for SYN and network floods |
| 4 — Containment | Disable bridge, isolate affected session/service, block attributable hostile peers, recover from known clean configuration | Android owns its sockets; bridge owns service isolation and restart; firewall owns peer blocking |
| 5 — Quarantine | Close owned sockets, halt dispatch, authenticate and restore a known snapshot; fresh Creator biometric authorization before unseal | Device-specific Keystore/biometric adapter and local storage; independent bridge quarantine |

## Policy behavior implemented

Monotonic 60-second integrity-check policy, explicit threat-to-level mapping, latched escalation, bounded in-memory transition records, dispatch denial before successful enforcement and at Levels 4–5, and an enforcement adapter contract. Failed enforcement never lowers the requested level. Recovery must perform a cryptographically authorized biometric operation and revalidate state. The class has no credential strings or boolean biometric bypass.

## Security semantics to preserve during integration

- SHA-256 alone proves correspondence to a digest, not who authorized a snapshot. Authenticate snapshot metadata and content with a Keystore-backed MAC/signature, atomic storage, and a defined anti-rollback policy. Quarantine must remain latched across process restart; this policy's in-memory state is insufficient for that.
- Entropy is a signal, not a complete DLP verdict: ciphertext and benign random data can have high entropy; secrets can have low entropy. Apply schema/allowlist checks, known-secret rules, and explicit data-boundary decisions before transmission.
- Android notification access does not grant visibility into other apps' memory or arbitrary network traffic. A normal app cannot reliably detect all rogue apps or memory injection attempts. Define observable signals and treat unobservable conditions as unknown.
- Device-wide traffic mediation requires separately authorized VpnService or privileged management. VPN packet handling cannot prevent a Wi-Fi link from being saturated. Local app and host firewall controls have different boundaries.
- An IP alone is not identity. NAT/shared addresses and spoofable signals require care before persistent blocking. Unknown headers must be evaluated against an actual protocol schema; optional benign headers are not automatically intrusion.
- Clean service recovery is not an Android handset reboot. Android cannot silently reboot the whole phone from this normal app. Quarantine must stop in-flight and queued requests, and all sends must traverse the same gate.
- Baseline checks every 60 seconds while idle need an Android-compatible foreground lifecycle; tick() alone is not a scheduler. Battery/background restrictions must be tested on target devices.

## Remaining implementation

Real detectors and thresholds; token buckets/window counters; transport cancellation and gate integration; DLP; durable quarantine; encrypted authenticated snapshots; biometric CryptoObject adapter; trusted Creator enrollment; host firewall/service restart adapter; false-positive/recovery tests. API schemas must expose actual defense evidence before UI badges report active protection.

References: https://developer.android.com/reference/android/net/VpnService and https://developer.android.com/identity/sign-in/biometric-auth
