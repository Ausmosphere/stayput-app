# Stayput MVP validation

Validated on 7 November 2026.

- Debug APK builds successfully against Android API 35; minimum API 26.
- Android lint: zero errors and zero warnings.
- APK signature verifies with Android apksigner (debug v2 signature).
- 17 standalone clock, countdown, streak, weekday recurrence, and daylight-saving checks pass.
- 70 device integration checks pass on an Android 15 / API 35 Google APIs emulator, including real accessibility overlays, a whitelisted Settings app, accessibility-service reconnection, asynchronous persistence, and atomic early-exit state.
- UI checks also passed at 200% font size with reduced motion, covering keyboard and accessibility navigation, duration controls, labeled fields, unsaved drafts, app selection, and keyboard-safe save controls.
- The updated debug APK was installed and visually checked on a Pixel 9 Pro XL running Android 17 / API 37, with existing preferences preserved.
- Captured and visually reviewed the focus screen, active session, permissions, routines, tower, settings, full-screen app block, and floating timer over Android Settings.

The device checks cover: permission availability; actual accessibility-service connection; session readiness; session start; overlapping-session rejection; immutable active whitelist; fifteen-minute extension; exhausted demo keys; allowed-app access; non-allowed-app blocking; early exit; exact key consumption; overlay removal; early-exit persistence without tower credit; idempotent finish; automatic timer completion; completion persistence; one floor per completed day; and avoiding duplicate floors for multiple sessions on a day.

Instrumentation itself force-stops its target package and leaves Android's prior accessibility binding dead. The disposable-emulator fixture explicitly reconnects that binding before testing. Normal application onboarding also detects a disconnected service and requires reconnection rather than claiming blocking is ready.

The Android 15 checks used a hardware-accelerated emulator on Windows.
The Pixel check covered the UI and installation; OEM background restrictions, routine/DND behavior, and vendor-specific settings still need dedicated physical-device testing.
Nothing Glyph requires a compatible physical phone and was not hardware-tested.
Audible output was not evaluated on physical speakers; the included WAV files were generated locally and packaged in the APK.

The original API 28 validation results are committed in `validation/core-tests.txt`, `validation/device-tests.txt`, and `validation/build.txt`.
Current local device results are written to ignored `artifacts/device-tests.txt`, and lint reports are under `app/build/reports/`.
The original UI screenshots are in `docs/screenshots/`.
The repository includes the source, Gradle wrapper, required SDK dependency, original audio assets, and validation evidence; machine-local SDK settings, build outputs, credentials, and signing keys are excluded.
