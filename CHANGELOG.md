# Changelog

All notable changes to Skald Mobile are documented here.

The format is based on Keep a Changelog, and releases use Semantic Versioning for `versionName`. Android `versionCode` is a separate monotonically increasing integer managed by the release tooling.

## [Unreleased]

### Added

### Changed

### Fixed

## [0.3.3] - 2026-08-12

### Added

- Added native Markdown tables and local Mermaid flowchart rendering. Tables preserve inline
  formatting and scroll horizontally on narrow screens; synced `mermaid` fences render as
  theme-aware diagrams without uploading their source. Unsupported Mermaid families retain a
  labelled source fallback instead of silently becoming paragraph text.

### Changed

### Fixed
## [0.3.2] - 2026-08-12

### Added

- Added a searchable Insert sheet for headings, formatting, links, lists, tasks, callouts, code,
  tables, dividers, and registered extension components. It preserves the current selection in
  Live and Source modes, supports `Ctrl+I` / `⌘I` on physical keyboards, and includes a GitHub
  repository action that collects the note property before inserting its portable callout.
### Changed

- Replaced the fourteen unexplained glyphs above the keyboard with a compact quick bar for Bold,
  Italic, Task, Note link, Insert, and Done. Less-common actions now have visible names and
  descriptions in the Insert sheet instead of requiring users to decipher Markdown symbols.

### Fixed
## [0.3.1] - 2026-08-12

### Added

- Live GitHub repository cards on Android now fetch public repository metadata without
  login, cache public responses for offline use, refresh with ETags, and show repository,
  issue, pull-request, release, and workflow details in the native Compose renderer.
- Optional GitHub Device Flow login for private repository cards. Tokens are accepted only
  when Android Keystore-backed encrypted storage is available; private responses stay in
  memory and are never written to the public repository cache.

- Added the mobile half of Skald's built-in extension system: versioned manifests,
  per-platform capabilities, collision-safe component and property registration, and a
  Compose renderer registry. The GitHub card is now the first registered extension instead
  of a special case in `MarkdownView`; unknown components still render as portable callouts.

- GitHub repository-card compatibility with desktop Skald. Mobile preserves the
  `github: owner/repo` note property and renders bare or explicit `> [!github]`
  callouts as native, tappable repository cards without requiring GitHub login.

### Changed

### Fixed
## [0.3.0] - 2026-08-11

### Added

- Full-text Hall search across complete Markdown bodies with ranked snippets, source lines, quoted terms, and `schema:`, `tag:`, and `folder:` filters.
- Saved Hall searches and a tag browser built from frontmatter, inline body tags, and thread tags.
- Recently Deleted, backed by the local snapshots Skald already keeps, with one-tap restore to the original path.
- Editable templates for every schema with `{{title}}` and `{{date}}` placeholders, applied to new notes and daily pages.
- Long-press explorer selection with validated bulk Move and Delete actions, plus Copy links and Android sharing.

### Changed

- Note moves now validate the whole selection before touching disk, stage files as one operation, carry history forward, and rewrite links against the pre-move vault index.
- Inline tags outside code spans and fenced code blocks now join frontmatter tags in the vault index and editor vocabulary.

### Fixed

- Moving notes with ambiguous bare wikilinks now preserves their resolved destination instead of allowing a same-named note to steal the link.
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

[0.2.6]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.2.6

[0.3.0]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.3.0

[0.3.1]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.3.1

[0.3.2]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.3.2

[Unreleased]: https://github.com/vardirhq/skald-mobile/compare/v0.3.3...HEAD
[0.3.3]: https://github.com/vardirhq/skald-mobile/releases/tag/v0.3.3
