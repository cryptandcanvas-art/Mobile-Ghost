# Build status

- Verified Microsoft OpenJDK 17.0.14.
- Installed Android SDK platform 35, build tools 35.0.0 and platform tools, with owner-approved SDK license.
- Generated Gradle 8.11.1 wrapper; distribution checksum pinned.
- All Android Java source files compile against API 35 using javac. DeviceIdentity emits a deprecation note for KeyInfo.isInsideSecureHardware.
- Standalone defense policy checks passed.
- Certificate-authenticated HTTPS reads of status/feed succeeded; no server mutations or pairing performed.

APK assembly is blocked at Android resource linking: both Gradle's AAPT2 8.9.2 and SDK AAPT2 35.0.0 report an unexpected daemon link failure. A direct AAPT2 invocation also exits with Windows access-violation code -1073741819. Native resource-tool failure needs further diagnosis; this is not a successful APK build. Lint did not complete because the resource prerequisite failed. No installable APK is delivered yet.

Device runtime, hardware enrollment, network authentication enforcement and UI rendering are untested. Observed backend authentication gaps are documented in README.md.
