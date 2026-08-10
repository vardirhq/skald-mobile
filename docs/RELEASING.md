# Releasing Skald Mobile

Skald Mobile uses Semantic Versioning for `versionName` and a monotonically increasing integer for Android `versionCode`. Both live in `version.properties`; Gradle reads that file directly.

## One-time signing setup

Create and protect one long-lived Android release keystore. Do not replace it between releases: Android requires future APKs with the same application ID to be signed by the same key in order to upgrade an installed app.

Configure these GitHub Actions repository secrets:

- `SKALD_ANDROID_KEYSTORE_BASE64` — the JKS keystore encoded as a single-line base64 string.
- `SKALD_ANDROID_STORE_PASSWORD` — keystore password.
- `SKALD_ANDROID_KEY_PASSWORD` — key password.
- `SKALD_ANDROID_KEY_ALIAS` — optional; defaults to `skald`.

Keep an offline backup of the keystore and credentials outside GitHub.

## Normal release flow

1. Add user-visible changes under `[Unreleased]` in `CHANGELOG.md` as development happens.
2. Run **Actions → Prepare release** and choose `patch`, `minor`, `major`, or a custom SemVer version.
3. The workflow increments `versionCode`, bumps `versionName`, dates the changelog, verifies the candidate, and opens `release/vX.Y.Z` as a PR.
4. Review and merge that PR.
5. Run **Actions → Android signed release** with the tag field empty.
6. The workflow verifies that release metadata agrees, creates `vX.Y.Z`, runs tests, restores the signing key only inside the runner, builds signed APK and AAB artifacts, verifies their signatures, generates SHA-256 checksums and build provenance attestations, and publishes the GitHub Release.

Pushing a valid `vX.Y.Z` tag manually also triggers the signed release workflow.

## Local metadata commands

```bash
python tools/release.py version
python tools/release.py check
python tools/release.py prepare patch
python tools/release.py notes
```

`prepare` refuses to cut a release when `[Unreleased]` contains no bullet entries.

## Release artifacts

Each GitHub Release contains:

- `Skald-Mobile-X.Y.Z.apk` — signed direct-install APK.
- `Skald-Mobile-X.Y.Z.aab` — signed Android App Bundle.
- `SHA256SUMS.txt` — SHA-256 hashes for both artifacts.

The APK is additionally checked with Android `apksigner`, and the AAB is checked with `jarsigner`, before publication.
