package app.itv.prototype.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.itv.prototype.ItvApplication
import app.itv.prototype.Pictures
import app.itv.prototype.R
import app.itv.prototype.core.LibraryEpisode
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.PlaybackRules
import app.itv.prototype.core.persianClock
import app.itv.prototype.core.persianDigits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SeriesActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var seriesId = 0L
    private var selectedSeason: Int? = null
    private var initialFocusApplied = false
    private var focusedSeason: Int? = null
    private var focusedEpisodeId: String? = null
    private var focusOnPlay = false
    private var focusOnBack = false
    private lateinit var title: TextView
    private lateinit var kind: TextView
    private lateinit var meta: TextView
    private lateinit var description: TextView
    private lateinit var play: Button
    private lateinit var back: Button
    private lateinit var poster: ImageView
    private lateinit var seasons: LinearLayout
    private lateinit var episodes: LinearLayout
    private lateinit var seasonScroller: HorizontalScrollView
    private lateinit var episodeScroller: HorizontalScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)
        seriesId = intent.getLongExtra(EXTRA_ID, 0L)
        title = findViewById(R.id.title)
        kind = findViewById(R.id.kind)
        meta = findViewById(R.id.meta)
        description = findViewById(R.id.description)
        play = findViewById(R.id.play)
        back = findViewById(R.id.navHome)
        poster = findViewById(R.id.poster)
        seasons = findViewById(R.id.seasons)
        episodes = findViewById(R.id.episodes)
        seasonScroller = findViewById(R.id.seasonScroller)
        episodeScroller = findViewById(R.id.episodeScroller)
        seasonScroller.disableSelfFocus()
        episodeScroller.disableSelfFocus()
        play.bindFocusTint()
        back.bindFocusTint()
        play.ensureFocusId()
        back.ensureFocusId()
        back.nextFocusDownId = play.id
        play.nextFocusUpId = back.id
        play.setOnFocusChangeListener { view, focused ->
            val button = view as Button
            button.setTextColor(getColor(R.color.bg))
            button.animate().scaleX(1f).scaleY(1f)
                .setDuration(110).start()
            if (focused) {
                focusOnPlay = true
                focusOnBack = false
                focusedEpisodeId = null
            }
        }
        back.setOnFocusChangeListener { view, focused ->
            val button = view as Button
            button.setTextColor(getColor(R.color.text))
            if (focused) {
                focusOnBack = true
                focusOnPlay = false
            }
        }
        back.setOnClickListener { finish() }
        findViewById<Button>(R.id.settings).apply { bindFocusTint(); setOnClickListener { startActivity(Intent(this@SeriesActivity, SettingsActivity::class.java)) } }
        findViewById<Button>(R.id.showEpisodes).apply {
            bindFocusTint()
            setOnClickListener { episodes.getChildAt(0)?.requestFocus() }
        }
        poster.clipRound(14f)
        if (seriesId == 0L) finish()
    }

    override fun onStart() {
        super.onStart()
        observeJob?.cancel()
        observeJob = scope.launch {
            ItvApplication.instance.repository.observeSeries(seriesId).collect { series ->
                if (series == null) finish() else bind(series)
            }
        }
    }

    override fun onStop() {
        observeJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bind(series: LibrarySeries) {
        rememberSeriesFocus()
        title.text = series.title
        kind.text = if (series.isMovie) "فیلم سینمایی" else if (series.presentSeasonCount > 1) "سریال چندفصلی" else "سریال"
        meta.text = if (series.isMovie) "فیلم سینمایی · ${persianDigits(series.durationMinutes)} دقیقه" else getString(
            R.string.season_episode_meta,
            getString(R.string.season_count, persianDigits(series.presentSeasonCount)),
            getString(R.string.episode_count, persianDigits(series.presentEpisodes.size)),
        )
        description.maxLines = if (series.isMovie) 4 else 2
        findViewById<View>(R.id.episodeSection).visibility = if (series.isMovie) View.GONE else View.VISIBLE
        findViewById<View>(R.id.showEpisodes).visibility = if (series.isMovie) View.GONE else View.VISIBLE
        findViewById<View>(R.id.seriesHero).layoutParams = findViewById<View>(R.id.seriesHero).layoutParams.apply { height = ((if (series.isMovie) 430 else 246) * resources.displayMetrics.density).toInt() }
        description.text = series.description.orEmpty()
        description.visibility = if (series.description.isNullOrBlank()) View.GONE else View.VISIBLE
        Pictures.load(series.posterUrl, poster, R.drawable.bg_poster)
        Pictures.load(series.backdropUrl ?: series.presentEpisodes.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl ?: series.posterUrl, findViewById(R.id.backdrop))
        val continueEp = series.continueEpisode
        play.isEnabled = continueEp != null
        play.text = when {
            continueEp == null -> getString(R.string.play_first)
            continueEp.lastPositionMs > PlaybackRules.RESUME_THRESHOLD_MS -> getString(R.string.continue_watch)
            series.isMovie -> "پخش فیلم"
            else -> getString(R.string.play_episode, continueEp.label())
        }
        play.setOnClickListener { continueEp?.let(::openPlayer) }
        findViewById<View>(R.id.episodesHeading).visibility = if (series.isMovie) View.GONE else View.VISIBLE
        episodeScroller.visibility = if (series.isMovie) View.GONE else View.VISIBLE
        val seasonNumbers = series.presentEpisodes.map { it.seasonNumber }.distinct()
        val multi = seasonNumbers.size > 1
        seasonScroller.visibility = if (multi) View.VISIBLE else View.GONE
        if (!multi) selectedSeason = seasonNumbers.singleOrNull()
        if (selectedSeason == null || selectedSeason !in seasonNumbers) {
            selectedSeason = continueEp?.seasonNumber ?: seasonNumbers.firstOrNull()
        }
        val seasonViews = bindSeasons(seasonNumbers, series)
        val visibleEpisodes = series.presentEpisodes.filter { selectedSeason == null || it.seasonNumber == selectedSeason }
        val episodeViews = bindEpisodes(if (series.isMovie) emptyList() else visibleEpisodes)
        wireSeriesFocus(seasonViews, episodeViews)
        restoreSeriesFocus(seasonViews, episodeViews)
    }

    private fun rememberSeriesFocus() {
        if (!initialFocusApplied) return
        when (val focused = currentFocus) {
            play -> {
                focusOnPlay = true
                focusOnBack = false
            }
            back -> {
                if (focusedEpisodeId == null && focusedSeason == null) {
                    focusOnBack = true
                    focusOnPlay = false
                }
            }
            else -> {
                val seasonTag = focused?.getTag(R.id.seasons) as? Int
                val episodeTag = focused?.getTag(R.id.episodes) as? String
                if (seasonTag != null) focusedSeason = seasonTag
                if (episodeTag != null) focusedEpisodeId = episodeTag
            }
        }
    }

    private fun bindSeasons(numbers: List<Int>, series: LibrarySeries): List<View> {
        seasons.removeAllViews()
        if (seasonScroller.visibility != View.VISIBLE) return emptyList()
        return numbers.map { number ->
            val chip = Button(this)
            chip.ensureFocusId()
            chip.tag = number
            chip.setTag(R.id.seasons, number)
            chip.text = getString(R.string.season_label, persianDigits(number))
            chip.isSelected = number == selectedSeason
            chip.background = getDrawable(R.drawable.bg_chip)
            chip.setTextColor(getColor(if (chip.isFocused) R.color.bg else R.color.text))
            chip.isFocusable = true
            chip.isFocusableInTouchMode = true
            chip.isClickable = true
            chip.textSize = 10f
            chip.minWidth = 0
            chip.minimumWidth = 0
            chip.minHeight = 0
            chip.minimumHeight = 0
            chip.setPadding(12, 0, 12, 0)
            chip.setOnFocusChangeListener { view, focused ->
                (view as Button).setTextColor(getColor(if (focused) R.color.bg else R.color.text))
                if (focused) {
                    focusedEpisodeId = null
                    focusedSeason = number
                    focusOnPlay = false
                    focusOnBack = false
                }
            }
            chip.setOnClickListener {
                selectedSeason = number
                focusedSeason = number
                focusOnPlay = false
                focusOnBack = false
                focusedEpisodeId = null
                bind(series)
            }
            val params = LinearLayout.LayoutParams((44 * resources.displayMetrics.density).toInt(), (28 * resources.displayMetrics.density).toInt())
            params.marginEnd = (10 * resources.displayMetrics.density).toInt()
            seasons.addView(chip, params)
            chip
        }
    }

    private fun bindEpisodes(items: List<LibraryEpisode>): List<View> {
        episodes.removeAllViews()
        return items.map { episode ->
            val card = layoutInflater.inflate(R.layout.item_episode, episodes, false)
            card.ensureFocusId()
            card.tag = episode.sourceEpisodeId
            card.setTag(R.id.episodes, episode.sourceEpisodeId)
            val thumb = card.findViewById<ImageView>(R.id.thumb)
            thumb.clipRound(5f)
            Pictures.load(episode.imageUrl, thumb, R.drawable.bg_poster)
            card.findViewById<TextView>(R.id.title).text = episode.label()
            card.findViewById<TextView>(R.id.episodeNumber).text = persianDigits((episode.displayNumber ?: (episode.sourceOrder + 1).toString()).padStart(2, '0'))
            card.findViewById<TextView>(R.id.state).text =
                if (episode.durationMs > 0L) persianClock(episode.durationMs) else ""
            card.findViewById<View>(R.id.watchedBadge).visibility = if (episode.isWatched) View.VISIBLE else View.GONE
            val progress = card.findViewById<View>(R.id.progress)
            if (episode.durationMs > 0 && episode.lastPositionMs > 0 && !episode.isWatched) {
                progress.visibility = View.VISIBLE
                val width = (200f * (episode.lastPositionMs.toFloat() / episode.durationMs)).toInt().coerceIn(2, 201)
                progress.layoutParams = progress.layoutParams.apply { this.width = (width * resources.displayMetrics.density).toInt() }
            }
            card.bindCardFocus()
            card.setOnFocusChangeListener { view, focused ->
                view.animate().scaleX(1f).scaleY(1f)
                    .setDuration(120).start()
                view.elevation = if (focused) 18f else 0f
                if (focused) {
                    focusedSeason = null
                    focusedEpisodeId = episode.sourceEpisodeId
                    focusOnPlay = false
                    focusOnBack = false
                    view.requestRectangleOnScreen(android.graphics.Rect(), false)
                }
            }
            card.setOnClickListener { openPlayer(episode) }
            card.setOnLongClickListener {
                scope.launch { ItvApplication.instance.repository.setWatched(episode.id, !episode.isWatched) }
                true
            }
            episodes.addView(card)
            card
        }
    }

    private fun wireSeriesFocus(seasonViews: List<View>, episodeViews: List<View>) {
        wireRtlRow(seasonViews)
        wireRtlRow(episodeViews)
        val firstSeason = seasonViews.firstOrNull { it.isSelected } ?: seasonViews.firstOrNull()
        val firstEpisode = episodeViews.firstOrNull()
        val downFromPlay = firstSeason ?: firstEpisode
        play.nextFocusDownId = downFromPlay?.id ?: View.NO_ID
        play.nextFocusUpId = back.id
        play.nextFocusLeftId = findViewById<View>(R.id.showEpisodes).takeIf { it.isShown }?.id ?: play.id
        play.nextFocusRightId = View.NO_ID
        back.nextFocusDownId = play.id
        findViewById<View>(R.id.settings).nextFocusDownId = play.id
        findViewById<View>(R.id.showEpisodes).apply { nextFocusRightId = play.id; nextFocusDownId = downFromPlay?.id ?: play.id; nextFocusUpId = back.id }
        seasonViews.forEach { chip ->
            chip.nextFocusUpId = play.id
            chip.nextFocusDownId = firstEpisode?.id ?: View.NO_ID
        }
        episodeViews.forEach { card ->
            card.nextFocusUpId = firstSeason?.id ?: play.id
        }
        firstEpisode?.nextFocusUpId = firstSeason?.id ?: play.id
        if (focusedEpisodeId == null) scrollRowToStart(episodeScroller)
        if (focusedSeason == null) scrollRowToStart(seasonScroller)
    }

    private fun scrollRowToStart(scroller: HorizontalScrollView) {
        if (scroller.visibility != View.VISIBLE) return
        scroller.post {
            val content = scroller.getChildAt(0) ?: return@post
            if (content.width == 0 || scroller.width == 0) {
                return@post
            }
            val start = if (scroller.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                (content.width - scroller.width).coerceAtLeast(0)
            } else 0
            scroller.scrollTo(start, 0)
        }
    }

    private fun restoreSeriesFocus(seasonViews: List<View>, episodeViews: List<View>) {
        val seasonTarget = focusedSeason?.let { number -> seasonViews.firstOrNull { it.getTag(R.id.seasons) == number } }
        val episodeTarget = focusedEpisodeId?.let { id -> episodeViews.firstOrNull { it.getTag(R.id.episodes) == id } }
        val target = when {
            !initialFocusApplied -> play
            seasonTarget != null && !focusOnPlay && !focusOnBack -> seasonTarget
            episodeTarget != null && !focusOnPlay && !focusOnBack -> episodeTarget
            focusOnBack && focusedEpisodeId == null && focusedSeason == null -> back
            focusOnPlay -> play
            episodeTarget != null -> episodeTarget
            seasonTarget != null -> seasonTarget
            else -> play
        }
        target.post { target.requestFocus() }
        initialFocusApplied = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !initialFocusApplied) play.post { play.requestFocus() }
    }

    private fun openPlayer(episode: LibraryEpisode) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_EPISODE_ID, episode.id)
                .putExtra(PlayerActivity.EXTRA_SERIES_ID, episode.seriesId),
        )
    }

    companion object {
        const val EXTRA_ID = "series_id"
    }
}
