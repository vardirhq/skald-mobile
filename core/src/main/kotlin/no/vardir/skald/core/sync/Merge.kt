package no.vardir.skald.core.sync

import kotlinx.serialization.Serializable

/**
 * Conflict resolution. GESH orders events but never merges them, so this is
 * entirely Skald's problem — and it is a pure function of three things: what a
 * remote device says, what this device last agreed on, and what is on disk now.
 *
 * The rule is last-writer-wins on a per-path logical clock, with the device id
 * breaking ties so every device reaches the same answer without talking to the
 * others. What matters for a notes app is what happens to the loser: nothing is
 * silently dropped. A local edit that loses is captured into the note's history
 * first, so it stays one tap away rather than gone.
 */
object Merge {

    /** The hash of a path that does not exist. Absence is a state, with a clock. */
    const val ABSENT = ""

    /** The last state this device agreed on for a path — either published or applied. */
    @Serializable
    data class FileState(
        /** SHA-256 of the content, or [ABSENT] for a tombstone. */
        val hash: String,
        /** Per-path logical clock. Only ever compared, never interpreted. */
        val rev: Long,
        /** Device that wrote this revision; the tiebreak at equal revisions. */
        val writer: String,
    )

    enum class Action {
        /** The filesystem already agrees; do not touch it. */
        Noop,

        /** Write or delete locally. */
        Apply,

        /** The local copy wins; ignore the incoming op and let the next push carry it. */
        KeepLocal,
    }

    data class Result(
        val action: Action,
        /** True when applying is about to overwrite an unpublished local edit. */
        val preserveLocal: Boolean,
        /** New bookkeeping state for the path, or null to leave it as it was. */
        val record: FileState?,
    )

    /** Higher revision wins; equal revisions break by device id, identically everywhere. */
    fun beats(aRev: Long, aWriter: String, bRev: Long, bWriter: String): Boolean =
        if (aRev != bRev) aRev > bRev else aWriter > bWriter

    private fun beats(a: FileState, b: FileState): Boolean = beats(a.rev, a.writer, b.rev, b.writer)

    /** The revision this device should publish for a path it has just changed. */
    fun nextRev(known: FileState?): Long = (known?.rev ?: 0L) + 1

    fun decide(
        incoming: Payload.FileOp,
        /** The device that authored the revision — not necessarily the event's sender. */
        incomingWriter: String,
        /** Last synced state for this path, or null if this device has never seen it. */
        known: FileState?,
        /** Hash of the file on disk right now, or [ABSENT] when it is not there. */
        localHash: String,
        /** This device's own id, used as the tiebreak against the incoming writer. */
        localDeviceId: String,
    ): Result {
        // A note and an attachment differ only in how their bytes travelled; the
        // clock, the tiebreak and the meaning of absence are identical.
        val incomingHash = when (incoming) {
            is Payload.Delete -> ABSENT
            is Payload.Put -> incoming.hash
            is Payload.PutBin -> incoming.hash
        }
        val incomingState = FileState(incomingHash, incoming.rev, incomingWriter)
        val knownHash = known?.hash ?: ABSENT

        // The filesystem already says what the incoming op says. Whether that
        // happened by sync or by two people typing the same thing, there is
        // nothing to write — but the clock may still need to move forward.
        if (localHash == incomingHash) {
            return Result(
                action = Action.Noop,
                preserveLocal = false,
                record = if (known == null || beats(incomingState, known)) incomingState else null,
            )
        }

        // Does the local copy carry an edit this device has not published yet?
        if (localHash == knownHash) {
            // No. The only reason not to apply is that the op is older than what
            // we already agreed on — a replay, or a page re-read after a partial ack.
            if (known != null && !beats(incomingState, known)) {
                return Result(Action.Noop, preserveLocal = false, record = null)
            }
            return Result(Action.Apply, preserveLocal = false, record = incomingState)
        }

        // Yes. That edit will be published by this device at the next revision,
        // so that is the claim the incoming op has to beat.
        val localClaimRev = nextRev(known)
        return if (beats(incomingState.rev, incomingState.writer, localClaimRev, localDeviceId)) {
            Result(Action.Apply, preserveLocal = true, record = incomingState)
        } else {
            Result(Action.KeepLocal, preserveLocal = false, record = null)
        }
    }
}
