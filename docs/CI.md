# CI

Pull requests and pushes to main run unsigned debug builds. Signing credentials are intentionally unavailable there.

Only the tag/manual **Android signed release** workflow receives the release keystore secrets. This keeps untrusted pull-request code from accessing the application signing identity while still giving every change a compile/test gate before merge.
