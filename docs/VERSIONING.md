# Versioning

Skald Mobile uses two coordinated version values in `version.properties`:

- `VERSION_NAME` is the public Semantic Version, for example `0.2.0`.
- `VERSION_CODE` is Android's private monotonically increasing integer.

Every release increments `VERSION_CODE` by exactly one, including pre-releases. `VERSION_NAME` is bumped according to SemVer. Gradle reads both values directly, so there is no duplicate version declaration in `app/build.gradle.kts` to drift out of sync.

Do not edit release versions in Gradle. Use the **Prepare release** workflow or `python tools/release.py prepare ...`.
