# Working in this repo

Skald Mobile — Kotlin and Jetpack Compose, a local-first Markdown vault that syncs
through GESH. `README.md` says what the app does and how sync works; this file is
about how to change it.

## Before you call a change done

**Add a `CHANGELOG.md` entry for anything a user would notice**, under `[Unreleased]`
in `Added`, `Changed` or `Fixed`. Do it in the same commit as the change — the
release tooling cuts `[Unreleased]` straight into the dated release section, and
release notes are extracted from there, so an entry written later is an entry that
missed its release. Write it for someone installing the app, not as a commit
message: what changed for them, not which composable moved.

Skip the entry for internal refactors, formatting, dependency churn and CI-only
work. `docs/CHANGELOG_GUIDE.md` is the full rule.

Leave the three `### Added` / `### Changed` / `### Fixed` headings in place even when
empty — `tools/release.py` copies the section verbatim and expects that shape.

## Building and testing

```bash
./gradlew :core:test          # 155 tests, no Android SDK needed
./gradlew :app:assembleDebug  # what CI gates on
python tools/test_release.py  # release metadata, also gated in CI
```

Needs JDK 17+ and an Android SDK with API 35. `core` is plain Kotlin/JVM and compiles
without one; `app` does not. In a sandbox with no SDK, install it rather than shipping
Compose changes unverified — `sdkmanager "platforms;android-35" "build-tools;35.0.0"`
into a local `ANDROID_HOME`, with `sdk.dir` in `local.properties` (gitignored). There
is no Compose UI or screenshot test infrastructure, so layout changes are compile-
checked only; say so rather than implying they were seen running.

## Where things live

```text
core/   the domain, pure Kotlin/JVM — no Android API anywhere, which is why it holds
        everything risky: parsing, editing rules, crypto, protocol, merge, sync
app/    Android and Compose — data/, ui/theme/, ui/ (runes, sheets, screens, shell)
```

Every screen reads one `VaultSnapshot`, and nothing outside `core/vault` parses a
note. Logic worth testing belongs in `core`, where it can be tested without a device.

## Conventions that are easy to break

- **Tokens, not colours.** `ui/theme/Tokens.kt` mirrors the desktop's `styles/tokens.css`
  one for one, deliberately, so the two builds never drift. Use `Skald.colors`,
  `Skald.type` and `Skald.metrics`; do not introduce a literal `Color(0xFF…)` in UI code
  or rename a token.
- **Touch targets clear 44dp.** `IconButtonSlot` is the reference. Anything tappable
  that a thumb has to find — sheet buttons, row affordances — matches it.
- **The bottom edge is where a thumb lives.** Sheets commit through `SkaldSheet`'s
  `actions` slot, which stays pinned below the scrolling fields; do not put a commit
  button at the end of a scrolling column.
- **Versions come from `version.properties`.** It is the single source of truth for
  `versionName` and `versionCode`. Never hand-edit them elsewhere, and let the release
  tooling bump them — see `docs/RELEASING.md`.
- **Comments in the house voice.** The existing ones explain why a thing is the way it
  is, in prose, often with the phone as the reason. Match that density and register
  rather than annotating the obvious.

## Git

Work on a branch and push there; never push to `main`. Open a PR only when asked.
