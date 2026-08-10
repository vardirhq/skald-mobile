# Android release artifacts

The signed release workflow publishes both Android distribution formats:

- APK for direct installation and GitHub distribution.
- AAB for stores that consume Android App Bundles.

Both are generated from the same tagged source commit and signing identity. The workflow refuses publication if either artifact is missing or signature verification fails. SHA-256 checksums are published alongside them, and GitHub build provenance attestations are generated for the binary artifacts.
