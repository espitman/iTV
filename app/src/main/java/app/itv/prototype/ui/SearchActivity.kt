package app.itv.prototype.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

class SearchActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private lateinit var searchBar: View
    private lateinit var query: EditText
    private lateinit var clearQuery: ImageButton
    private lateinit var voiceSearch: Button
    private lateinit var resultsChrome: View
    private lateinit var resultsTitle: TextView
    private lateinit var resultCount: TextView
    private lateinit var filterAll: Button
    private lateinit var filterSeries: Button
    private lateinit var filterMovies: Button
    private lateinit var empty: TextView
    private lateinit var results: LinearLayout
    private var catalog: List<LibrarySeries> = emptyList()
    private var filter = Filter.ALL
    private var blockAutoKeyboard = false
    private var imeInsetVisible = false
    private var keyboardGeneration = 0
    private var landed = false
    private var cards: List<List<View>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        searchBar = findViewById(R.id.searchBar)
        query = findViewById(R.id.query)
        clearQuery = findViewById(R.id.clearQuery)
        voiceSearch = findViewById(R.id.voiceSearch)
        resultsChrome = findViewById(R.id.resultsChrome)
        resultsTitle = findViewById(R.id.resultsTitle)
        resultCount = findViewById(R.id.resultCount)
        filterAll = findViewById(R.id.filterAll)
        filterSeries = findViewById(R.id.filterSeries)
        filterMovies = findViewById(R.id.filterMovies)
        empty = findViewById(R.id.searchEmpty)
        results = findViewById(R.id.results)
        query.showSoftInputOnFocus = true
        resultsChrome.visibility = View.GONE
        empty.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= 30) {
            window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                imeInsetVisible = insets.isVisible(WindowInsets.Type.ime())
                view.onApplyWindowInsets(insets)
            }
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

        prepareFocus(query)
        prepareFocus(clearQuery)
        prepareFocus(voiceSearch)
        prepareFocus(filterAll)
        prepareFocus(filterSeries)
        prepareFocus(filterMovies)
        query.setOnClickListener { openKeyboard() }
        query.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> {
                    openKeyboard()
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> leaveQuery(View.FOCUS_LEFT)
                KeyEvent.KEYCODE_DPAD_RIGHT -> leaveQuery(View.FOCUS_RIGHT)
                KeyEvent.KEYCODE_DPAD_UP -> leaveQuery(View.FOCUS_UP)
                KeyEvent.KEYCODE_DPAD_DOWN -> leaveQuery(View.FOCUS_DOWN)
                else -> false
            }
        }
        query.setOnFocusChangeListener { _, focused ->
            searchBar.isSelected = focused
            if (!focused) hideKeyboard()
        }
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                focusFirstResult()
                true
            } else {
                false
            }
        }
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = render()
        })
        clearQuery.setOnClickListener {
            blockAutoKeyboard = false
            query.text?.clear()
            openKeyboard()
        }
        voiceSearch.setOnClickListener { startVoiceSearch() }
        filterAll.setOnClickListener { selectFilter(Filter.ALL) }
        filterSeries.setOnClickListener { selectFilter(Filter.SERIES) }
        filterMovies.setOnClickListener { selectFilter(Filter.MOVIES) }
        wireControls()
    }

    override fun onStart() {
        super.onStart()
        blockAutoKeyboard = false
        observeJob?.cancel()
        observeJob = scope.launch {
            ItvApplication.instance.repository.observeLibrary().collect { items ->
                catalog = items
                render()
                if (!landed) {
                    landed = true
                    if (hasWindowFocus() && query.text.isNullOrBlank()) openKeyboard(fromUser = false)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !blockAutoKeyboard && query.text.isNullOrBlank()) openKeyboard(fromUser = false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0 && isImeVisible()) {
            blockAutoKeyboard = true
            hideKeyboard()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        hideKeyboard()
        observeJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_VOICE || resultCode != RESULT_OK) return
        val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (spoken.isEmpty()) return
        query.setText(spoken)
        query.setSelection(query.text?.length ?: 0)
        hideKeyboard()
    }

    private fun selectFilter(next: Filter) {
        filter = next
        render()
        focusFirstResult()
    }

    private fun render() {
        val needle = query.text?.toString().orEmpty().trim()
        clearQuery.visibility = if (needle.isEmpty()) View.GONE else View.VISIBLE
        if (needle.isEmpty()) {
            resultsChrome.visibility = View.GONE
            empty.visibility = View.GONE
            val focusedCard = currentFocus?.tag is Long
            results.removeAllViews()
            cards = emptyList()
            wireControls()
            if (focusedCard) query.requestFocus()
            return
        }
        val matches = HomeCatalog.search(catalog, needle)
        val visible = matches.filter { item ->
            when (filter) {
                Filter.ALL -> true
                Filter.SERIES -> !item.isMovie
                Filter.MOVIES -> item.isMovie
            }
        }
        resultsChrome.visibility = View.VISIBLE
        resultsTitle.text = getString(R.string.search_results_for, needle)
        resultCount.text = getString(R.string.search_count_found, persianDigits(visible.size))
        filterAll.text = getString(R.string.search_filter_all, persianDigits(matches.size))
        filterSeries.text = getString(R.string.search_filter_series, persianDigits(matches.count { !it.isMovie }))
        filterMovies.text = getString(R.string.search_filter_movies, persianDigits(matches.count { it.isMovie }))
        styleFilter(filterAll, filter == Filter.ALL)
        styleFilter(filterSeries, filter == Filter.SERIES)
        styleFilter(filterMovies, filter == Filter.MOVIES)
        val hasResults = visible.isNotEmpty()
        empty.setText(if (matches.isEmpty()) R.string.search_empty else R.string.search_filter_empty)
        empty.visibility = if (hasResults) View.GONE else View.VISIBLE
        val focusedId = (currentFocus?.tag as? Long)
        if (!hasResults) {
            results.removeAllViews()
            cards = emptyList()
        } else if (results.width <= 0) {
            results.post { render() }
        } else {
            cards = buildRows(visible)
        }
        wireControls()
        if (focusedId != null) {
            cards.flatten().firstOrNull { it.tag == focusedId }?.requestFocus()
        }
    }

    private fun buildRows(items: List<LibrarySeries>): List<List<View>> {
        results.removeAllViews()
        val gap = (12 * resources.displayMetrics.density).toInt()
        val column = (results.width - gap * (COLUMNS - 1)) / COLUMNS
        val focusPad = resources.getDimensionPixelSize(R.dimen.catalog_focus_pad)
        val posterHeight = LibraryGridLayout.posterFrameHeight(column, focusPad)
        return items.chunked(COLUMNS).mapIndexed { _, row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                clipChildren = false
            }
            val cards = row.map { series -> resultCard(series, column, posterHeight) }
            cards.forEachIndexed { index, card ->
                line.addView(
                    card,
                    LinearLayout.LayoutParams(column, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        if (index > 0) marginStart = gap
                    },
                )
            }
            results.addView(
                line,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = gap
                },
            )
            cards
        }
    }

    private fun resultCard(series: LibrarySeries, column: Int, posterHeight: Int): View {
        val card = layoutInflater.inflate(R.layout.item_search_result, results, false)
        card.tag = series.id
        card.isFocusable = true
        card.isFocusableInTouchMode = true
        card.isClickable = true
        if (Build.VERSION.SDK_INT >= 26) card.defaultFocusHighlightEnabled = false
        card.id = View.generateViewId()
        card.layoutParams = LinearLayout.LayoutParams(column, ViewGroup.LayoutParams.WRAP_CONTENT)
        card.findViewById<View>(R.id.posterFrame).layoutParams =
            card.findViewById<View>(R.id.posterFrame).layoutParams.apply { height = posterHeight }
        card.findViewById<View>(R.id.cardInner).clipRound(10f)
        Pictures.load(
            LibraryGridLayout.coverUrl(series.posterUrl, series.backdropUrl),
            card.findViewById(R.id.poster),
            R.drawable.bg_poster,
        )
        card.findViewById<TextView>(R.id.kind).setText(
            if (series.isMovie) R.string.search_kind_movie else R.string.search_kind_series,
        )
        card.findViewById<TextView>(R.id.title).text = series.title
        val meta = card.findViewById<TextView>(R.id.meta)
        meta.text = catalogMeta(series)
        meta.visibility = if (meta.text.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
        card.setOnClickListener { openDetails(series) }
        return card
    }

    private fun wireControls() {
        val field = buildList {
            add(query)
            if (clearQuery.visibility == View.VISIBLE) add(clearQuery)
            add(voiceSearch)
        }
        wireRtlRow(field)
        field.first().nextFocusRightId = field.first().id
        field.last().nextFocusLeftId = field.last().id
        field.forEach { it.nextFocusUpId = R.id.navSearch }
        val filters = listOf(filterAll, filterSeries, filterMovies)
        wireRtlRow(filters)
        filters.first().nextFocusRightId = filters.first().id
        filters.last().nextFocusLeftId = filters.last().id
        val firstCard = cards.firstOrNull()?.firstOrNull()
        val downTarget = if (resultsChrome.visibility == View.VISIBLE) activeFilter().id else query.id
        field.forEach { it.nextFocusDownId = downTarget }
        filters.forEach {
            it.nextFocusUpId = query.id
            it.nextFocusDownId = firstCard?.id ?: it.id
            it.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && firstCard != null) {
                    firstCard.requestFocus()
                    true
                } else {
                    false
                }
            }
        }
        cards.forEachIndexed { rowIndex, row ->
            wireRtlRow(row)
            row.first().nextFocusRightId = row.first().id
            row.last().nextFocusLeftId = row.last().id
            row.forEachIndexed { column, card ->
                val above = cards.getOrNull(rowIndex - 1)?.getOrNull(column)
                val below = cards.getOrNull(rowIndex + 1)?.getOrNull(column)
                card.nextFocusUpId = above?.id ?: activeFilter().id
                card.nextFocusDownId = below?.id ?: card.id
            }
        }
    }

    private fun activeFilter(): Button = when (filter) {
        Filter.ALL -> filterAll
        Filter.SERIES -> filterSeries
        Filter.MOVIES -> filterMovies
    }

    private fun styleFilter(button: Button, selected: Boolean) {
        button.setBackgroundResource(if (selected) R.drawable.bg_search_chip_on else R.drawable.bg_search_chip)
        button.setTextColor(getColor(if (selected) R.color.bg else R.color.text))
    }

    private fun focusFirstResult() {
        cards.firstOrNull()?.firstOrNull()?.requestFocus()
    }

    private fun openDetails(series: LibrarySeries) {
        startActivity(Intent(this, SeriesActivity::class.java).putExtra(SeriesActivity.EXTRA_ID, series.id))
    }

    private fun leaveQuery(direction: Int): Boolean {
        val next = query.focusSearch(direction)
        if (next != null && next !== query) next.requestFocus()
        return true
    }

    private fun openKeyboard(fromUser: Boolean = true) {
        if (fromUser) blockAutoKeyboard = false
        if (blockAutoKeyboard) return
        val generation = ++keyboardGeneration
        query.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val shown = imm.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT)
        if (!shown) {
            query.post {
                if (generation != keyboardGeneration || blockAutoKeyboard) return@post
                imm.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun isImeVisible(): Boolean {
        if (imeInsetVisible) return true
        if (Build.VERSION.SDK_INT < 30) return false
        val insets = window.decorView.rootWindowInsets ?: return false
        return insets.isVisible(WindowInsets.Type.ime())
    }

    private fun hideKeyboard() {
        keyboardGeneration++
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(query.windowToken, 0)
    }

    private fun startVoiceSearch() {
        if (!voiceSupported()) {
            Toast.makeText(this, R.string.search_voice_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_voice))
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_VOICE)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.search_voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun voiceSupported(): Boolean {
        if (SpeechRecognizer.isRecognitionAvailable(this)) return true
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(packageManager) != null
    }

    private fun prepareFocus(view: View) {
        if (Build.VERSION.SDK_INT >= 26) view.defaultFocusHighlightEnabled = false
        view.isFocusable = true
        view.isFocusableInTouchMode = true
    }

    private fun catalogMeta(series: LibrarySeries): String = if (series.importState != ImportState.DONE) {
        when (series.importState) {
            ImportState.RUNNING -> getString(R.string.importing)
            ImportState.QUEUED -> getString(R.string.import_queued)
            ImportState.ERROR -> series.importError ?: getString(R.string.import_error)
            ImportState.DONE -> ""
        }
    } else if (series.isMovie) {
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

    private enum class Filter { ALL, SERIES, MOVIES }

    companion object {
        private const val REQUEST_VOICE = 41
        private const val COLUMNS = 6
    }
}
