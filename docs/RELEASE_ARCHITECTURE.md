# Release architecture

The release system has three gates:

1. **Normal CI** validates release metadata, runs core tests, and proves the Android debug build compiles on every PR and main push.
2. **Prepare release** is the only normal path for changing release numbers. It converts `[Unreleased]` into a dated release, increments Android `versionCode`, validates the candidate, and opens a release PR.
3. **Android signed release** only operates on a release commit already reachable from `main`. It checks the tag against `versionName`, restores the signing key ephemerally, builds APK/AAB, verifies signatures, hashes and attests artifacts, then publishes them.

This keeps signing secrets out of pull-request jobs and makes the changelog/version state auditable before a tag or binary is published.
