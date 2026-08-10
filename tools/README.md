# Release tools

`release.py` owns Skald Mobile release metadata.

```text
python tools/release.py version
python tools/release.py check [--tag vX.Y.Z]
python tools/release.py prepare <major|minor|patch|X.Y.Z>
python tools/release.py notes [--version X.Y.Z] [--out file]
```

`test_release.py` is the lightweight CI consistency test. `release-check.sh` additionally runs the core tests and Android debug build.
