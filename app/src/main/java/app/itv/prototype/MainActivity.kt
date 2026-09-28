package app.itv.prototype

import android.app.Activity
import android.content.Intent
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.itv.prototype.core.HomeCatalog
import app.itv.prototype.core.ImportState
import app.itv.prototype.core.LibraryEpisode
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.PlaybackRules
import app.itv.prototype.core.persianDigits
import app.itv.prototype.ui.HomeLayout
import app.itv.prototype.ui.ImageSize
import app.itv.prototype.ui.LibraryGridActivity
import app.itv.prototype.ui.PlayerActivity
import app.itv.prototype.ui.RtlShelfScrollView
import app.itv.prototype.ui.SearchActivity
import app.itv.prototype.ui.SeriesActivity
import app.itv.prototype.ui.SettingsActivity
import app.itv.prototype.ui.applyCinematicFocus
import app.itv.prototype.ui.clipRound
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
    private lateinit var navHome: Button
    private lateinit var navLibrary: Button
    private lateinit var navSearch: Button
    private var focusedSeriesId: Long? = null
    private var focusedMoreKind: Boolean? = null
    private var heroSeries: LibrarySeries? = null
    private var initialFocus = false
    private var boundSignature: String? = null
    private lateinit var heroPlay: Button
    private lateinit var heroDetails: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        empty = findViewById(R.id.empty)
        library = findViewById(R.id.library)
        libraryScroll = findViewById(R.id.libraryScroll)
        libraryScroll.isFocusable = false
        libraryScroll.isFocusableInTouchMode = false
        libraryScroll.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        settings = findViewById(R.id.settings)
        heroPlay = findViewById(R.id.heroPlay)
        heroDetails = findViewById(R.id.heroDetails)
        navHome = findViewById(R.id.navHome)
        navLibrary = findViewById(R.id.navLibrary)
        navSearch = findViewById(R.id.navSearch)
        findViewById<View>(R.id.emptySettings)?.setOnClickListener { openSettings() }

        listOf(heroPlay, heroDetails).forEach { btn ->
            btn.isFocusable = true
            btn.isFocusableInTouchMode = true
            btn.isClickable = true
            if (Build.VERSION.SDK_INT >= 26) btn.defaultFocusHighlightEnabled = false
            btn.setOnFocusChangeListener { _, focused ->
                if (focused) libraryScroll.scrollTo(0, 0)
            }
        }
        heroPlay.setTextColor(getColor(R.color.bg))
        heroDetails.setTextColor(getColor(R.color.text))

        heroPlay.setOnClickListener {
            val ep = heroSeries?.continueEpisode ?: heroSeries?.presentEpisodes?.firstOrNull()
            ep?.let(::openPlayer)
        }
        heroDetails.setOnClickListener {
            heroSeries?.let { openSeries(it.id) }
        }

        navHome.setOnClickListener {
            libraryScroll.scrollTo(0, 0)
            heroPlay.requestFocus()
        }
        navLibrary.setOnClickListener { openLibrary(false) }
        navSearch.setOnClickListener { openSearch() }
        settings.setOnClickListener { openSettings() }

        val headerNav = listOf(navHome, navLibrary, settings, navSearch)
        headerNav.forEach { button ->
            if (Build.VERSION.SDK_INT >= 26) button.defaultFocusHighlightEnabled = false
            button.setOnFocusChangeListener { view, focused ->
                val selected = view.id == R.id.navHome
                (view as TextView).setTextColor(
                    getColor(if (focused || selected) R.color.text else R.color.muted),
                )
            }
        }
        wireRtlRow(headerNav)
        headerNav.forEach { it.nextFocusDownId = heroPlay.id }
        headerNav.first().nextFocusRightId = headerNav.first().id
        headerNav.last().nextFocusLeftId = headerNav.last().id
        wireRtlRow(listOf(heroDetails, heroPlay))
        heroPlay.nextFocusUpId = navHome.id
        heroDetails.nextFocusUpId = navHome.id
        heroDetails.nextFocusRightId = heroDetails.id
        heroPlay.nextFocusLeftId = heroPlay.id
    }

    override fun onStart() {
        super.onStart()
        observeJob?.cancel()
        observeJob = scope.launch { ItvApplication.instance.repository.observeLibrary().collect(::bind) }
    }

    override fun onStop() {
        observeJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun bind(items: List<LibrarySeries>) {
        val previousFocusId = currentFocus?.id
        val stayOnHeader = currentFocus in setOf(settings, navHome, navLibrary, navSearch)
        (currentFocus?.tag as? Long)?.let { focusedSeriesId = it }
        val hasItems = items.isNotEmpty()
        empty.visibility = if (hasItems) View.GONE else View.VISIBLE
        libraryScroll.visibility = if (hasItems) View.VISIBLE else View.GONE
        findViewById<View>(R.id.heroBackdropContainer)?.visibility = if (hasItems) View.VISIBLE else View.GONE
        if (!hasItems) {
            heroSeries = null
            boundSignature = null
            val target = findViewById<View>(R.id.emptySettings) ?: settings
            target.post { target.requestFocus() }
            return
        }

        val continueItems = HomeCatalog.continueWatching(items)
        val shelfLimit = homeShelfLimit()
        val seriesItems = HomeCatalog.recent(items, false, shelfLimit)
        val movieItems = HomeCatalog.recent(items, true, shelfLimit)
        val signature = HomeLayout.catalogSignature(
            continueItems.map { it.id },
            seriesItems.map { it.id },
            movieItems.map { it.id },
            (continueItems + seriesItems + movieItems).map {
                "${it.importState}:${it.continueEpisode?.lastPositionMs ?: 0}:${it.posterUrl.orEmpty()}"
            },
        )
        val currentHero = items.firstOrNull { it.id == heroSeries?.id } ?: HomeCatalog.featured(items)
        if (signature == boundSignature && library.childCount > 0) {
            currentHero?.let(::bindHero)
            return
        }
        boundSignature = signature
        library.removeAllViews()
        currentHero?.let(::bindHero)

        val groups = listOf(
            Triple(null, getString(R.string.continue_row), continueItems),
            Triple(false, getString(R.string.new_series), seriesItems),
            Triple(true, getString(R.string.new_movies), movieItems),
        )

        val cardRows = mutableListOf<List<View>>()
        val moreButtons = mutableMapOf<Boolean, View>()

        groups.forEach { (movies, title, contents) ->
            if (contents.isEmpty()) return@forEach

            val heading = layoutInflater.inflate(R.layout.item_home_heading, library, false) as TextView
            heading.text = title
            library.addView(heading)

            val continueRow = movies == null
            val rowHeight = resources.getDimensionPixelSize(
                if (continueRow) R.dimen.home_continue_row_h else R.dimen.home_poster_row_h,
            )
            val cardHeight = resources.getDimensionPixelSize(
                if (continueRow) R.dimen.home_continue_h else R.dimen.home_poster_h,
            )
            val horizontal = RtlShelfScrollView(this).apply {
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.home_shelf_pad_h),
                    resources.getDimensionPixelSize(R.dimen.home_shelf_pad_v),
                    resources.getDimensionPixelSize(R.dimen.home_shelf_pad_h),
                    resources.getDimensionPixelSize(R.dimen.home_shelf_pad_v),
                )
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                clipChildren = false
                clipToPadding = false
            }
            horizontal.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, cardHeight))
            library.addView(horizontal, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowHeight))

            val cards = contents.map { item ->
                card(item, row, continueRow).also {
                    it.tag = item.id
                    it.ensureFocusId()
                    row.addView(it)
                }
            }.toMutableList()

            if (movies != null) {
                val more = layoutInflater.inflate(R.layout.item_home_more, row, false).apply {
                    ensureFocusId()
                    tag = "more_$movies"
                    clipRound(10f)
                    if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
                    setOnFocusChangeListener { view, focused ->
                        view.applyCinematicFocus(focused)
                        if (focused) {
                            focusedMoreKind = movies
                            revealHomeItem(view)
                        }
                    }
                    setOnClickListener {
                        focusedMoreKind = movies
                        openLibrary(movies)
                    }
                }
                row.addView(more)
                cards += more
                moreButtons[movies] = more
            }

            wireRtlRow(cards)
            cards.first().nextFocusRightId = cards.first().id
            cards.last().nextFocusLeftId = cards.last().id
            cardRows += cards
        }

        cardRows.forEachIndexed { rowIndex, cards ->
            cards.forEachIndexed { index, card ->
                card.nextFocusUpId = cardRows.getOrNull(rowIndex - 1)?.let { it[index.coerceAtMost(it.lastIndex)].id } ?: heroPlay.id
                card.nextFocusDownId = cardRows.getOrNull(rowIndex + 1)?.let { it[index.coerceAtMost(it.lastIndex)].id } ?: card.id
            }
        }

        val all = cardRows.flatten()
        val landing = all.firstOrNull { it.tag == focusedSeriesId } ?: all.firstOrNull()
        if (landing != null) {
            heroPlay.nextFocusDownId = landing.id
            heroDetails.nextFocusDownId = landing.id
        }

        val stable = previousFocusId?.let { findViewById<View>(it) }?.takeIf { it.isShown && it.isFocusable }
        val target = when {
            !initialFocus -> heroPlay
            stayOnHeader -> currentFocus ?: heroPlay
            else -> stable ?: focusedMoreKind?.let(moreButtons::get) ?: landing ?: heroPlay
        }
        initialFocus = true
        if (target !== currentFocus) {
            target.post { if (target.isShown) target.requestFocus() }
        }
    }

    private fun revealHomeItem(view: View) {
        val shelf = generateSequence(view.parent as? View) { it.parent as? View }
            .filterIsInstance<RtlShelfScrollView>()
            .firstOrNull()
        shelf?.reveal(view, true)
        if (shelf == null) return
        val contentTop = library.top + shelf.top
        val contentBottom = contentTop + shelf.height
        val visibleTop = libraryScroll.scrollY
        val visibleBottom = visibleTop + libraryScroll.height
        val headerReserve = resources.getDimensionPixelSize(R.dimen.home_header_h)
        val nextY = when {
            contentTop < visibleTop + headerReserve -> (contentTop - headerReserve).coerceAtLeast(0)
            contentBottom > visibleBottom -> contentBottom - libraryScroll.height
            else -> return
        }
        libraryScroll.scrollTo(0, nextY)
    }

    private fun card(series: LibrarySeries, row: LinearLayout, continuePlayback: Boolean): View {
        val layoutRes = if (continuePlayback) R.layout.item_home_continue else R.layout.item_home_poster
        val card = layoutInflater.inflate(layoutRes, row, false)
        val poster = card.findViewById<ImageView>(R.id.poster)
        card.findViewById<View>(R.id.cardInner)?.clipRound(8f)
        if (Build.VERSION.SDK_INT >= 26) card.defaultFocusHighlightEnabled = false

        if (continuePlayback) {
            val continueEp = series.continueEpisode
            val thumbUrl = continueEp?.imageUrl ?: series.backdropUrl ?: series.posterUrl
            Pictures.load(thumbUrl, poster, R.drawable.bg_poster)
            bindProgress(card, continueEp, series)
        } else {
            val posterUrl = coverUrl(series)
            Pictures.load(posterUrl, poster, R.drawable.bg_poster) { bitmap ->
                val size = ImageSize(bitmap.width, bitmap.height)
                poster.scaleType = if (HomeLayout.coverFitsFrame(size)) {
                    ImageView.ScaleType.CENTER_CROP
                } else {
                    ImageView.ScaleType.FIT_CENTER
                }
            }
            val hasPoster = !posterUrl.isNullOrBlank()
            card.findViewById<View>(R.id.cardScrim)?.visibility = if (hasPoster) View.INVISIBLE else View.VISIBLE
            card.findViewById<TextView>(R.id.title)?.apply {
                text = series.title
                visibility = if (hasPoster) View.INVISIBLE else View.VISIBLE
            }
        }

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
            ImportState.DONE -> badge.visibility = View.INVISIBLE
        }

        card.setOnFocusChangeListener { view, focused ->
            view.applyCinematicFocus(focused)
            if (focused) {
                bindHero(series)
                focusedSeriesId = view.tag as? Long
                focusedMoreKind = null
                heroPlay.nextFocusDownId = view.id
                heroDetails.nextFocusDownId = view.id
                revealHomeItem(view)
            }
        }

        card.setOnClickListener {
            focusedSeriesId = series.id
            if (continuePlayback) series.continueEpisode?.let(::openPlayer) else openSeries(series.id)
        }
        return card
    }

    private fun bindProgress(card: View, continueEp: LibraryEpisode?, series: LibrarySeries) {
        val progress = card.findViewById<View>(R.id.progress)
        val track = card.findViewById<View>(R.id.progressTrack)
        val show = continueEp != null &&
            continueEp.durationMs > 0 &&
            continueEp.lastPositionMs > 0 &&
            !PlaybackRules.seriesWatched(series.episodes)
        val fill = if (show && continueEp != null) {
            HomeLayout.progressFill(continueEp.lastPositionMs, continueEp.durationMs)
        } else {
            0f
        }
        track.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        track.alpha = if (show) 1f else 0f
        progress.alpha = if (show && fill > 0f) 1f else 0f
        progress.scaleX = fill
        progress.post {
            progress.pivotX = if (progress.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                progress.width.toFloat()
            } else {
                0f
            }
            progress.scaleX = fill
        }
    }

    private fun openPlayer(episode: LibraryEpisode) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_EPISODE_ID, episode.id)
                .putExtra(PlayerActivity.EXTRA_SERIES_ID, episode.seriesId),
        )
    }

    private fun openSeries(id: Long) {
        startActivity(Intent(this, SeriesActivity::class.java).putExtra(SeriesActivity.EXTRA_ID, id))
    }

    private fun openLibrary(movies: Boolean) {
        startActivity(
            Intent(this, LibraryGridActivity::class.java)
                .putExtra(LibraryGridActivity.EXTRA_MOVIES, movies),
        )
    }

    private fun openSearch() {
        startActivity(Intent(this, SearchActivity::class.java))
    }

    private fun imageSize(url: String?): ImageSize? =
        Pictures.sizeOf(url)?.let { ImageSize(it.first, it.second) }

    private fun coverUrl(series: LibrarySeries): String? =
        HomeLayout.coverUrl(
            series.posterUrl,
            series.backdropUrl,
            imageSize(series.posterUrl),
            imageSize(series.backdropUrl),
        ) ?: series.posterUrl ?: series.backdropUrl

    private fun artwork(series: LibrarySeries): String? {
        val episodeUrl = series.continueEpisode?.imageUrl
            ?: series.presentEpisodes.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
        return HomeLayout.heroUrl(
            series.backdropUrl,
            episodeUrl,
            series.posterUrl,
            imageSize(series.backdropUrl),
            imageSize(episodeUrl),
            imageSize(series.posterUrl),
        )
    }

    private fun applyHeroPlacement(view: ImageView, width: Int, height: Int) {
        if (view.width <= 0 || view.height <= 0) {
            view.post { applyHeroPlacement(view, width, height) }
            return
        }
        val place = HomeLayout.heroPlacement(view.width, view.height, ImageSize(width, height))
        val matrix = Matrix()
        matrix.setScale(place.scale, place.scale)
        matrix.postTranslate(place.translateX, place.translateY)
        view.scaleType = ImageView.ScaleType.MATRIX
        view.imageMatrix = matrix
    }

    private fun bindHero(series: LibrarySeries) {
        heroSeries = series
        val backdrop = findViewById<ImageView>(R.id.backdrop)
        val url = artwork(series)
        Pictures.load(url, backdrop) { bitmap ->
            applyHeroPlacement(backdrop, bitmap.width, bitmap.height)
        }
        findViewById<TextView>(R.id.heroTitle).text = series.title
        findViewById<TextView>(R.id.heroKindChip).text =
            getString(if (series.isMovie) R.string.hero_kind_movie else R.string.hero_kind_series)
        bindHeroChips(series)
        val playEp = series.continueEpisode ?: series.presentEpisodes.firstOrNull()
        heroPlay.isEnabled = playEp != null
        heroPlay.text = if ((series.continueEpisode?.lastPositionMs ?: 0) > PlaybackRules.RESUME_THRESHOLD_MS) {
            getString(R.string.hero_continue)
        } else {
            getString(R.string.hero_watch)
        }
    }

    private fun bindHeroChips(series: LibrarySeries) {
        val chip2 = findViewById<TextView>(R.id.heroChip2)
        val chip3 = findViewById<TextView>(R.id.heroChip3)
        if (series.isMovie) {
            val duration = series.durationMinutes
            chip2.text = if (duration > 0) "${persianDigits(duration)} دقیقه" else series.kind.label
            chip2.visibility = View.VISIBLE
            chip3.visibility = View.GONE
            return
        }
        chip2.text = getString(R.string.season_count, persianDigits(series.presentSeasonCount))
        chip3.text = getString(R.string.episode_count, persianDigits(series.presentEpisodes.size))
        chip2.visibility = View.VISIBLE
        chip3.visibility = View.VISIBLE
    }

    private fun homeShelfLimit(): Int {
        val poster = resources.getDimensionPixelSize(R.dimen.home_poster_w)
        val gap = resources.getDimensionPixelSize(R.dimen.home_poster_gap)
        val shelfPad = resources.getDimensionPixelSize(R.dimen.home_shelf_pad_h)
        val outer = if (library.width > 0) library.width else resources.displayMetrics.widthPixels
        val available = (outer - library.paddingStart - library.paddingEnd).coerceAtLeast(0)
        return HomeLayout.shelfVisibleCount(
            availableWidth = available,
            posterWidth = poster,
            gap = gap,
            paddingStart = shelfPad,
            paddingEnd = shelfPad,
            moreWidth = poster,
            maxCount = HomeCatalog.HOME_LIMIT,
        ).coerceAtLeast(1)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
