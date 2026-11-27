# Offline privacy

Swara is meant to work without a network connection. Android libraries can still add permissions and
startup components to an app through manifest merging. ONNX Runtime 1.31.1 added two things this way:

- the `android.permission.INTERNET` permission
- `ai.onnxruntime.TelemetryInitializer`, a startup component for Microsoft telemetry

## Safeguards

| Safeguard | Where |
| --- | --- |
| Remove the permission and the component from the merged manifest | `app/src/main/AndroidManifest.xml` |
| Set `ORT_DISABLE_TELEMETRY=1` before any ONNX Runtime code runs | `SwaraApplication.attachBaseContext` |
| Fail the build on an unapproved permission or any `ai.onnxruntime.` component | `verifyMergedManifests` Gradle task, also run in CI |
| Check the installed app on a device | `OfflinePrivacyTest` instrumented test |

## Android 10: restart after updating

**What happens:** after updating from a version that had the internet permission, the app on
Android 10 can still open network connections until the device restarts. The updated app no longer
lists the permission. A likely explanation is that Android applies the removal only after a restart,
but we have not confirmed this.

**Tell users:** restart your phone after updating. This is an accepted, temporary limitation. In our
tests, the component stayed removed and the telemetry opt-out stayed set during this time. Neither
one blocks network access.

**How it was tested** (emulators, debug builds, November 2026). The test tried TCP connections from
inside the app's process.

| Android version | After an update, before restart | After restart | Fresh install |
| --- | --- | --- | --- |
| 10 (API 29) | **Connected** (2 trials) | Blocked (2 trials) | Blocked without a restart (1 trial, test server only) |
| 15 (API 35) | Blocked (1 trial) | Blocked (1 trial) | Not part of this check |
| 16 (API 36) | Blocked (1 trial) | Blocked (1 trial) | Not part of this check |
| 11 to 14 (API 30 to 34) | **Untested** | **Untested** | **Untested** |
| Real phones, any version | **Untested** | **Untested** | **Untested** |

This check has not been run on a release build.

**What this does not show:** these tests check whether the app could connect at the times tested.
They are not a claim that the app makes no network calls, and they do not show whether any earlier
version sent telemetry data.

## If a feature needs the internet later

Adding the permission back removes one safeguard on every Android version. Removing the ONNX
Runtime component and setting the opt-out then become the main protections. Update the allowed
permission list in `app/build.gradle` and the internet checks in `OfflinePrivacyTest` in the same
change.
