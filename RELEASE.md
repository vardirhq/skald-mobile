# Release quick reference

See [`docs/RELEASING.md`](docs/RELEASING.md) for the full process.

1. Keep `CHANGELOG.md` → `[Unreleased]` current.
2. Run **Prepare release** in GitHub Actions.
3. Merge the generated `release/vX.Y.Z` PR.
4. Run **Android signed release** with the tag input empty.
5. GitHub publishes a signature-verified APK, AAB, checksums, and provenance attestation.

Required repository secrets: `SKALD_ANDROID_KEYSTORE_BASE64`, `SKALD_ANDROID_STORE_PASSWORD`, `SKALD_ANDROID_KEY_PASSWORD`; `SKALD_ANDROID_KEY_ALIAS` is optional and defaults to `skald`.
