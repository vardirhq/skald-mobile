# Release invariants

A valid Skald Mobile release satisfies all of these:

- `version.properties` contains the version Gradle packages.
- The tag is exactly `v` plus `VERSION_NAME`.
- `CHANGELOG.md` contains a dated section for that version.
- The release commit is reachable from `main`.
- Android `VERSION_CODE` is a positive integer and is advanced by release preparation.
- APK and AAB are built from the tagged commit with the stable release key.
- APK and AAB signatures verify before publication.
