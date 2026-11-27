# Release notes

## Unreleased

**Restart your phone after updating.**

- Swara no longer requests the internet permission. A library the app uses (ONNX Runtime) had added
  it, along with a startup component for Microsoft telemetry. This update removes both.
- On Android 10, an updated app can still open network connections until the phone restarts.
  In our emulator tests, restarting blocked them. See [Offline privacy](offline-privacy.md#android-10-restart-after-updating).
