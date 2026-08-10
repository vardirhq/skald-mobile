# Release security boundaries

The Android signing key is never required for ordinary development, tests, debug APKs, or pull-request validation. Only the release job receives the GitHub Actions signing secrets, and the keystore exists only in the runner's temporary directory during that job.

Do not expose signing secrets as Gradle properties committed to the repository, workflow outputs, artifacts, logs, or pull-request jobs.
