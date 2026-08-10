# First signed release

Before invoking the signed workflow for the first time, create the long-lived JKS key and configure the repository secrets described in `SIGNING.md`. The repository currently starts at `0.1.0` / `versionCode=1`; do not publish a differently signed `no.vardir.skald` APK and then expect it to upgrade cleanly to the CI-signed application later.

Once signing is configured, use the same Prepare release → merge → Android signed release flow as every later release.
