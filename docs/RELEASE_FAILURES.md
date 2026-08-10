# Release failure behavior

A release is not published when metadata, tests, compilation, artifact discovery, APK signature verification, or AAB signature verification fails. The Git tag may already exist when a build-stage failure occurs; fix the underlying release commit through the appropriate release/hotfix process rather than silently replacing binaries from unrelated source.

The signing keystore is reconstructed under the ephemeral GitHub runner temp directory and disappears with the runner.
