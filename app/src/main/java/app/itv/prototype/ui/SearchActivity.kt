package app.itv.prototype.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import app.itv.prototype.ItvApplication
import app.itv.prototype.Pictures
import app.itv.prototype.R
import app.itv.prototype.core.HomeCatalog
import app.itv.prototype.core.ImportState
import app.itv.prototype.core.LibrarySeries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SearchActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private lateinit var query: EditText
    private lateinit var empty: TextView
    private lateinit var grid: GridView
    private lateinit var adapter: PosterAdapter
    private var catalog: List<LibrarySeries> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        query = findViewById(R.id.query)
        empty = findViewById(R.id.searchEmpty)
        grid = findViewById(R.id.results)
        adapter = PosterAdapter()
        grid.adapter = adapter
        grid.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
            val series = adapter.getItem(position)
            startActivity(Intent(this, SeriesActivity::class.java).putExtra(SeriesActivity.EXTRA_ID, series.id))
        }

        findViewById<Button>(R.id.navHome).apply {
            setBackgroundResource(R.drawable.bg_nav_pill)
            setTextColor(getColor(R.color.muted))
            setOnClickListener { finish() }
        }
        findViewById<Button>(R.id.navSearch).apply {
            setBackgroundResource(R.drawable.bg_nav_pill_home)
            setTextColor(getColor(R.color.text))
        }
        findViewById<Button>(R.id.navSeries).setOnClickListener {
            startActivity(
                Intent(this@SearchActivity, LibraryGridActivity::class.java)
                    .putExtra(LibraryGridActivity.EXTRA_MOVIES, false),
            )
        }
        findViewById<Button>(R.id.navMovies).setOnClickListener {
            startActivity(
                Intent(this@SearchActivity, LibraryGridActivity::class.java)
                    .putExtra(LibraryGridActivity.EXTRA_MOVIES, true),
            )
        }
        findViewById<Button>(R.id.settings).setOnClickListener {
            startActivity(Intent(this@SearchActivity, SettingsActivity::class.java))
        }

        val headerNav = listOf(
            findViewById<Button>(R.id.navHome),
            findViewById<Button>(R.id.navSeries),
            findViewById<Button>(R.id.navMovies),
            findViewById<Button>(R.id.settings),
            findViewById<Button>(R.id.navSearch),
        )
        wireRtlRow(headerNav)
        headerNav.forEach {
            if (Build.VERSION.SDK_INT >= 26) it.defaultFocusHighlightEnabled = false
            it.nextFocusDownId = query.id
        }
        headerNav.first().nextFocusRightId = headerNav.first().id
        headerNav.last().nextFocusLeftId = headerNav.last().id
        query.nextFocusUpId = R.id.navSearch
        query.nextFocusDownId = grid.id

        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = render()
        })
        query.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        observeJob?.cancel()
        observeJob = scope.launch {
            ItvApplication.instance.repository.observeLibrary().collect { items ->
                catalog = items
                render()
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

    private fun render() {
        adapter.items = HomeCatalog.search(catalog, query.text.toString())
        adapter.notifyDataSetChanged()
        val hasResults = adapter.count > 0
        empty.visibility = if (hasResults) View.GONE else View.VISIBLE
        grid.visibility = if (hasResults) View.VISIBLE else View.GONE
    }

    private inner class PosterAdapter : BaseAdapter() {
        var items: List<LibrarySeries> = emptyList()

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id
        override fun hasStableIds() = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val card = convertView ?: layoutInflater.inflate(R.layout.item_home_poster, parent, false).apply {
                layoutParams = AbsListView.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.home_poster_w),
                    resources.getDimensionPixelSize(R.dimen.home_poster_h),
                )
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false
                if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
            }
            val series = getItem(position)
            val poster = card.findViewById<ImageView>(R.id.poster)
            card.findViewById<View>(R.id.cardInner)?.clipRound(8f)
            val posterUrl = series.posterUrl ?: series.backdropUrl
            Pictures.load(posterUrl, poster, R.drawable.bg_poster)
            val hasPoster = !posterUrl.isNullOrBlank()
            card.findViewById<View>(R.id.cardScrim)?.visibility = if (hasPoster) View.GONE else View.VISIBLE
            card.findViewById<TextView>(R.id.title)?.apply {
                text = series.title
                visibility = if (hasPoster) View.GONE else View.VISIBLE
            }
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
    }
}
