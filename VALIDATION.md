# Stayput MVP validation

Validated on 7 November 2026.

- Debug APK builds successfully against Android API 35; minimum API 26.
- Android lint: zero errors (remaining warnings concern English-only MVP text, intentional synchronous session-state persistence, custom programmatic views, and similar non-blocking items).
- APK signature verifies with Android apksigner (debug v2 signature).
- 17 standalone clock, countdown, streak, weekday recurrence, and daylight-saving checks pass.
- 21 device integration checks pass on an Android 9 / API 28 AOSP emulator, including real accessibility overlays and a whitelisted Settings app.
- Captured and visually reviewed the focus screen, active session, permissions, routines, tower, settings, full-screen app block, and floating timer over Android Settings.

The device checks cover: permission availability; actual accessibility-service connection; session readiness; session start; overlapping-session rejection; immutable active whitelist; fifteen-minute extension; exhausted demo keys; allowed-app access; non-allowed-app blocking; early exit; exact key consumption; overlay removal; early-exit persistence without tower credit; idempotent finish; automatic timer completion; completion persistence; one floor per completed day; and avoiding duplicate floors for multiple sessions on a day.

Instrumentation itself force-stops its target package and leaves Android's prior accessibility binding dead. The disposable-emulator fixture explicitly reconnects that binding before testing. Normal application onboarding also detects a disconnected service and requires reconnection rather than claiming blocking is ready.

An Android 15 emulator was attempted but restarted its system services during first boot without hardware acceleration; no Stayput runtime claims rely on that image. A modern physical Android phone is still needed to verify OEM background restrictions, Android 15-specific routine/DND behavior, and vendor-specific settings. Nothing Glyph requires a compatible physical phone and was not hardware-tested. Audible output was not evaluated on physical speakers; the included WAV files were generated locally and packaged in the APK.

Raw successful results are committed in `validation/core-tests.txt`, `validation/device-tests.txt`, and `validation/build.txt`. UI screenshots are in `docs/screenshots/`. The repository includes the source, Gradle wrapper, required SDK dependency, original audio assets, and validation evidence; machine-local SDK settings, build outputs, credentials, and signing keys are excluded.
