# Release secrets

| Secret | Required | Purpose |
| --- | --- | --- |
| `SKALD_ANDROID_KEYSTORE_BASE64` | Yes | Base64-encoded JKS release keystore |
| `SKALD_ANDROID_STORE_PASSWORD` | Yes | Keystore password |
| `SKALD_ANDROID_KEY_PASSWORD` | Yes | Password for the signing key |
| `SKALD_ANDROID_KEY_ALIAS` | No | Signing key alias; defaults to `skald` |

Secrets are only consumed by the signed release workflow. Pull-request CI never receives or needs signing credentials.
