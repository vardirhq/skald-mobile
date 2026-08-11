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
The GitHub extension requests only external-link access on Android because mobile currently does
not fetch repository data or authenticate.

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
