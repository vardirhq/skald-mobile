# GitHub release setup

Before the first signed release:

1. Add the Android signing secrets listed in `docs/RELEASE_SECRETS.md` under repository **Settings → Secrets and variables → Actions**.
2. Under **Settings → Actions → General → Workflow permissions**, allow GitHub Actions to create pull requests if you want **Prepare release** to open its release PR automatically.
3. Keep the release keystore backed up offline.

No signing material is committed to the repository.
