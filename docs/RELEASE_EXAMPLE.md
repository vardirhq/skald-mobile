# Example release

Suppose main is at `0.1.0`, `versionCode=1`, and `[Unreleased]` contains the changes for the next release.

Running **Prepare release → minor** produces a PR with:

```properties
VERSION_NAME=0.2.0
VERSION_CODE=2
```

and moves the unreleased changelog entries under `## [0.2.0] - YYYY-MM-DD`.

After that PR merges, **Android signed release** with an empty tag creates `v0.2.0` and publishes `Skald-Mobile-0.2.0.apk`, `Skald-Mobile-0.2.0.aab`, and `SHA256SUMS.txt`.
