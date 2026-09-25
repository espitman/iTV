package app.itv.prototype.core

object PlaybackRules {
    const val RESUME_THRESHOLD_MS = 10_000L
    const val NEAR_END_RESET_MS = 10_000L
    const val WATCHED_MIN_DURATION_MS = 30_000L
    const val NEXT_CARD_MS = 60 * 1000L
    const val NEXT_LOCK_MS = 10 * 1000L
    const val SAVE_INTERVAL_MS = 18_000L
    const val CONTROLS_HIDE_MS = 8_000L
    const val SEEK_DEBOUNCE_MS = 500L
    const val HOLD_FAST_MS = 2_000L
    const val STEP_NORMAL_MS = 10_000L
    const val STEP_HOLD_MS = 20_000L
    const val MEDIA_FORWARD_MS = 10_000L
    const val MEDIA_REWIND_MS = 10_000L
    const val RESUME_COUNTDOWN_SEC = 5
    const val NEXT_COUNTDOWN_SEC = 10

    val playbackSpeeds = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    fun shouldPromptResume(lastPositionMs: Long): Boolean = lastPositionMs > RESUME_THRESHOLD_MS

    fun watchedThreshold(durationMs: Long): Long =
        minOf(300_000L, (durationMs * 0.1).toLong())

    fun isWatched(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= WATCHED_MIN_DURATION_MS) return false
        return positionMs >= durationMs - watchedThreshold(durationMs)
    }

    fun persistablePosition(positionMs: Long, durationMs: Long): Long {
        if (durationMs > 0 && positionMs >= durationMs - NEAR_END_RESET_MS) return 0L
        return positionMs.coerceAtLeast(0L)
    }

    fun dpadSeekStep(heldMs: Long): Long =
        if (heldMs > HOLD_FAST_MS) STEP_HOLD_MS else STEP_NORMAL_MS

    fun nextPresent(current: LibraryEpisode, episodes: List<LibraryEpisode>): LibraryEpisode? =
        PresentationOrder.nextAfter(current, episodes)

    fun firstUnwatchedOrContinue(episodes: List<LibraryEpisode>): LibraryEpisode? {
        val present = PresentationOrder.present(episodes)
        present.lastOrNull { it.lastPositionMs > RESUME_THRESHOLD_MS && !it.isWatched }?.let { return it }
        val lastWatchedIndex = present.indexOfLast { it.isWatched }
        if (lastWatchedIndex >= 0) {
            present.getOrNull(lastWatchedIndex + 1)?.let { return it }
        }
        return present.firstOrNull { !it.isWatched } ?: present.firstOrNull()
    }

    fun seriesWatched(episodes: List<LibraryEpisode>): Boolean {
        val present = episodes.filter { it.sourcePresent }
        return present.isNotEmpty() && present.all { it.isWatched }
    }

    fun positiveMarker(seconds: Int): Int = if (seconds > 0) seconds else 0

    fun skipIntroActive(positionMs: Long, startSec: Int, endSec: Int): Boolean {
        val start = positiveMarker(startSec)
        val end = positiveMarker(endSec)
        if (start == 0 || end == 0 || end <= start) return false
        val pos = positionMs / 1000L
        return pos >= start && pos < end
    }

    fun skipOutroActive(positionMs: Long, lastStartSec: Int, durationMs: Long): Boolean {
        val start = positiveMarker(lastStartSec)
        if (start == 0 || durationMs <= 0) return false
        val pos = positionMs / 1000L
        return pos >= start && pos < durationMs / 1000L
    }

    fun skipIntroTargetMs(endSec: Int): Long = positiveMarker(endSec) * 1000L

    fun nextLockActive(hasNext: Boolean, cancelled: Boolean, durationMs: Long, positionMs: Long): Boolean {
        if (!hasNext || cancelled || durationMs <= 0L) return false
        return durationMs - positionMs < NEXT_LOCK_MS
    }

    fun shouldAutoAdvanceOnEnded(hasNext: Boolean, cancelled: Boolean): Boolean =
        hasNext && !cancelled

    fun acceptHlsUrl(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        val host = uri.host?.lowercase()?.trim('.').orEmpty()
        if (host != "telewebion.net" && !host.endsWith(".telewebion.net")) return null
        if (uri.userInfo != null) return null
        return value
    }
}

data class SeekBurst(
    val keyCode: Int,
    val downTime: Long,
    val originMs: Long,
    val targetMs: Long = originMs,
    val lastEventTimeMs: Long = downTime,
) {
    val movedMs: Long get() = targetMs - originMs

    fun continues(keyCode: Int, eventTimeMs: Long): Boolean =
        this.keyCode == keyCode && eventTimeMs >= lastEventTimeMs &&
            eventTimeMs - lastEventTimeMs <= 1_500L

    fun advance(deltaMs: Long, durationMs: Long, eventTimeMs: Long = lastEventTimeMs): SeekBurst {
        val lastPosition = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        return copy(targetMs = (targetMs + deltaMs).coerceIn(0L, lastPosition), lastEventTimeMs = eventTimeMs)
    }
}

data class NextCountdown(
    val remainingSec: Int = PlaybackRules.NEXT_COUNTDOWN_SEC,
    val started: Boolean = false,
    val cancelled: Boolean = false,
) {
    val shouldAutoPlay: Boolean
        get() = started && !cancelled && remainingSec <= 0

    fun onWindow(inLock: Boolean): NextCountdown {
        if (cancelled) return this
        return when {
            inLock && !started -> copy(started = true, remainingSec = PlaybackRules.NEXT_COUNTDOWN_SEC)
            !inLock && started -> copy(started = false, remainingSec = PlaybackRules.NEXT_COUNTDOWN_SEC)
            else -> this
        }
    }

    fun tick(): NextCountdown {
        if (cancelled || !started) return this
        return copy(remainingSec = (remainingSec - 1).coerceAtLeast(0))
    }

    fun cancel(): NextCountdown = copy(cancelled = true, started = false)

    fun reset(): NextCountdown = NextCountdown()
}

data class LibraryEpisode(
    val id: Long,
    val seriesId: Long,
    val seasonId: Long,
    val sourceEpisodeId: String,
    val sourceOrder: Int,
    val seasonNumber: Int,
    val displayNumber: String?,
    val title: String,
    val imageUrl: String?,
    val firstTitrajeStartSec: Int,
    val firstTitrajeEndSec: Int,
    val lastTitrajeStartSec: Int,
    val sourcePresent: Boolean,
    val lastPositionMs: Long,
    val isWatched: Boolean,
    val durationMs: Long,
) {
    fun label(): String {
        val number = displayNumber?.trim().orEmpty()
        return if (number.isNotEmpty()) "قسمت ${persianDigits(number)}" else title.ifBlank { "قسمت" }
    }
}

data class LibrarySeries(
    val id: Long,
    val kind: SourceKind,
    val sourceId: String,
    val title: String,
    val sourceTitle: String,
    val localTitle: String?,
    val posterUrl: String?,
    val description: String?,
    val addedAt: Long,
    val refreshedAt: Long,
    val importState: ImportState,
    val importError: String?,
    val seasons: List<LibrarySeason>,
    val episodes: List<LibraryEpisode>,
    val isMovie: Boolean = false,
    val durationMinutes: Int = 0,
    val backdropUrl: String? = null,
) {
    val presentEpisodes: List<LibraryEpisode>
        get() = PresentationOrder.present(episodes)

    val presentSeasonCount: Int
        get() = presentEpisodes.map { it.seasonNumber }.distinct().size

    val continueEpisode: LibraryEpisode?
        get() = PlaybackRules.firstUnwatchedOrContinue(episodes)
}

data class LibrarySeason(
    val id: Long,
    val seriesId: Long,
    val seasonNumber: Int,
    val sourceOrder: Int,
    val title: String?,
)
