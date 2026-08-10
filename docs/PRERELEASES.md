# Pre-releases

Custom release versions may use normal SemVer pre-release identifiers such as `0.3.0-beta.1` or `1.0.0-rc.1`. Every pre-release still consumes the next Android `versionCode`, because Android requires upgrade versions to increase monotonically.

The signed release workflow automatically treats a hyphenated SemVer version as a GitHub pre-release. A manually dispatched release can also explicitly request pre-release status.
