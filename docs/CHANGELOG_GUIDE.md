# Changelog discipline

Keep `CHANGELOG.md` useful to people installing the app, not as a dump of commit messages.

During normal development, every user-visible change belongs under `[Unreleased]` in one of `Added`, `Changed`, or `Fixed`. Internal refactors, formatting, dependency churn with no user impact, and CI-only changes generally do not need entries.

The release preparation workflow turns the current `[Unreleased]` content into a dated version section and creates a fresh empty `[Unreleased]` section. Release notes are extracted directly from that version section, so the changelog is the source of truth rather than GitHub's automatically generated commit list.
