# Android signing key

Skald Mobile releases must always use the same signing identity for application ID `no.vardir.skald`.

A suitable initial key can be created locally with:

```bash
keytool -genkeypair -v \
  -keystore skald-release.jks \
  -alias skald \
  -keyalg RSA -keysize 4096 -validity 10000
```

Encode it for the GitHub repository secret with:

```bash
base64 -w 0 skald-release.jks
```

On macOS, use `base64 < skald-release.jks | tr -d '\n'`.

Store that output as `SKALD_ANDROID_KEYSTORE_BASE64`, and store the passwords as `SKALD_ANDROID_STORE_PASSWORD` and `SKALD_ANDROID_KEY_PASSWORD`. If a different alias is chosen, store it as `SKALD_ANDROID_KEY_ALIAS`.

Do not commit the JKS file. Keep at least one secure offline backup of the keystore and its credentials. Losing the signing key prevents existing installations from accepting future updates signed by a replacement key.
