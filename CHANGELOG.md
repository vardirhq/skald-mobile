# Changelog

All notable changes to Skald Mobile are documented here.

The format is based on Keep a Changelog, and releases use Semantic Versioning for `versionName`. Android `versionCode` is a separate monotonically increasing integer managed by the release tooling.

## [Unreleased]

### Added

### Changed

### Fixed

## [0.2.6] - 2026-08-10

### Added

### Changed

### Fixed

- Fixed every sheet drawing its bottom edge underneath the Android navigation bar, which left Save and Cancel sitting behind the system buttons.
## [0.2.5] - 2026-08-10

### Added

### Changed

- Sheet actions now split the width of the sheet and clear a 44dp touch target, instead of sitting as two small words in the bottom-right corner.

### Fixed

- Fixed the Save and Cancel buttons scrolling away with the fields on long sheets — thread editing, note properties and new note — so reaching Save no longer means scrolling to the very end of the sheet. On short screens they could be pushed off the bottom edge entirely.
## [0.2.4] - 2026-08-10

### Added

### Changed

- Android release signing now uses a genuine JKS keystore with separate store and key passwords, matching the proven Boss Fight release setup.

### Fixed

- Fixed the generated signing backup using PKCS12 despite carrying a `.jks` filename, which caused repeated release credential failures.
- Signing preflight now verifies both the JKS store password and the private-key password by signing a probe JAR before the Android build starts.
## [0.2.3] - 2026-08-10

### Added

### Changed

- Release signing now uses the fixed `skald` key alias instead of relying on a configurable GitHub Actions alias secret.
- Signing validation now distinguishes between an invalid keystore/store password and a missing signing alias before the Android build starts.

### Fixed

- Fixed the release signing preflight so misconfigured signing credentials fail immediately with an actionable error instead of surfacing later during APK packaging.
## [0.2.2] - 2026-08-10

### Added

### Changed

- Signed release CI now validates the Android signing keystore before starting the expensive build.

### Fixed

- Fixed PKCS12 release signing by using the keystore password for the private-key entry, matching the generated Skald signing store.
## [0.2.1] - 2026-08-10

### Added

### Changed

- Debug APKs now install as `no.vardir.skald.dev`, keeping development builds separate from production installs.
- Release preparation now exercises R8 minification so release-only shrinker failures are caught before tagging.

### Fixed

- Fixed signed release builds failing in R8 on Tink's compile-time Error Prone annotations.
## [0.2.0] - 2026-08-10

### Added

- Mobile-first note creation, folder creation, note actions, folder actions, and thread editing sheets.
- Rich live Markdown editing with formatting controls, suggestions, task/thread context, source mode, and editable frontmatter properties.
- Search, backlinks, constellation navigation, settings, and sync controls tailored for phone use.
- Proper Android release tooling with centralized version metadata, automated release preparation, signed APK/AAB builds, signature verification, checksums, and GitHub Release publishing.

### Changed

- Reworked the main shell and navigation for a more native mobile workflow, including contextual top bars, bottom navigation, floating compose actions, and keyboard-aware layout behavior.
- Improved consistency across note, folder, thread, and editor interactions.
- Android versioning now uses `version.properties` as the single source of truth for both `versionName` and `versionCode`.

### Fixed

- Corrected stale Compose callback call sites after component API changes.
- Restored the missing new-folder flow that prevented release builds from compiling.
- Fixed several editor and sheet integration issues uncovered by CI while bringing the mobile UX together.

## [0.1.0] - 2026-08-10

### Added

- Initial Android application foundation with Kotlin and Jetpack Compose.
- Local-first Skald vault browsing, editing, typed notes, threads, backlinks, attachments, settings, and constellation views.
- GESH-backed encrypted sync and device pairing support.
- Debug APK CI for pull requests and `main`.

[0.1.0]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.1.0

[0.2.0]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.2.0

[0.2.1]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.2.1

[0.2.2]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.2.2

[0.2.3]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.2.3

[0.2.4]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.2.4

[0.2.5]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.2.5

[Unreleased]: https://github.com/vardirhq/skald-mobile/compare/v0.2.6...HEAD
[0.2.6]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.2.6
