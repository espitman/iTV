package app.itv.prototype.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import app.itv.prototype.ItvApplication
import app.itv.prototype.Pictures
import app.itv.prototype.R
import app.itv.prototype.core.HomeCatalog
import app.itv.prototype.core.ImportState
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.persianDigits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LibraryGridActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private lateinit var grid: GridView
    private lateinit var adapter: SeriesAdapter
    private var movies = false
    private var applied: LibraryGridLayout.Metrics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library_grid)
        movies = intent.getBooleanExtra(EXTRA_MOVIES, false)
        findViewById<TextView>(R.id.gridTitle).setText(
            if (movies) R.string.catalog_movies_title else R.string.catalog_series_title,
        )
        findViewById<TextView>(R.id.gridChip).setText(
            if (movies) R.string.catalog_movies_chip else R.string.catalog_series_chip,
        )
        grid = findViewById(R.id.grid)
        if (Build.VERSION.SDK_INT >= 26) grid.defaultFocusHighlightEnabled = false
        grid.selector = getDrawable(android.R.color.transparent)
        grid.stretchMode = GridView.NO_STRETCH
        adapter = SeriesAdapter()
        grid.adapter = adapter
        grid.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
            val series = adapter.getItem(position)
            startActivity(Intent(this, SeriesActivity::class.java).putExtra(SeriesActivity.EXTRA_ID, series.id))
        }
        findViewById<Button>(R.id.navBack).apply {
            bindFocusTint()
            if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
            setOnClickListener { finish() }
            nextFocusDownId = R.id.grid
        }
        grid.nextFocusUpId = R.id.navBack
        applyGridMetrics()
        grid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyGridMetrics() }
    }

    override fun onStart() {
        super.onStart()
        observeJob?.cancel()
        observeJob = scope.launch {
            ItvApplication.instance.repository.observeLibrary().collect { items ->
                val selected = grid.selectedItemPosition.coerceAtLeast(0)
                adapter.items = HomeCatalog.all(items, movies)
                adapter.notifyDataSetChanged()
                if (adapter.count > 0) grid.post {
                    grid.setSelection(selected.coerceAtMost(adapter.count - 1))
                    grid.requestFocus()
                }
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

    private fun applyGridMetrics() {
        val catalogPad = resources.getDimensionPixelSize(R.dimen.catalog_pad)
        val width = if (grid.width > 0) grid.width else resources.displayMetrics.widthPixels
        val available = width - 2 * catalogPad
        if (available <= 0) return
        val metrics = LibraryGridLayout.metrics(
            availableWidth = available,
            minColumnWidth = resources.getDimensionPixelSize(R.dimen.catalog_min_column),
            titleBlock = resources.getDimensionPixelSize(R.dimen.catalog_title_block),
            focusPad = resources.getDimensionPixelSize(R.dimen.catalog_focus_pad),
        )
        val padLeft = catalogPad + metrics.leftGutter
        val padRight = catalogPad + metrics.rightGutter
        if (applied == metrics && grid.paddingLeft == padLeft && grid.paddingRight == padRight) return
        applied = metrics
        adapter.metrics = metrics
        grid.setPadding(padLeft, grid.paddingTop, padRight, grid.paddingBottom)
        grid.numColumns = metrics.columns
        grid.horizontalSpacing = metrics.gap
        grid.verticalSpacing = metrics.gap
        grid.columnWidth = metrics.columnWidth
        grid.stretchMode = GridView.NO_STRETCH
        adapter.notifyDataSetChanged()
    }

    private inner class SeriesAdapter : BaseAdapter() {
        var items: List<LibrarySeries> = emptyList()
        var metrics: LibraryGridLayout.Metrics? = null

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id
        override fun hasStableIds() = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val focusPad = resources.getDimensionPixelSize(R.dimen.catalog_focus_pad)
            val titleBlock = resources.getDimensionPixelSize(R.dimen.catalog_title_block)
            val columnWidth = metrics?.columnWidth
                ?: grid.columnWidth.takeIf { it > 0 }
                ?: resources.getDimensionPixelSize(R.dimen.catalog_min_column)
            val posterHeight = LibraryGridLayout.posterFrameHeight(columnWidth, focusPad)
            val itemHeight = posterHeight + titleBlock
            val card = convertView ?: layoutInflater.inflate(R.layout.item_series, parent, false).apply {
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false
                if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
            }
            card.isSelected = position == grid.selectedItemPosition
            card.refreshDrawableState()
            card.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight)
            card.findViewById<View>(R.id.posterFrame).layoutParams =
                card.findViewById<View>(R.id.posterFrame).layoutParams.apply { height = posterHeight }
            val series = getItem(position)
            val poster = card.findViewById<ImageView>(R.id.poster)
            val inner = card.findViewById<View>(R.id.cardInner)
            inner.clipRound(10f)
            Pictures.load(LibraryGridLayout.coverUrl(series.posterUrl, series.backdropUrl), poster, R.drawable.bg_poster)
            card.findViewById<TextView>(R.id.title).text = series.title
            val meta = card.findViewById<TextView>(R.id.meta)
            meta.text = catalogMeta(series)
            meta.visibility = if (meta.text.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
            val badge = card.findViewById<TextView>(R.id.badge)
            badge.visibility = if (series.importState == ImportState.DONE) View.GONE else View.VISIBLE
            badge.text = when (series.importState) {
                ImportState.RUNNING -> getString(R.string.importing)
                ImportState.QUEUED -> getString(R.string.import_queued)
                ImportState.ERROR -> series.importError ?: getString(R.string.import_error)
                ImportState.DONE -> ""
            }
            return card
        }

        private fun catalogMeta(series: LibrarySeries): String = if (series.isMovie) {
            LibraryGridLayout.movieDurationMinutes(series.durationMinutes)
                ?.let { getString(R.string.duration_minutes, persianDigits(it)) }
                .orEmpty()
        } else if (LibraryGridLayout.hasSeriesMeta(series.presentSeasonCount, series.presentEpisodes.size)) {
            getString(
                R.string.season_episode_meta,
                getString(R.string.season_count, persianDigits(series.presentSeasonCount)),
                getString(R.string.episode_count, persianDigits(series.presentEpisodes.size)),
            )
        } else {
            ""
        }
    }

    companion object {
        const val EXTRA_MOVIES = "movies"
    }
}
