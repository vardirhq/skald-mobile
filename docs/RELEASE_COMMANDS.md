# Release command reference

```bash
# Show current public version
python tools/release.py version

# Verify version.properties and CHANGELOG.md agree
python tools/release.py check

# Verify them against a prospective tag
python tools/release.py check --tag v0.2.0

# Prepare the next patch release locally
python tools/release.py prepare patch

# Extract the current release notes
python tools/release.py notes
```

GitHub Actions should be the normal release path; these commands exist so the same checks are reproducible locally.
