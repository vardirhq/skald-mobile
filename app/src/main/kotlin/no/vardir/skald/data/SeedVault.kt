package no.vardir.skald.data

import no.vardir.skald.core.model.NoteHistoryReason

/**
 * A first-run vault, written as real Markdown files rather than mock data —
 * so everything the screens show is something the indexer actually derived,
 * and deleting a note actually deletes it.
 */
object SeedVault {

    fun write(vault: FileVault, todayIso: String) {
        for ((path, content) in notes(todayIso)) {
            vault.write(path, content, NoteHistoryReason.External)
        }
    }

    private fun notes(today: String): List<Pair<String, String>> = listOf(
        "Notes/Skald design rationale.md" to """
            ---
            title: Skald design rationale
            schema: Note
            tags: [design, vision, editor]
            created: 2026-05-12
            ---

            # Notes that link themselves.

            The hardest part of building a knowledge tool is resisting the urge to make it a
            database. Skald takes the opposite bet: the connections *are* the product, so the
            editor surfaces them as you write.

            > [!Premise] The graph is not the point. The writing is — the graph is just what
            > writing leaves behind.

            ## Typed notes, not folders

            Every note has a schema — `Note`, `Project`, `Idea`. The schema draws a small
            [[On the use of runes|rune]] that follows it everywhere it is mentioned, so you
            scan by shape rather than by reading every title.

            ## Tasks live in the text

            An inline checkbox is a thread — it exists in the note and in the global list, and
            an edit in either propagates to the other.

            - [ ] Draft schema for the Saga entity @due(2026-05-31) #schema
            - [ ] Spike: typed frontmatter forms @status(working) #schema #ui
            - [ ] Try Plex Mono vs JetBrains for code blocks @p(low) #type

            See also [[Stack decisions, 2026]] and [[Editor as instrument]].

            > Let the links between your thoughts be the thing that endures.
        """.trimIndent(),

        "Notes/Stack decisions, 2026.md" to """
            ---
            title: Stack decisions, 2026
            schema: Note
            tags: [stack, infra]
            created: 2026-05-02
            ---

            # What we build on, and why

            Every dependency is a promise to maintain something you did not write. These are
            the ones worth making.

            ## Storage

            Markdown on disk, always. The index is derived and disposable — delete `.skald/`
            and nothing of yours is lost. See [[Skald design rationale]].

            ## Sync

            [[CRDTs in collaboration]] is the wrong shape for a file-per-note vault. Skald
            resolves per path instead, last-writer-wins on a logical clock, and keeps the
            loser in the note's history.

            - [ ] Decide between Tantivy-WASM and Meilisearch lite @due(2026-06-02) #search
            - [x] Refactor the folder watcher @due(2026-05-24) #infra
        """.trimIndent(),

        "Notes/Editor as instrument.md" to """
            ---
            title: Editor as instrument
            schema: Idea
            tags: [editor]
            created: 2026-04-28
            ---

            # The editor is an instrument, not a form

            A form collects. An instrument is played. The difference shows up in the second
            hour, not the first.

            Wikilink autocomplete, inline threads, and typed frontmatter rendered as a carved
            tablet rather than YAML — all of it exists so the writing never stops to become
            data entry. Related: [[Skald design rationale]], [[On the use of runes]].

            - [ ] Backlinks should include inline mentions, not just [[...]] @due(2026-06-05) #editor
        """.trimIndent(),

        "Notes/On the use of runes.md" to """
            ---
            title: On the use of runes
            schema: Idea
            tags: [design, type]
            created: 2026-05-05
            ---

            # A schema you can see from across the room

            A coloured dot tells you there is a category. A rune tells you *which*. Each Skald
            schema carries a monoline mark drawn on the same 24-grid, so a list of notes reads
            as a rhythm rather than as a wall of titles.

            | Schema | Rune | Why |
            | --- | --- | --- |

            - Note — nauthiz, a stave crossed once
            - Project — a pennant, a thing in motion
            - Person — raidho, a character on the road
            - Daily — tiwaz, pointing up at this day

            Drawn from [[Old Norse poetic meter]].

            - [ ] Write a field guide to the runic schema markers @p(low) #doc
        """.trimIndent(),

        "Projects/Jörmungandr API rewrite.md" to """
            ---
            title: Jörmungandr API rewrite
            schema: Project
            tags: [api, sync]
            created: 2026-04-19
            ---

            # Jörmungandr API rewrite

            The serpent that circles the world, and eats its own tail — which is roughly what
            the old API did to itself every release.

            ## Open threads

            - [ ] Replace the editor core for live collaboration @due(2026-05-29) @p(high) @status(working) #editor
            - [ ] Wire awareness into the new presence layer @due(2026-05-30) #sync

            Depends on [[Stack decisions, 2026]] and blocks [[Yggdrasil graph layout]].
        """.trimIndent(),

        "Projects/Yggdrasil graph layout.md" to """
            ---
            title: Yggdrasil graph layout
            schema: Project
            tags: [graph]
            created: 2026-04-22
            ---

            # A map you return to

            Positions are authored, not simulated. A layout that re-renders on every open is
            not a map — it is a screensaver.

            - [ ] Rebuild the constellation layout @due(2026-05-28) @p(high) @status(working) #graph

            Talks to [[Stack decisions, 2026]], [[CRDTs in collaboration]] and [[Christoffer]].
        """.trimIndent(),

        "Projects/Mjölnir auth migration.md" to """
            ---
            title: Mjölnir auth migration
            schema: Project
            tags: [auth]
            created: 2026-03-30
            ---

            # Mjölnir auth migration

            Blocked on a decision nobody wants to make.

            - [ ] Migrate auth off the old session layer @due(2026-05-22) @p(high) @status(blocked) #auth

            Owner: [[Christoffer]]. Context in [[Stack decisions, 2026]].
        """.trimIndent(),

        "People/Christoffer.md" to """
            ---
            title: Christoffer
            schema: Person
            created: 2026-02-11
            ---

            # Christoffer

            Works on [[Yggdrasil graph layout]] and [[Mjölnir auth migration]].

            - [x] Send Christoffer the new icon set @due(2026-05-26) @p(low) #chore
        """.trimIndent(),

        "People/Ada.md" to """
            ---
            title: Ada
            schema: Person
            created: 2026-02-11
            ---

            # Ada

            Sharpest reader of [[Jörmungandr API rewrite]] we have.
        """.trimIndent(),

        "References/Old Norse poetic meter.md" to """
            ---
            title: Old Norse poetic meter
            schema: Source
            tags: [norse]
            created: 2026-01-08
            ---

            # Old Norse poetic meter

            *Fornyrðislag* — "old story metre." Four stressed syllables to the long line,
            split by a caesura, bound by alliteration rather than rhyme.

            The relevant lesson for [[On the use of runes]]: the constraint is what makes it
            memorable.
        """.trimIndent(),

        "References/CRDTs in collaboration.md" to """
            ---
            title: CRDTs in collaboration
            schema: Source
            tags: [sync]
            created: 2026-03-02
            ---

            # CRDTs in collaboration

            Excellent for one document with many cursors. Expensive for a vault of a thousand
            files where two devices are rarely awake at once.

            Read alongside [[Stack decisions, 2026]] and [[Yggdrasil graph layout]].
        """.trimIndent(),

        "Daily/$today.md" to """
            ---
            schema: Daily
            created: $today
            ---

            # $today

            Today's page in the saga. Anything you write here is just a note — the date in the
            title is what makes it a Daily.

            - [ ] Read back yesterday's threads
        """.trimIndent(),
    )
}
