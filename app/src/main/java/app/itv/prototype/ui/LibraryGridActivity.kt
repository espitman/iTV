package app.itv.prototype.ui

import android.app.Activity
import android.content.Intent
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
import app.itv.prototype.core.PlaybackRules
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library_grid)
        movies = intent.getBooleanExtra(EXTRA_MOVIES, false)
        findViewById<TextView>(R.id.gridTitle).setText(if (movies) R.string.all_movies else R.string.all_series)
        grid = findViewById(R.id.grid)
        adapter = SeriesAdapter()
        grid.adapter = adapter
        grid.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
            val series = adapter.getItem(position)
            startActivity(Intent(this, SeriesActivity::class.java).putExtra(SeriesActivity.EXTRA_ID, series.id))
        }
        findViewById<Button>(R.id.navHome).apply { bindFocusTint(); setOnClickListener { finish() } }
        findViewById<Button>(R.id.settings).apply {
            bindFocusTint()
            setOnClickListener { startActivity(Intent(this@LibraryGridActivity, SettingsActivity::class.java)) }
        }
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class SeriesAdapter : BaseAdapter() {
        var items: List<LibrarySeries> = emptyList()

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id
        override fun hasStableIds() = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val card = convertView ?: layoutInflater.inflate(R.layout.item_series, parent, false).apply {
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(156))
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false
            }
            val series = getItem(position)
            val poster = card.findViewById<ImageView>(R.id.poster)
            poster.clipRound(6f)
            Pictures.load(series.backdropUrl
                ?: series.presentEpisodes.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl
                ?: series.posterUrl, poster, R.drawable.bg_poster)
            card.findViewById<TextView>(R.id.title).text = series.title
            card.findViewById<TextView>(R.id.meta).text = if (series.isMovie) {
                if (series.durationMinutes > 0) "فیلم سینمایی · ${persianDigits(series.durationMinutes)} دقیقه" else "فیلم سینمایی"
            } else {
                getString(R.string.season_episode_meta,
                    getString(R.string.season_count, persianDigits(series.presentSeasonCount)),
                    getString(R.string.episode_count, persianDigits(series.presentEpisodes.size)))
            }
            val badge = card.findViewById<TextView>(R.id.badge)
            badge.visibility = if (series.importState == ImportState.DONE) View.GONE else View.VISIBLE
            badge.text = when (series.importState) {
                ImportState.RUNNING -> getString(R.string.importing)
                ImportState.QUEUED -> getString(R.string.import_queued)
                ImportState.ERROR -> series.importError ?: getString(R.string.import_error)
                ImportState.DONE -> ""
            }
            val episode = series.continueEpisode
            val progress = card.findViewById<View>(R.id.progress)
            val track = card.findViewById<View>(R.id.progressTrack)
            val showProgress = episode != null && episode.durationMs > 0 && episode.lastPositionMs > 0 &&
                !PlaybackRules.seriesWatched(series.episodes)
            track.visibility = if (showProgress) View.VISIBLE else View.GONE
            progress.visibility = if (showProgress) View.VISIBLE else View.GONE
            if (showProgress && episode != null) {
                val width = (270f * episode.lastPositionMs / episode.durationMs).toInt().coerceIn(4, 270)
                progress.layoutParams = progress.layoutParams.apply { this.width = dp(width) }
            }
            return card
        }
    }

    companion object {
        const val EXTRA_MOVIES = "movies"
    }
}
