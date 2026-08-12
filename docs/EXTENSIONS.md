# Built-in extensions on Android

Skald Mobile shares the portable extension contract introduced by desktop Skald. GitHub is the
first built-in extension and owns the `github` note property plus the `[!github]` Markdown
component. Both clients use the same reverse-domain id and semantic version.

The Android core defines manifests, per-platform capabilities, descriptors, validation, and
case-insensitive component lookup in `core/.../extensions/Extensions.kt`. Compose renderers are
registered separately under `app/.../ui/extensions/`, so the pure core never depends on Android
UI code. `MarkdownView` asks the registry for a renderer; it does not know about GitHub.

Unknown components still use the ordinary callout renderer. Their source is retained byte for
byte, which lets newer desktop components pass safely through an older mobile installation.

## Security boundary

This is a registry for trusted code shipped with the application, not a loader for downloaded
Kotlin, DEX, JavaScript, or WebView plugins. Each manifest declares capabilities per platform.
The GitHub extension requests network, authentication, secure-storage, settings, and external-link
capabilities on Android. Its Compose renderer fetches public repository data anonymously, keeps a
ten-minute ETag cache for offline rendering, and can use GitHub Device Flow for private repositories.
Skald brokers those operations: the renderer never receives an access token, tokens are accepted
only when Android Keystore-backed storage is available, and private responses remain in memory.

Third-party packages would require signing, explicit capability grants, compatibility checks,
revocation, and isolation before Skald could safely load them. Until then, new extensions are
reviewed and compiled as part of Skald Mobile.

## Adding a renderer

1. Add or mirror the extension manifest and descriptor in core.
2. Declare the exact component types and note properties it owns.
3. Register matching Compose renderers. Startup validation fails when declared and supplied
   components differ or when ids, properties, or component types collide.
4. Keep an honest non-network fallback and test descriptor lookup and normalization.
5. Update desktop and mobile together before either client starts creating new component syntax.

## GitHub build configuration

Public cards require no configuration. Private access uses the same GitHub App contract as desktop.
Enable Device Flow and provide the app's public identifiers while building:

```bash
SKALD_GITHUB_CLIENT_ID=Iv1.example \
SKALD_GITHUB_APP_SLUG=skald-desktop \
./gradlew :app:assembleDebug
```

These values identify the GitHub App and are not secrets. Never package a GitHub client secret.
