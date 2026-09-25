package app.itv.prototype

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.itv.prototype.core.ImportState
import app.itv.prototype.core.HomeCatalog
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.PlaybackRules
import app.itv.prototype.core.persianDigits
import app.itv.prototype.ui.PlayerActivity
import app.itv.prototype.ui.LibraryGridActivity
import app.itv.prototype.ui.SeriesActivity
import app.itv.prototype.ui.SettingsActivity
import app.itv.prototype.ui.bindCardFocus
import app.itv.prototype.ui.bindFocusTint
import app.itv.prototype.ui.clipRound
import app.itv.prototype.ui.disableSelfFocus
import app.itv.prototype.ui.ensureFocusId
import app.itv.prototype.ui.wireRtlRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private lateinit var empty: View
    private lateinit var library: LinearLayout
    private lateinit var libraryScroll: ScrollView
    private lateinit var settings: Button
    private var focusedSeriesId: Long? = null
    private var focusedMoreKind: Boolean? = null
    private var heroSeries: LibrarySeries? = null
    private var initialFocus = false
    private lateinit var heroPlay: Button
    private lateinit var heroDetails: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        empty = findViewById(R.id.empty)
        library = findViewById(R.id.library)
        libraryScroll = findViewById(R.id.libraryScroll)
        settings = findViewById(R.id.settings)
        settings.bindFocusTint()
        heroPlay = findViewById(R.id.heroPlay)
        heroDetails = findViewById(R.id.heroDetails)
        findViewById<View>(R.id.hero).clipRound(12f)
        listOf(heroPlay, heroDetails).forEach { it.bindFocusTint() }
        heroPlay.setOnClickListener {
            heroSeries?.continueEpisode?.let { episode ->
                startActivity(Intent(this, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_EPISODE_ID, episode.id)
                    .putExtra(PlayerActivity.EXTRA_SERIES_ID, episode.seriesId))
            }
        }
        heroDetails.setOnClickListener { heroSeries?.let { openSeries(it.id) } }
        findViewById<Button>(R.id.navHome).apply {
            bindFocusTint()
            setOnClickListener { libraryScroll.smoothScrollTo(0, 0); heroPlay.requestFocus() }
        }
        settings.setOnClickListener { openSettings() }
    }

    override fun onStart() {
        super.onStart()
        observeJob?.cancel()
        observeJob = scope.launch { ItvApplication.instance.repository.observeLibrary().collect(::bind) }
    }
    override fun onStop() { observeJob?.cancel(); super.onStop() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun bind(items: List<LibrarySeries>) {
        val previousFocusId = currentFocus?.id
        val stayOnSettings = currentFocus === settings
        (currentFocus?.tag as? Long)?.let { focusedSeriesId = it }
        library.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        libraryScroll.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        if (items.isEmpty()) {
            heroSeries = null
            settings.post { settings.requestFocus() }
            return
        }
        bindHero(items.firstOrNull { it.id == heroSeries?.id } ?: items.firstOrNull { !it.isMovie } ?: items.first())
        val groups = listOf(false to "سریال‌ها", true to "فیلم‌های سینمایی")
        val cardRows = mutableListOf<List<View>>()
        val moreButtons = mutableMapOf<Boolean, View>()
        groups.forEach { (movies, title) ->
            val contents = HomeCatalog.recent(items, movies)
            if (contents.isEmpty()) return@forEach
            val heading = TextView(this).apply {
                text = title
                textSize = 18f
                setTypeface(settings.typeface, Typeface.BOLD)
                setTextColor(getColor(R.color.text))
                setPadding(0, dp(9), 0, dp(6))
            }
            library.addView(heading)
            val horizontal = HorizontalScrollView(this).apply {
                disableSelfFocus()
                isHorizontalScrollBarEnabled = false
                clipChildren = false
                clipToPadding = false
                setPadding(0, dp(6), 0, dp(12))
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                setPadding(0, 0, 0, 0)
            }
            horizontal.addView(row, android.widget.FrameLayout.LayoutParams(-2, -2))
            library.addView(horizontal, LinearLayout.LayoutParams(-1, -2))
            val cards = contents.map { item -> card(item, row).also { it.tag = item.id; it.ensureFocusId(); row.addView(it) } }.toMutableList()
            val more = Button(this).apply {
                ensureFocusId()
                text = getString(R.string.show_more)
                textSize = 15f
                setTextColor(getColor(R.color.cyan))
                setTypeface(typeface, Typeface.BOLD)
                background = getDrawable(R.drawable.bg_episode)
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                setOnFocusChangeListener { view, focused ->
                    if (focused) {
                        focusedMoreKind = movies
                        view.requestRectangleOnScreen(android.graphics.Rect(), false)
                    }
                }
                setOnClickListener {
                    focusedMoreKind = movies
                    startActivity(Intent(this@MainActivity, LibraryGridActivity::class.java)
                        .putExtra(LibraryGridActivity.EXTRA_MOVIES, movies))
                }
            }
            row.addView(more, LinearLayout.LayoutParams(dp(138), dp(156)).apply { marginEnd = dp(12) })
            cards += more
            moreButtons[movies] = more
            wireRtlRow(cards)
            cardRows += cards
            horizontal.post { horizontal.scrollTo((row.width - horizontal.width).coerceAtLeast(0), 0) }
        }
        cardRows.forEachIndexed { rowIndex, cards ->
            cards.forEachIndexed { index, card ->
                card.nextFocusUpId = cardRows.getOrNull(rowIndex - 1)?.let { it[index.coerceAtMost(it.lastIndex)].id } ?: heroPlay.id
                card.nextFocusDownId = cardRows.getOrNull(rowIndex + 1)?.let { it[index.coerceAtMost(it.lastIndex)].id } ?: card.id
            }
        }
        val all = cardRows.flatten()
        val landing = all.firstOrNull { it.tag == focusedSeriesId } ?: all.first()
        heroPlay.nextFocusDownId = landing.id
        heroDetails.nextFocusDownId = landing.id
        settings.nextFocusDownId = heroPlay.id
        findViewById<View>(R.id.navHome).nextFocusDownId = heroPlay.id
        val stable = previousFocusId?.let { findViewById<View>(it) }?.takeIf { it.isShown && it.isFocusable }
        val target = if (!initialFocus) heroPlay else if (stayOnSettings) settings else stable
            ?: focusedMoreKind?.let(moreButtons::get) ?: landing
        initialFocus = true
        target.post { target.requestFocus() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun card(series: LibrarySeries, row: LinearLayout): View {
        val card = layoutInflater.inflate(R.layout.item_series, row, false)
        val poster = card.findViewById<ImageView>(R.id.poster)
        poster.clipRound(6f)
        Pictures.load(artwork(series), poster, R.drawable.bg_poster)
        card.findViewById<TextView>(R.id.title).text = series.title
        card.findViewById<TextView>(R.id.meta).text = meta(series)
        val badge = card.findViewById<TextView>(R.id.badge)
        when (series.importState) {
            ImportState.RUNNING -> {
                badge.visibility = View.VISIBLE
                badge.text = getString(R.string.importing)
            }
            ImportState.QUEUED -> {
                badge.visibility = View.VISIBLE
                badge.text = getString(R.string.import_queued)
            }
            ImportState.ERROR -> {
                badge.visibility = View.VISIBLE
                badge.text = series.importError ?: getString(R.string.import_error)
            }
            ImportState.DONE -> badge.visibility = View.GONE
        }
        val continueEp = series.continueEpisode
        val progress = card.findViewById<View>(R.id.progress)
        val track = card.findViewById<View>(R.id.progressTrack)
        if (continueEp != null && (continueEp.lastPositionMs > 0 || !PlaybackRules.seriesWatched(series.episodes))) {
            if (continueEp.durationMs > 0 && continueEp.lastPositionMs > 0) {
                track.visibility = View.VISIBLE
                progress.visibility = View.VISIBLE
                val width = (270f * (continueEp.lastPositionMs.toFloat() / continueEp.durationMs.toFloat())).toInt().coerceIn(4, 270)
                progress.layoutParams = progress.layoutParams.apply { this.width = (width * resources.displayMetrics.density).toInt() }
            }
        }
        card.bindCardFocus()
        card.setOnFocusChangeListener { view, focused ->
            view.animate().scaleX(1f).scaleY(1f)
                .setDuration(120).start()
            view.elevation = if (focused) 18f else 0f
            if (focused) {
                bindHero(series)
                focusedSeriesId = view.tag as? Long
                focusedMoreKind = null
                heroPlay.nextFocusDownId = view.id
                heroDetails.nextFocusDownId = view.id
                val bounds = android.graphics.Rect()
                view.getDrawingRect(bounds)
                view.requestRectangleOnScreen(bounds, false)
            }
        }
        card.setOnClickListener {
            focusedSeriesId = series.id
            openSeries(series.id)
        }
        return card
    }

    private fun openSeries(id: Long) {
        startActivity(Intent(this, SeriesActivity::class.java).putExtra(SeriesActivity.EXTRA_ID, id))
    }

    private fun artwork(series: LibrarySeries) = series.backdropUrl
        ?: series.presentEpisodes.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl ?: series.posterUrl

    private fun bindHero(series: LibrarySeries) {
        heroSeries = series
        Pictures.load(artwork(series), findViewById(R.id.backdrop))
        findViewById<TextView>(R.id.heroTitle).text = series.title
        findViewById<TextView>(R.id.heroMeta).text = meta(series)
        heroPlay.isEnabled = series.continueEpisode != null
        heroPlay.text = if ((series.continueEpisode?.lastPositionMs ?: 0) > PlaybackRules.RESUME_THRESHOLD_MS) "ادامهٔ تماشا  ▶" else "تماشا  ▶"
    }

    private fun meta(series: LibrarySeries): String {
        if (series.isMovie) return "فیلم سینمایی · ${persianDigits(series.durationMinutes)} دقیقه"
        val kind = "سریال"
        val seasons = getString(R.string.season_count, persianDigits(series.presentSeasonCount))
        val episodes = getString(R.string.episode_count, persianDigits(series.presentEpisodes.size))
        return getString(R.string.season_episode_meta, kind, "$seasons · $episodes")
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

}
