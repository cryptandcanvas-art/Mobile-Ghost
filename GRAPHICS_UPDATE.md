# Little Omega 0.2 graphics

Vector Ω shield, adaptive launcher icon, neutral-to-live-tinted sentry reticle and custom orbital hero. Black/teal palette follows the GRIM flagship site. Native dashboard has separate Stream, Brain and Settings views; setup/pairing is moved into Settings. Action cards display priority, score, origin and clear commit/purge actions. Chat has its own composer and reply panel. Notification review remains explicit.

Action count starts unknown and is populated by received data. Sentry tint and level derive from authenticated server telemetry; unavailable or stale checks become neutral/unverified. No graphic indicates that incomplete local defense or biometric adapters are active.

## Signing and update limitation

The first installed APK used an ephemeral GitHub runner debug signing key. That private package-signing key was not retained. A differently signed APK cannot update it in place. Uninstalling that app would delete the Android Keystore device identity, so a replacement needs an authorized vault-binding reset and phone re-enrollment. Do not uninstall or clear data until that cutover is coordinated.

This is separate from the server TLS certificate and the phone's P-256 request-signing key. A stable local package-signing key will be used for subsequent delivered APKs and kept outside the repository. It must be backed up securely to preserve update continuity. No private key or signing password belongs in GitHub source or a public artifact.
