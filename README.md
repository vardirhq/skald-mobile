# Skald Mobile

The phone half of [Skald](https://github.com/vardirhq/skald) — a local-first Markdown
knowledge base. Kotlin and Jetpack Compose, pairing to a desktop vault through
[GESH](https://github.com/vardirhq/generic-encrypted-sync-hub).

This is not a viewer. It holds a real vault of Markdown files, indexes them itself, and
is a full peer on a sync root: it can join one by scanning a code, or own one and hand
codes out.

> *Mobile is not a port of the desktop shell — it's a re-housing of the same tokens,
> runes, and data into a touch-native layout.*
> — the Skald spec, §10

## What it does

- **Setup, on first run** — name a vault, then choose how it starts: empty, joined to a
  vault you already have, or holding a sample vault to look around in. Empty and sample
  go on to offer sync straight away, so owning a root is one screen rather than a trip
  into Settings later. Joining creates the folder and redeems the pairing code *before*
  anything is written into it — a phone that joins has nothing of its own to publish over
  what is already on the other end.
- **A live editor** — the desktop's arrangement, re-housed for a thumb: the note stays
  rendered, and the one block your caret is in shows the Markdown it really is. Tap a
  block to write in it, tap a link to follow it instead. Enter continues a list, leaves
  it on an empty item, and stays a plain newline inside a fence. Above the keyboard sits
  a bar of marks — bold, italic, code, strike, links, wikilinks, bullets, numbers,
  checkboxes, quotes, headings, fences, rules — where every button is a toggle, because
  a phone has no ⌘B and finding the way back out from between two asterisks with a thumb
  is not a thing anyone should have to do. Read and source views are one tap away.
- **Typed notes** — every note carries a schema (`Note`, `Project`, `Person`, `Daily`,
  `Idea`, `Source`, `Code`, `Place`), from frontmatter or inferred from its folder. Each
  schema wears a monoline rune, drawn on the same 24-grid as the desktop's, that follows
  the note into every list, chip, tab and star.
- **Threads** — any `- [ ]` checkbox becomes a task in the global list, grouped by when
  it is due. Ticking it anywhere rewrites that line in its parent note, metadata intact:
  `@due(2026-06-01) @p(high) @status(working) #tag`.
- **Wikilinks and backlinks** — `[[Note]]`, `[[Folder/Note]]` and `[[Folder/Note.md]]`
  all resolve, folder-qualified beating bare, so two notes with the same file name stay
  apart. The editor shows what links back; renaming a note rewrites every link that
  pointed at it, in whichever form it was written.
- **The Logbook** — today's page in the saga: the date set large, honest counts, a week
  of activity, the threads due soonest, what you touched, a pinned note.
- **The Constellation** — the graph as a star chart. Positions are laid out once,
  persisted to `.skald/layout.json`, and clustered by folder. Pinch to zoom, drag to pan.
  A map you return to, not a simulation.
- **Skald's Hall** — full-screen fuzzy search over notes, threads and *cantos* (the
  commands), with the same weighted matcher the desktop uses.
- **Local note history** — a snapshot before every edit, external change, rename, delete
  and restore, kept in `.skald/history/`. Nothing sync overwrites is ever actually gone.
- **Encrypted sync** — see below.
- **Three surfaces** — Midnight, Slate and Daybreak; three densities; three marks.

## Sync, and the two halves of pairing

Skald Mobile is the "second client" [the desktop's sync doc](https://github.com/vardirhq/skald/blob/main/docs/sync.md)
describes bringing up, reimplemented in Kotlin rather than shared as source:

| Desktop (`src-shared/`) | Here (`core/`) |
| --- | --- |
| `gesh/bytes.ts` | `gesh/Bytes.kt` |
| `gesh/ids.ts` | `gesh/Ids.kt` |
| `gesh/crypto.ts` | `gesh/Crypto.kt` — `javax.crypto` instead of WebCrypto |
| `gesh/pairing.ts` | `gesh/Pairing.kt` |
| `gesh/protocol.ts` | `gesh/GeshClient.kt` — OkHttp behind an injectable transport |
| `sync/payload.ts` | `sync/Payload.kt` |
| `sync/merge.ts` | `sync/Merge.kt` |
| `src-main/sync.ts` | `sync/SyncEngine.kt` |

The wire format is identical, deliberately: AES-256-GCM with a fresh 96-bit nonce, the
nonce prepended; the `<JSON header>\n<raw body bytes>` envelope; per-path logical clocks
with the device id as tiebreak; `.skald/sync.json` in the same shape.

**GESH holds one half of the secret and never the other.** The relay knows a root and its
devices. The content key is generated on the first device and travels only inside the
fragment of a pairing URI:

```text
gesh://pair?s=https%3A%2F%2Fgesh.vardir.no&c=79T54-26AJX#k=<base64url AES-256 key>
```

A fragment is never transmitted to a server, so one QR code carries both halves while the
relay receives only the first. GESH returns a string rather than an image precisely so the
key can be appended first — `ui/components/QrCode.kt` draws it on the device.

Credentials live in `EncryptedSharedPreferences` under the Android keystore, never in the
vault folder, and are excluded from cloud backup and device transfer: a restored backup
would be the same GESH device id twice.

### Pairing this phone to a desktop vault

1. On the desktop: **Settings → Sync → Pair a device**.
2. Here: **Settings → Sync → Scan a pairing code**, or just point the system camera at
   it — the `gesh://pair` link opens the app directly.

On a phone that has not been set up yet, both routes land in setup instead, which pairs
before the vault holds anything. A `gesh://pair` link opening a fresh install asks only
for a vault name and then joins.

The phone can also own a root itself (**Create a sync root**), in which case it holds the
authority credential and is the only thing that can pair or revoke another device.

### One thing to verify against your relay

`GET /v1/sync/{appId}/{rootId}/{deviceId}/{eventId}` downloads an event *by its author*,
so pulling a peer's note is cross-device by necessity. The desktop client and its live
test both rely on this. On the current `main` of the GESH server, `get_event` is guarded
by the same `SyncScope` extractor as `put_event`, which rejects a device credential whose
id does not match the one in the path — so a strict reading of that code would 401 exactly
the request every peer has to make. This client matches the desktop's behaviour; if your
relay refuses peer downloads, that is where to look.

## Architecture

```text
core/     Plain Kotlin/JVM. No Android API anywhere, so it compiles and tests
          without an SDK — and could back a desktop or server build unchanged.
  model/      the vault's types
  text/       frontmatter, threads, wikilinks, schema inference, fuzzy, Markdown,
              the live editor's block rules and the formatting marks
  vault/      the indexer: raw files in, one VaultSnapshot out
  graph/      the stable constellation layout
  gesh/       bytes, ids, crypto, pairing, the v1 protocol client
  sync/       the event envelope, the merge rules, the engine

app/      Android and Compose.
  data/       FileVault (the nine sync operations plus history), keystore
              secrets, WorkManager scheduling, the repository
  ui/theme/   tokens.css, one for one
  ui/         runes, the Markdown renderer, the screens, the shell
```

Every screen reads one `VaultSnapshot`. Nothing else parses a note.

## Building

```bash
./gradlew :core:test        # 123 tests — domain, editing, crypto, protocol, merge, sync
./gradlew :app:assembleDebug
```

Needs JDK 17 or newer and an Android SDK with API 35. The modules target JVM 17 rather
than pinning a toolchain, so any recent JDK works.

## Status

`core` is complete and tested: 123 tests cover frontmatter, threads and their write-back,
wikilink resolution, the Markdown block rules, sealing and opening events, path validation
against hostile payloads, the live editor's block splitting and caret arithmetic, what Enter
does in each kind of block, the formatting toggles, the merge rules, and a full end-to-end
loop — two vaults pairing,
propagating, deleting, converging, preserving the losing edit, and surviving a restart —
against an in-memory relay that encodes the protocol as documented.

`app` is written but **has never been compiled**: it was developed in a sandbox whose
egress policy blocks `dl.google.com`, and AndroidX is published nowhere else, so neither
the Android SDK nor the Compose artifacts could be fetched. Everything risky was pushed
into `core` for that reason. Expect the first `assembleDebug` to want small fixes.

Not built yet, and deliberately: attachment import from the phone's picker (the vault
reads and syncs attachments, but nothing adds one from here), the slash menu, and drag to
reposition a star.
