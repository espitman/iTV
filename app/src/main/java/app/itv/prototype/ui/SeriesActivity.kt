package app.itv.prototype.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.itv.prototype.ItvApplication
import app.itv.prototype.Pictures
import app.itv.prototype.R
import app.itv.prototype.core.LibraryEpisode
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.MovieCreditPerson
import app.itv.prototype.core.SeriesDetail
import app.itv.prototype.core.SourceKind
import app.itv.prototype.core.persianClock
import app.itv.prototype.core.persianDigits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeriesActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var seriesId = 0L
    private var selectedSeason: Int? = null
    private var initialFocusApplied = false
    private var focusedSeason: Int? = null
    private var focusedEpisodeId: String? = null
    private var focusOnPlay = false
    private var creditsFor: String? = null
    private var creditJob: Job? = null
    private var focusedCreditIndex: Int? = null
    private lateinit var title: TextView
    private lateinit var kind: TextView
    private lateinit var meta: TextView
    private lateinit var description: TextView
    private lateinit var play: Button
    private lateinit var poster: ImageView
    private lateinit var seasons: LinearLayout
    private lateinit var episodes: LinearLayout
    private lateinit var seasonScroller: RtlShelfScrollView
    private lateinit var episodeScroller: RtlShelfScrollView
    private lateinit var creditSection: View
    private lateinit var creditScroller: RtlShelfScrollView
    private lateinit var creditRows: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)
        seriesId = intent.getLongExtra(EXTRA_ID, 0L)
        title = findViewById(R.id.title)
        kind = findViewById(R.id.kind)
        meta = findViewById(R.id.meta)
        description = findViewById(R.id.description)
        play = findViewById(R.id.play)
        poster = findViewById(R.id.poster)
        seasons = findViewById(R.id.seasons)
        episodes = findViewById(R.id.episodes)
        seasonScroller = findViewById(R.id.seasonScroller)
        episodeScroller = findViewById(R.id.episodeScroller)
        seasonScroller.disableSelfFocus()
        episodeScroller.disableSelfFocus()
        creditSection = findViewById(R.id.movieCreditSection)
        creditScroller = findViewById(R.id.movieCredits)
        creditRows = findViewById(R.id.creditRows)
        creditScroller.disableSelfFocus()
        play.bindFocusTint()
        play.ensureFocusId()
        play.nextFocusUpId = View.NO_ID
        play.setOnFocusChangeListener { view, focused ->
            val button = view as Button
            button.setTextColor(getColor(R.color.bg))
            button.animate().scaleX(1f).scaleY(1f)
                .setDuration(110).start()
            if (focused) {
                focusOnPlay = true
                focusedEpisodeId = null
            }
        }
        findViewById<Button>(R.id.showEpisodes).apply {
            bindFocusTint()
            setOnClickListener { episodes.getChildAt(0)?.requestFocus() }
        }
        poster.clipRound(14f)
        poster.adjustViewBounds = false
        findViewById<ImageView>(R.id.backdrop).apply {
            adjustViewBounds = false
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
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
        kind.text = if (series.isMovie) getString(R.string.hero_kind_movie) else ""
        kind.visibility = if (series.isMovie) View.VISIBLE else View.GONE
        meta.text = if (series.isMovie) {
            getString(R.string.duration_minutes, persianDigits(series.durationMinutes))
        } else {
            getString(R.string.season_count, persianDigits(series.presentSeasonCount))
        }
        description.maxLines = if (series.isMovie) 6 else 3
        findViewById<View>(R.id.episodeSection).visibility = if (series.isMovie) View.GONE else View.VISIBLE
        if (series.isMovie && series.kind == SourceKind.PRODUCT) {
            loadMovieCredits(series.sourceId)
        } else {
            hideMovieCredits()
        }
        findViewById<View>(R.id.showEpisodes).visibility = View.GONE
        description.text = series.description.orEmpty()
        description.visibility = if (series.description.isNullOrBlank()) View.GONE else View.VISIBLE
        val backdrop = findViewById<ImageView>(R.id.backdrop)
        backdrop.adjustViewBounds = false
        Pictures.load(
            series.backdropUrl
                ?: series.presentEpisodes.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
                ?: series.posterUrl,
            backdrop,
            R.drawable.bg_poster,
        )
        val continueEp = series.continueEpisode
        play.isEnabled = continueEp != null
        play.text = when (SeriesDetail.playAction(series.isMovie, continueEp)) {
            SeriesDetail.PlayAction.PLAY_FIRST -> getString(R.string.play_first)
            SeriesDetail.PlayAction.PLAY_MOVIE -> getString(R.string.play_movie)
            SeriesDetail.PlayAction.CONTINUE_EPISODE ->
                if (series.isMovie || continueEp == null) {
                    getString(R.string.continue_watch)
                } else {
                    getString(R.string.continue_watch_episode, continueEp.label())
                }
            SeriesDetail.PlayAction.PLAY_EPISODE ->
                getString(R.string.play_episode, continueEp?.label().orEmpty())
        }
        play.setOnClickListener { continueEp?.let(::openPlayer) }
        findViewById<View>(R.id.episodesHeading).visibility = View.GONE
        episodeScroller.visibility = if (series.isMovie) View.GONE else View.VISIBLE
        val seasonNumbers = series.presentEpisodes.map { it.seasonNumber }.distinct()
        seasonScroller.visibility =
            if (SeriesDetail.showSeasonChips(series.isMovie, seasonNumbers.size)) View.VISIBLE else View.GONE
        selectedSeason = SeriesDetail.selectedSeason(seasonNumbers, selectedSeason, continueEp?.seasonNumber)
        val seasonViews = bindSeasons(seasonNumbers, series)
        val visibleEpisodes = series.presentEpisodes.filter { selectedSeason == null || it.seasonNumber == selectedSeason }
        val episodeViews = bindEpisodes(if (series.isMovie) emptyList() else visibleEpisodes)
        wireSeriesFocus(seasonViews, episodeViews)
        if (series.isMovie) wireCreditFocus(creditCards())
        restoreSeriesFocus(seasonViews, episodeViews)
    }

    private fun creditCards(): List<View> =
        (0 until creditRows.childCount).map { creditRows.getChildAt(it) }

    private fun hideMovieCredits() {
        creditJob?.cancel()
        creditsFor = null
        focusedCreditIndex = null
        creditRows.removeAllViews()
        setCreditSectionVisible(false)
    }

    private fun loadMovieCredits(contentId: String) {
        if (creditsFor == contentId) return
        creditsFor = contentId
        focusedCreditIndex = null
        creditJob?.cancel()
        creditRows.removeAllViews()
        setCreditSectionVisible(false)
        creditJob = scope.launch {
            val people = withContext(Dispatchers.IO) {
                runCatching { ItvApplication.instance.telewebion.loadMovieCredits(contentId) }
                    .getOrDefault(emptyList())
            }
            if (creditsFor != contentId) return@launch
            showMovieCredits(people)
        }
    }

    private fun showMovieCredits(people: List<MovieCreditPerson>) {
        creditRows.removeAllViews()
        if (people.isEmpty()) {
            setCreditSectionVisible(false)
            return
        }
        val cards = people.mapIndexed { index, person ->
            val card = layoutInflater.inflate(R.layout.item_credit, creditRows, false)
            card.ensureFocusId()
            if (Build.VERSION.SDK_INT >= 26) card.defaultFocusHighlightEnabled = false
            card.setTag(R.id.creditRows, index)
            card.contentDescription = person.name
            card.findViewById<TextView>(R.id.creditName).text = person.name
            card.findViewById<TextView>(R.id.creditRole).text = person.role
            val portrait = card.findViewById<ImageView>(R.id.creditPortrait)
            portrait.adjustViewBounds = false
            portrait.scaleType = ImageView.ScaleType.CENTER_CROP
            card.findViewById<View>(R.id.creditPortraitFrame).clipOval()
            portrait.clipOval()
            val focus = card.findViewById<View>(R.id.creditFocus)
            val halo = card.findViewById<View>(R.id.creditHalo)
            focus.isSelected = false
            halo.isSelected = false
            card.setOnFocusChangeListener { view, focused ->
                if (Build.VERSION.SDK_INT >= 26) view.defaultFocusHighlightEnabled = false
                focus.isSelected = focused
                halo.isSelected = focused
                if (focused) {
                    focusedCreditIndex = index
                    focusOnPlay = false
                    focusedEpisodeId = null
                    focusedSeason = null
                    creditScroller.reveal(view, true)
                }
            }
            creditRows.addView(card)
            Pictures.load(person.imageUrl, portrait, R.drawable.ic_credit_fallback)
            card
        }
        setCreditSectionVisible(true)
        wireCreditFocus(cards)
    }

    private fun setCreditSectionVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        creditSection.visibility = visibility
        creditScroller.visibility = visibility
    }

    private fun wireCreditFocus(cards: List<View>) {
        if (cards.isEmpty() || creditScroller.visibility != View.VISIBLE) return
        wireRtlRow(cards)
        val first = cards.first()
        play.nextFocusDownId = first.id
        cards.forEach { card ->
            card.nextFocusUpId = play.id
            card.nextFocusDownId = View.NO_ID
        }
        creditScroller.pinToStart = focusedCreditIndex == null
        if (focusedCreditIndex == null) scrollRowToStart(creditScroller)
    }

    private fun rememberSeriesFocus() {
        if (!initialFocusApplied) return
        when (val focused = currentFocus) {
            play -> focusOnPlay = true
            else -> {
                val seasonTag = focused?.getTag(R.id.seasons) as? Int
                val episodeTag = focused?.getTag(R.id.episodes) as? String
                val creditTag = focused?.getTag(R.id.creditRows) as? Int
                if (seasonTag != null) focusedSeason = seasonTag
                if (episodeTag != null) focusedEpisodeId = episodeTag
                if (creditTag != null) {
                    focusedCreditIndex = creditTag
                    focusOnPlay = false
                    focusedEpisodeId = null
                    focusedSeason = null
                }
            }
        }
    }

    private fun bindSeasons(numbers: List<Int>, series: LibrarySeries): List<View> {
        seasons.removeAllViews()
        if (seasonScroller.visibility != View.VISIBLE) return emptyList()
        val density = resources.displayMetrics.density
        return numbers.map { number ->
            val chip = Button(this)
            chip.ensureFocusId()
            chip.tag = number
            chip.setTag(R.id.seasons, number)
            chip.text = getString(R.string.season_label, persianDigits(number))
            chip.isSelected = number == selectedSeason
            chip.background = getDrawable(R.drawable.bg_season_chip)
            chip.setTextColor(getColor(if (chip.isFocused || chip.isSelected) R.color.bg else R.color.text))
            chip.isFocusable = true
            chip.isFocusableInTouchMode = true
            chip.isClickable = true
            chip.isAllCaps = false
            chip.textSize = 12f
            chip.minWidth = 0
            chip.minimumWidth = 0
            chip.minHeight = 0
            chip.minimumHeight = 0
            chip.setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            chip.setOnFocusChangeListener { view, focused ->
                val button = view as Button
                button.setTextColor(getColor(if (focused || button.isSelected) R.color.bg else R.color.text))
                if (focused) {
                    focusedEpisodeId = null
                    focusedSeason = number
                    focusOnPlay = false
                }
            }
            chip.setOnClickListener {
                selectedSeason = number
                focusedSeason = number
                focusOnPlay = false
                focusedEpisodeId = null
                bind(series)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (28 * density).toInt(),
            )
            params.marginEnd = (8 * density).toInt()
            seasons.addView(chip, params)
            chip
        }
    }

    private fun bindEpisodes(items: List<LibraryEpisode>): List<View> {
        episodes.removeAllViews()
        return items.map { episode ->
            val card = layoutInflater.inflate(R.layout.item_episode, episodes, false)
            card.ensureFocusId()
            if (Build.VERSION.SDK_INT >= 26) card.defaultFocusHighlightEnabled = false
            card.tag = episode.sourceEpisodeId
            card.setTag(R.id.episodes, episode.sourceEpisodeId)
            val thumb = card.findViewById<ImageView>(R.id.thumb)
            thumb.adjustViewBounds = false
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            card.findViewById<View>(R.id.thumbFrame).clipRound(8f)
            val thumbFocus = card.findViewById<View>(R.id.thumbFocus)
            thumbFocus.isSelected = false
            card.findViewById<TextView>(R.id.title).text = episode.label()
            card.findViewById<TextView>(R.id.episodeNumber).text = ""
            val remaining = SeriesDetail.overlayRemainingMs(episode)
            val state = card.findViewById<TextView>(R.id.state)
            if (remaining != null) {
                state.visibility = View.VISIBLE
                state.text = persianClock(remaining)
            } else {
                state.visibility = View.GONE
            }
            card.findViewById<View>(R.id.watchedBadge).visibility =
                if (episode.isWatched) View.VISIBLE else View.GONE
            val progress = card.findViewById<View>(R.id.progress)
            val track = card.findViewById<View>(R.id.progressTrack)
            val fill = SeriesDetail.progressFraction(episode)
            val showBar = fill > 0f
            track.visibility = View.VISIBLE
            progress.visibility = View.VISIBLE
            track.alpha = if (showBar) 1f else 0f
            progress.alpha = if (showBar) 1f else 0f
            progress.post {
                progress.pivotX = if (card.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    progress.width.toFloat()
                } else {
                    0f
                }
                progress.scaleX = fill
            }
            card.setOnFocusChangeListener { view, focused ->
                if (Build.VERSION.SDK_INT >= 26) view.defaultFocusHighlightEnabled = false
                thumbFocus.isSelected = focused
                if (focused) {
                    focusedSeason = null
                    focusedEpisodeId = episode.sourceEpisodeId
                    focusOnPlay = false
                    episodeScroller.reveal(view, true)
                }
            }
            card.setOnClickListener { openPlayer(episode) }
            card.setOnLongClickListener {
                scope.launch { ItvApplication.instance.repository.setWatched(episode.id, !episode.isWatched) }
                true
            }
            episodes.addView(card)
            Pictures.load(episode.imageUrl, thumb, R.drawable.bg_poster)
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
        play.nextFocusUpId = View.NO_ID
        play.nextFocusLeftId = View.NO_ID
        play.nextFocusRightId = View.NO_ID
        findViewById<View>(R.id.showEpisodes).apply {
            nextFocusRightId = play.id
            nextFocusDownId = downFromPlay?.id ?: play.id
            nextFocusUpId = View.NO_ID
        }
        seasonViews.forEach { chip ->
            chip.nextFocusUpId = play.id
            chip.nextFocusDownId = firstEpisode?.id ?: View.NO_ID
        }
        episodeViews.forEach { card ->
            card.nextFocusUpId = firstSeason?.id ?: play.id
        }
        firstEpisode?.nextFocusUpId = firstSeason?.id ?: play.id
        episodeScroller.pinToStart = focusedEpisodeId == null
        seasonScroller.pinToStart = focusedSeason == null
        if (focusedEpisodeId == null) scrollRowToStart(episodeScroller)
        if (focusedSeason == null) scrollRowToStart(seasonScroller)
    }

    private fun scrollRowToStart(scroller: RtlShelfScrollView) {
        if (scroller.visibility != View.VISIBLE) return
        scroller.pinToStart = true
        scroller.post {
            if (scroller.visibility != View.VISIBLE) return@post
            val content = scroller.getChildAt(0) ?: return@post
            if (content.width == 0 || scroller.width == 0) {
                scroller.post { scrollRowToStart(scroller) }
                return@post
            }
            val start = HomeLayout.rtlStartScrollX(
                content.width,
                scroller.width,
                scroller.paddingLeft,
                scroller.paddingRight,
            )
            if (scroller.scrollX != start) scroller.scrollTo(start, 0)
        }
    }

    private fun restoreSeriesFocus(seasonViews: List<View>, episodeViews: List<View>) {
        val seasonTarget = focusedSeason?.let { number -> seasonViews.firstOrNull { it.getTag(R.id.seasons) == number } }
        val episodeTarget = focusedEpisodeId?.let { id -> episodeViews.firstOrNull { it.getTag(R.id.episodes) == id } }
        val creditTarget = focusedCreditIndex?.let { index ->
            creditCards().firstOrNull { it.getTag(R.id.creditRows) == index }
        }
        val target = when {
            !initialFocusApplied -> play
            seasonTarget != null && !focusOnPlay -> seasonTarget
            episodeTarget != null && !focusOnPlay -> episodeTarget
            creditTarget != null && !focusOnPlay -> creditTarget
            focusOnPlay -> play
            episodeTarget != null -> episodeTarget
            seasonTarget != null -> seasonTarget
            creditTarget != null -> creditTarget
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
