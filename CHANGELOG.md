# Changelog

All notable changes to Skald Mobile are documented here.

The format is based on Keep a Changelog, and releases use Semantic Versioning for `versionName`. Android `versionCode` is a separate monotonically increasing integer managed by the release tooling.

## [Unreleased]

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

[Unreleased]: https://github.com/vardirhq/skald-mobile/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.1.0
