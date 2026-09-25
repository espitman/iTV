package app.itv.prototype.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import app.itv.prototype.ItvApplication
import app.itv.prototype.R
import app.itv.prototype.core.LibraryEpisode
import app.itv.prototype.core.LibrarySeries
import app.itv.prototype.core.NextCountdown
import app.itv.prototype.core.PlaybackRules
import app.itv.prototype.core.persianClock
import app.itv.prototype.core.persianDigits
import app.itv.prototype.data.TelewebionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var playerView: PlayerView
    private lateinit var root: View
    private lateinit var overlay: View
    private lateinit var spinner: ProgressBar
    private lateinit var status: TextView
    private lateinit var retry: Button
    private lateinit var controls: View
    private lateinit var seekLeftFeedback: TextView
    private lateinit var seekRightFeedback: TextView
    private lateinit var nowTitle: TextView
    private lateinit var nowMeta: TextView
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var timelineTitle: TextView
    private lateinit var timeline: DefaultTimeBar
    private lateinit var btnQuality: ImageButton
    private var qualityDialog: AlertDialog? = null
    private var qualityMode = "highest"
    private var scrubbing = false
    private var resumeAfterScrub = false
    private lateinit var btnPlay: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnRestart: ImageButton
    private lateinit var btnSpeed: ImageButton
    private lateinit var btnJump: ImageButton
    private lateinit var speedPanel: LinearLayout
    private lateinit var jumpPanel: View
    private lateinit var jumpHour: Button
    private lateinit var jumpMinute: Button
    private lateinit var jumpSecond: Button
    private lateinit var jumpGo: Button
    private lateinit var skip: Button
    private lateinit var nextCard: View
    private lateinit var nextLabel: TextView
    private lateinit var nextTitle: TextView
    private lateinit var nextPlay: Button
    private lateinit var nextCancel: Button
    private lateinit var resume: View
    private lateinit var resumeBackdrop: ImageView
    private lateinit var resumeTitle: TextView
    private lateinit var resumeBody: TextView
    private lateinit var resumePlay: Button
    private lateinit var resumeRestart: Button

    private var player: ExoPlayer? = null
    private var episode: LibraryEpisode? = null
    private var series: LibrarySeries? = null
    private var nextEpisode: LibraryEpisode? = null
    private var ready = false
    private var controlsVisible = false
    private var nextCountdown = NextCountdown()
    private var resumeRemaining = PlaybackRules.RESUME_COUNTDOWN_SEC
    private var lastSaveAt = 0L
    private var loadGeneration = 0L
    private var jumpH = 0
    private var jumpM = 0
    private var jumpS = 0
    private var firstTitrajeStart = 0
    private var firstTitrajeEnd = 0
    private var lastTitrajeStart = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        bindViews()
        val episodeId = intent.getLongExtra(EXTRA_EPISODE_ID, 0L)
        if (episodeId == 0L) {
            fail(getString(R.string.stream_error))
        } else {
            loadEpisode(episodeId)
        }
    }

    private fun bindViews() {
        playerView = findViewById(R.id.player)
        root = findViewById(R.id.root)
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        overlay = findViewById(R.id.overlay)
        spinner = findViewById(R.id.spinner)
        status = findViewById(R.id.status)
        retry = findViewById(R.id.retry)
        controls = findViewById(R.id.controls)
        seekLeftFeedback = findViewById(R.id.seekLeftFeedback)
        seekRightFeedback = findViewById(R.id.seekRightFeedback)
        nowTitle = findViewById(R.id.nowTitle)
        nowMeta = findViewById(R.id.nowMeta)
        timeCurrent = findViewById(R.id.timeCurrent)
        timeTotal = findViewById(R.id.timeTotal)
        timelineTitle = findViewById(R.id.timelineTitle)
        timeline = findViewById(R.id.timeline)
        btnQuality = findViewById(R.id.btnQuality)
        btnPlay = findViewById(R.id.btnPlay)
        btnNext = findViewById(R.id.btnNext)
        btnForward = findViewById(R.id.btnForward)
        btnRewind = findViewById(R.id.btnRewind)
        btnRestart = findViewById(R.id.btnRestart)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnJump = findViewById(R.id.btnJump)
        speedPanel = findViewById(R.id.speedPanel)
        jumpPanel = findViewById(R.id.jumpPanel)
        jumpHour = findViewById(R.id.jumpHour)
        jumpMinute = findViewById(R.id.jumpMinute)
        jumpSecond = findViewById(R.id.jumpSecond)
        jumpGo = findViewById(R.id.jumpGo)
        skip = findViewById(R.id.skip)
        nextCard = findViewById(R.id.nextCard)
        nextLabel = findViewById(R.id.nextLabel)
        nextTitle = findViewById(R.id.nextTitle)
        nextPlay = findViewById(R.id.nextPlay)
        nextCancel = findViewById(R.id.nextCancel)
        resume = findViewById(R.id.resume)
        resumeBackdrop = findViewById(R.id.resumeBackdrop)
        resumeTitle = findViewById(R.id.resumeTitle)
        resumeBody = findViewById(R.id.resumeBody)
        resumePlay = findViewById(R.id.resumePlay)
        resumeRestart = findViewById(R.id.resumeRestart)
        playerView.isFocusable = false
        playerView.isFocusableInTouchMode = false
        playerView.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        listOf(btnQuality, retry, btnPlay, btnNext, btnForward, btnRewind, btnRestart, btnSpeed, btnJump, jumpHour, jumpMinute, jumpSecond, jumpGo, skip, nextPlay, nextCancel, resumePlay, resumeRestart)
            .forEach { it.bindFocusTint() }
        retry.setOnClickListener { episode?.let { loadEpisode(it.id) } }
        btnPlay.setOnClickListener { togglePlay() }
        btnNext.setOnClickListener { nextEpisode?.let { loadEpisode(it.id) } }
        btnForward.setOnClickListener { seekBy(PlaybackRules.MEDIA_FORWARD_MS) }
        btnRewind.setOnClickListener { seekBy(-PlaybackRules.MEDIA_REWIND_MS) }
        btnRestart.setOnClickListener { player?.seekTo(0); showControls() }
        btnSpeed.setOnClickListener { toggleSpeed() }
        btnJump.setOnClickListener { toggleJump() }
        jumpHour.setOnClickListener { jumpH = (jumpH + 1) % 10; refreshJumpLabels() }
        jumpMinute.setOnClickListener { jumpM = (jumpM + 1) % 60; refreshJumpLabels() }
        jumpSecond.setOnClickListener { jumpS = (jumpS + 1) % 60; refreshJumpLabels() }
        jumpGo.setOnClickListener { applyJump() }
        skip.setOnClickListener { skipCredits() }
        nextPlay.setOnClickListener { nextEpisode?.let { loadEpisode(it.id) } }
        nextCancel.setOnClickListener { cancelNextCountdown() }
        resumePlay.setOnClickListener { startFromResume(false) }
        resumeRestart.setOnClickListener { startFromResume(true) }
        buildSpeedButtons()
        btnQuality.setOnClickListener { showQualityMenu() }
        timeline.setKeyTimeIncrement(10_000L)
        timeline.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                scrubbing = true
                resumeAfterScrub = player?.playWhenReady == true
                player?.pause()
                handler.removeCallbacks(hideRunnable)
            }
            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                timeCurrent.text = persianClock(position)
            }
            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                scrubbing = false
                if (!canceled) player?.seekTo(position)
                if (resumeAfterScrub) player?.play()
                scheduleHide()
            }
        })
        val transport = listOf(btnRestart, btnRewind, btnPlay, btnForward, btnNext, btnQuality, btnSpeed, btnJump)
        transport.forEachIndexed { index, button ->
            button.nextFocusLeftId = transport.getOrNull(index - 1)?.id ?: button.id
            button.nextFocusRightId = transport.getOrNull(index + 1)?.id ?: button.id
            button.nextFocusUpId = timeline.id
        }
        timeline.nextFocusDownId = btnPlay.id
    }

    private fun loadEpisode(episodeId: Long) {
        persistProgress()
        releasePlayer()
        handler.removeCallbacks(tick)
        handler.removeCallbacks(nextTicker)
        handler.removeCallbacks(resumeTick)
        nextCountdown = NextCountdown()
        ready = false
        qualityMode = "highest"
        qualityDialog?.dismiss()
        scrubbing = false
        showOverlay(loading = true, error = null)
        hidePanels()
        val generation = ++loadGeneration
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val ep = ItvApplication.instance.repository.episode(episodeId) ?: return@withContext null
                val series = ItvApplication.instance.repository.series(ep.seriesId)
                Triple(ep, series, series?.let { PlaybackRules.nextPresent(ep, it.episodes) })
            }
            if (generation != loadGeneration) return@launch
            if (loaded == null) {
                fail(getString(R.string.stream_error))
                return@launch
            }
            episode = loaded.first
            series = loaded.second
            nextEpisode = loaded.third
            bindChrome()
            fetchStream(loaded.first, generation)
        }
    }

    private fun fetchStream(item: LibraryEpisode, generation: Long) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ItvApplication.instance.telewebion.resolveStream(
                        ItvApplication.instance.repository.series(item.seriesId)?.kind
                            ?: app.itv.prototype.core.SourceKind.PROGRAM,
                        item.sourceEpisodeId,
                    )
                }
            }
            if (generation != loadGeneration) return@launch
            result.onSuccess { stream ->
                firstTitrajeStart = stream.firstTitrajeStartSec.takeIf { it > 0 } ?: item.firstTitrajeStartSec
                firstTitrajeEnd = stream.firstTitrajeEndSec.takeIf { it > 0 } ?: item.firstTitrajeEndSec
                lastTitrajeStart = stream.lastTitrajeStartSec.takeIf { it > 0 } ?: item.lastTitrajeStartSec
                play(stream.url, item)
            }.onFailure { fail(it.message) }
        }
    }

    private fun play(url: String, item: LibraryEpisode) {
        releasePlayer()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(TelewebionClient.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val source = HlsMediaSource.Factory(http).createMediaSource(MediaItem.fromUri(url))
        val exo = ExoPlayer.Builder(this).build().also { player = it }
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, false)
            .setForceHighestSupportedBitrate(true).build()
        exo.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val selected = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { group -> (0 until group.length).filter { group.isTrackSelected(it) }.map { group.getTrackFormat(it) } }
                    .firstOrNull()
                val height = selected?.height?.takeIf { it > 0 }
                btnQuality.contentDescription = "کیفیت پخش" + (height?.let { "؛ ${persianDigits(it)}p" } ?: "")
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_BUFFERING && !ready && resume.visibility != View.VISIBLE) showOverlay(loading = true, error = null)
                if (state == Player.STATE_READY) {
                    val firstReady = !ready
                    ready = true
                    showOverlay(loading = false, error = null)
                    if (firstReady && resume.visibility != View.VISIBLE) showControls()
                }
                if (state == Player.STATE_ENDED) {
                    persistProgress()
                    if (PlaybackRules.shouldAutoAdvanceOnEnded(nextEpisode != null, nextCountdown.cancelled)) {
                        nextEpisode?.let { loadEpisode(it.id) } ?: showControls()
                    } else {
                        showControls()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlay.contentDescription = getString(if (isPlaying) R.string.pause else R.string.play)
                btnPlay.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                scheduleHide()
            }

            override fun onPlayerError(error: PlaybackException) {
                fail(error.message ?: getString(R.string.stream_error))
            }
        })
        playerView.player = exo
        exo.setMediaSource(source)
        exo.prepare()
        val resumePos = item.lastPositionMs
        if (PlaybackRules.shouldPromptResume(resumePos)) {
            exo.playWhenReady = false
            exo.seekTo(resumePos)
            showResume(item)
        } else {
            exo.playWhenReady = true
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun bindChrome() {
        val item = episode ?: return
        val currentSeries = series
        nowTitle.text = currentSeries?.title ?: item.title
        nowMeta.text = if (currentSeries?.isMovie == true) "فیلم سینمایی" else listOfNotNull(
            if (currentSeries != null && currentSeries.presentSeasonCount > 1) getString(R.string.season_label, persianDigits(item.seasonNumber)) else null,
            item.label(),
        ).joinToString(" · ")
        timelineTitle.text = if (currentSeries?.isMovie == true) currentSeries.title else "${currentSeries?.title ?: item.title} · ${item.label()}"
        btnNext.isEnabled = nextEpisode != null
        btnNext.visibility = if (currentSeries?.isMovie == true) View.GONE else View.VISIBLE
        btnForward.nextFocusRightId = if (currentSeries?.isMovie == true) btnQuality.id else btnNext.id
        btnQuality.nextFocusLeftId = if (currentSeries?.isMovie == true) btnForward.id else btnNext.id
        nextTitle.text = nextEpisode?.label().orEmpty()
        app.itv.prototype.Pictures.load(nextEpisode?.imageUrl, findViewById(R.id.nextImage), R.drawable.bg_poster)
    }

    private fun showResume(item: LibraryEpisode) {
        showOverlay(loading = false, error = null)
        app.itv.prototype.Pictures.load(series?.backdropUrl ?: item.imageUrl, resumeBackdrop, R.drawable.bg_cinema)
        resume.visibility = View.VISIBLE
        controls.visibility = View.GONE
        resumeRemaining = PlaybackRules.RESUME_COUNTDOWN_SEC
        resumeTitle.text = getString(R.string.resume_title, item.label())
        resumeBody.text = getString(R.string.resume_body, persianClock(item.lastPositionMs))
        updateResumeButton()
        resumePlay.post { resumePlay.requestFocus() }
        handler.removeCallbacks(resumeTick)
        handler.postDelayed(resumeTick, 1000)
    }

    private val resumeTick = object : Runnable {
        override fun run() {
            if (resume.visibility != View.VISIBLE) return
            if (!ready) { handler.postDelayed(this, 1000); return }
            resumeRemaining -= 1
            if (resumeRemaining <= 0) {
                startFromResume(false)
            } else {
                updateResumeButton()
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun updateResumeButton() {
        resumePlay.text = "${getString(R.string.resume_action)} ${persianDigits(resumeRemaining)}"
    }

    private fun startFromResume(fromStart: Boolean) {
        handler.removeCallbacks(resumeTick)
        resume.visibility = View.GONE
        player?.let {
            if (fromStart) it.seekTo(0)
            it.playWhenReady = true
            it.play()
        }
        showControls()
    }

    private val tick = object : Runnable {
        override fun run() {
            val exo = player
            if (exo != null && ready) {
                val pos = exo.currentPosition
                val dur = exo.duration.coerceAtLeast(0L)
                if (!scrubbing) timeCurrent.text = persianClock(pos)
                timeTotal.text = if (dur > 0) persianClock(dur) else "—"
                timeline.setDuration(dur)
                timeline.setEnabled(dur > 0 && exo.isCurrentMediaItemSeekable)
                if (!scrubbing) timeline.setPosition(pos)
                timeline.setBufferedPosition(exo.bufferedPosition)
                if (System.currentTimeMillis() - lastSaveAt >= PlaybackRules.SAVE_INTERVAL_MS) persistProgress()
                updateSkip(pos, dur)
                if (!scrubbing && qualityDialog?.isShowing != true) updateNextCard()
            }
            handler.postDelayed(this, 250)
        }
    }

    private fun updateSkip(pos: Long, dur: Long) {
        if (resume.visibility == View.VISIBLE) {
            skip.visibility = View.GONE
            return
        }
        val intro = PlaybackRules.skipIntroActive(pos, firstTitrajeStart, firstTitrajeEnd)
        val outro = PlaybackRules.skipOutroActive(pos, lastTitrajeStart, dur)
        val locked = nextLocked(dur, pos)
        if ((intro || outro) && !locked && !controlsVisible) {
            skip.visibility = View.VISIBLE
            skip.text = getString(if (intro) R.string.skip_intro else R.string.skip_outro)
        } else {
            skip.visibility = View.GONE
        }
    }

    private fun nextLocked(duration: Long, position: Long): Boolean =
        PlaybackRules.nextLockActive(nextEpisode != null, nextCountdown.cancelled, duration, position)

    private fun updateNextCard() {
        val exo = player ?: return
        val dur = exo.duration
        val pos = exo.currentPosition
        val next = nextEpisode
        if (next == null || nextCountdown.cancelled || dur <= 0) {
            nextCard.visibility = View.GONE
            stopNextTicker()
            return
        }
        val remain = (dur - pos).coerceAtLeast(0L)
        val locked = remain < PlaybackRules.NEXT_LOCK_MS
        val near = remain < PlaybackRules.NEXT_CARD_MS && controlsVisible
        val previous = nextCountdown
        nextCountdown = nextCountdown.onWindow(locked)
        if (nextCountdown.started && !previous.started) {
            startNextTicker()
        } else if (!nextCountdown.started) {
            stopNextTicker()
        }
        nextCard.visibility = if (locked || near) View.VISIBLE else View.GONE
        if (locked) {
            if (!isNextCardFocus()) nextPlay.requestFocus()
            nextLabel.text = getString(R.string.next_countdown, persianDigits(nextCountdown.remainingSec))
            if (nextCountdown.shouldAutoPlay) loadEpisode(next.id)
        } else {
            nextLabel.text = getString(R.string.next_episode)
        }
    }

    private fun startNextTicker() {
        handler.removeCallbacks(nextTicker)
        handler.postDelayed(nextTicker, 1000L)
    }

    private fun stopNextTicker() {
        handler.removeCallbacks(nextTicker)
    }

    private val nextTicker = object : Runnable {
        override fun run() {
            if (!nextCountdown.started || nextCountdown.cancelled) return
            nextCountdown = nextCountdown.tick()
            updateNextCard()
            if (nextCountdown.started && !nextCountdown.shouldAutoPlay && !nextCountdown.cancelled) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private fun cancelNextCountdown() {
        stopNextTicker()
        nextCountdown = nextCountdown.cancel()
        updateNextCard()
    }

    private fun skipCredits() {
        val exo = player ?: return
        val pos = exo.currentPosition
        if (PlaybackRules.skipIntroActive(pos, firstTitrajeStart, firstTitrajeEnd)) {
            exo.seekTo(PlaybackRules.skipIntroTargetMs(firstTitrajeEnd))
        } else if (PlaybackRules.skipOutroActive(pos, lastTitrajeStart, exo.duration)) {
            exo.seekTo(exo.duration.coerceAtLeast(0L))
        }
    }

    private fun togglePlay() {
        val exo = player ?: return
        if (exo.isPlaying) exo.pause() else exo.play()
        showControls()
    }

    private fun seekBy(delta: Long, revealControls: Boolean = controlsVisible) {
        val exo = player ?: return
        if (!exo.isCurrentMediaItemSeekable) return
        val upperBound = exo.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (exo.currentPosition + delta).coerceIn(0L, upperBound)
        exo.seekTo(target)
        if (revealControls) showControls() else showSeekFeedback(delta)
    }

    private fun showSeekFeedback(delta: Long) {
        val showing = if (delta > 0L) seekRightFeedback else seekLeftFeedback
        val other = if (delta > 0L) seekLeftFeedback else seekRightFeedback
        other.visibility = View.GONE
        showing.text = (if (delta > 0L) "+" else "−") + persianDigits(kotlin.math.abs(delta) / 1000L)
        showing.visibility = View.VISIBLE
        handler.removeCallbacks(hideSeekFeedback)
        handler.postDelayed(hideSeekFeedback, 1_500L)
    }

    private val hideSeekFeedback = Runnable {
        seekLeftFeedback.visibility = View.GONE
        seekRightFeedback.visibility = View.GONE
    }

    private fun showControls() {
        if (resume.visibility == View.VISIBLE) return
        controlsVisible = true
        controls.visibility = View.VISIBLE
        if (!speedOpen() && !jumpOpen() && nextCard.visibility != View.VISIBLE && !isControlFocus()) {
            btnPlay.post { if (!isControlFocus()) btnPlay.requestFocus() }
        }
        scheduleHide()
    }

    private fun hideControls() {
        if (speedOpen() || jumpOpen() || scrubbing || qualityDialog?.isShowing == true) return
        if (controls.hasFocus()) root.requestFocus()
        controlsVisible = false
        controls.visibility = View.GONE
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        if (controlsVisible && !speedOpen() && !jumpOpen() && !scrubbing && qualityDialog?.isShowing != true) {
            handler.postDelayed(hideRunnable, PlaybackRules.CONTROLS_HIDE_MS)
        }
    }

    private val hideRunnable = Runnable { hideControls() }

    private fun showQualityMenu() {
        val exo = player ?: return
        val choices = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group -> (0 until group.length).filter { group.isTrackSupported(it) }
                .map { index -> Triple(group, index, group.getTrackFormat(index)) } }
            .sortedWith(compareByDescending<Triple<Tracks.Group, Int, androidx.media3.common.Format>> { it.third.height }.thenByDescending { it.third.bitrate })
        val labels = listOf("بالاترین کیفیت (پیش‌فرض)", "خودکار") + choices.map { (_, _, format) ->
            if (format.height > 0) "${persianDigits(format.height)}p" else format.label ?: "${persianDigits(format.bitrate / 1000)} kbps"
        }
        val selected = when (qualityMode) {
            "highest" -> 0
            "auto" -> 1
            else -> choices.indexOfFirst { it.first.isTrackSelected(it.second) }.let { if (it < 0) 0 else it + 2 }
        }
        handler.removeCallbacks(hideRunnable)
        qualityDialog = AlertDialog.Builder(this).setTitle("کیفیت پخش")
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                val params = exo.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .setForceHighestSupportedBitrate(which == 0)
                qualityMode = when (which) { 0 -> "highest"; 1 -> "auto"; else -> "manual" }
                if (which >= 2) {
                    val (group, index, _) = choices[which - 2]
                    params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                }
                val position = exo.currentPosition
                val wasPlaying = exo.playWhenReady
                // Recreate the decoder on an explicit resolution change. Some TV codecs
                // report adaptive support but retain the previous output buffer dimensions.
                exo.stop()
                exo.trackSelectionParameters = params.build()
                exo.seekTo(position)
                exo.prepare()
                exo.playWhenReady = wasPlaying
                dialog.dismiss()
            }.setNegativeButton("بستن", null).create()
        qualityDialog?.setOnDismissListener { btnQuality.requestFocus(); scheduleHide() }
        qualityDialog?.show()
    }

    private fun toggleSpeed() {
        speedPanel.visibility = if (speedOpen()) View.GONE else View.VISIBLE
        if (speedOpen()) speedPanel.getChildAt(0)?.requestFocus()
        scheduleHide()
    }

    private fun toggleJump() {
        jumpPanel.visibility = if (jumpOpen()) View.GONE else View.VISIBLE
        if (jumpOpen()) {
            val pos = ((player?.currentPosition ?: 0L) / 1000L).toInt()
            jumpH = pos / 3600
            jumpM = (pos % 3600) / 60
            jumpS = pos % 60
            refreshJumpLabels()
            jumpMinute.requestFocus()
        }
        scheduleHide()
    }

    private fun buildSpeedButtons() {
        speedPanel.removeAllViews()
        PlaybackRules.playbackSpeeds.forEach { speed ->
            val button = Button(this)
            button.text = persianDigits(speed.toString().trimEnd('0').trimEnd('.') + "×")
            button.background = getDrawable(R.drawable.bg_button)
            button.isFocusable = true
            button.bindFocusTint()
            button.setOnClickListener {
                player?.setPlaybackSpeed(speed)
                speedPanel.visibility = View.GONE
                showControls()
            }
            speedPanel.addView(button)
        }
    }

    private fun refreshJumpLabels() {
        jumpHour.text = persianDigits("%02d".format(jumpH))
        jumpMinute.text = persianDigits("%02d".format(jumpM))
        jumpSecond.text = persianDigits("%02d".format(jumpS))
    }

    private fun applyJump() {
        player?.seekTo(((jumpH * 3600L) + (jumpM * 60L) + jumpS) * 1000L)
        jumpPanel.visibility = View.GONE
        showControls()
    }

    private fun speedOpen() = speedPanel.visibility == View.VISIBLE
    private fun jumpOpen() = jumpPanel.visibility == View.VISIBLE

    private fun hidePanels() {
        speedPanel.visibility = View.GONE
        jumpPanel.visibility = View.GONE
        nextCard.visibility = View.GONE
        skip.visibility = View.GONE
        resume.visibility = View.GONE
        controls.visibility = View.GONE
        controlsVisible = false
        handler.removeCallbacks(hideSeekFeedback)
        hideSeekFeedback.run()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            return handleBack()
        }
        val horizontalKey = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        val canQuickSeek = ready && overlay.visibility != View.VISIBLE && resume.visibility != View.VISIBLE &&
            !controlsVisible && !speedOpen() && !jumpOpen() &&
            !(nextCard.visibility == View.VISIBLE && nextLocked(player?.duration ?: 0L, player?.currentPosition ?: 0L))
        if (horizontalKey && canQuickSeek) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val step = PlaybackRules.dpadSeekStep(event.eventTime - event.downTime)
                seekBy(if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) step else -step, revealControls = false)
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && controlsVisible) scheduleHide()
        if (timeline.isFocused && controlsVisible && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN && ready && overlay.visibility != View.VISIBLE && resume.visibility != View.VISIBLE) {
            val locked = nextCard.visibility == View.VISIBLE &&
                nextLocked(player?.duration ?: 0L, player?.currentPosition ?: 0L)
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (locked) {
                        if (!isNextCardFocus()) nextPlay.requestFocus()
                        return super.dispatchKeyEvent(event)
                    }
                    if (isFocusedClickable()) return super.dispatchKeyEvent(event)
                    if (!controlsVisible) {
                        showControls()
                        return true
                    }
                    togglePlay()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (isFocusedClickable()) return super.dispatchKeyEvent(event)
                    if (!controlsVisible) {
                        showControls()
                        return true
                    }
                    togglePlay()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.play(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (locked) return dispatchLockedNavigation(event)
                    if (speedOpen() || jumpOpen() || isFocusedClickable()) {
                        return super.dispatchKeyEvent(event)
                    }
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (!controlsVisible) return true
                    }
                    if (!controlsVisible && (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
                        showControls()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-PlaybackRules.MEDIA_REWIND_MS); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(PlaybackRules.MEDIA_FORWARD_MS); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun dispatchLockedNavigation(event: KeyEvent): Boolean {
        if (!isNextCardFocus()) {
            nextPlay.requestFocus()
            return true
        }
        val from = currentFocus
        val handled = super.dispatchKeyEvent(event)
        if (!isNextCardFocus()) {
            (from ?: nextPlay).requestFocus()
        }
        return handled
    }

    private fun isFocusedClickable(): Boolean {
        val focus = currentFocus ?: return false
        if (focus === playerView) return false
        return focus.isShownClickableFocus()
    }

    private fun isControlFocus(): Boolean {
        val focus = currentFocus ?: return false
        return focus !== playerView && focus.isShown && (
            focus === btnPlay || focus === btnNext || focus === btnForward || focus === btnRewind ||
                focus === timeline || focus === btnQuality || focus === btnRestart || focus === btnSpeed || focus === btnJump ||
                focus.parent === speedPanel || focus === skip || isNextCardFocus()
            )
    }

    private fun isNextCardFocus(): Boolean {
        val focus = currentFocus ?: return false
        return focus === nextPlay || focus === nextCancel
    }

    private fun handleBack(): Boolean {
        when {
            scrubbing -> { timeline.isEnabled = false; timeline.isEnabled = true; return true }
            resume.visibility == View.VISIBLE -> { startFromResume(false); return true }
            jumpOpen() -> { jumpPanel.visibility = View.GONE; return true }
            speedOpen() -> { speedPanel.visibility = View.GONE; return true }
            controlsVisible -> { hideControls(); return true }
            else -> { persistProgress(flush = true); finish(); return true }
        }
    }

    private fun persistProgress(flush: Boolean = false) {
        val item = episode ?: return
        val exo = player ?: return
        lastSaveAt = System.currentTimeMillis()
        val duration = exo.duration.coerceAtLeast(0L)
        val position = exo.currentPosition.coerceAtLeast(0L)
        val repository = ItvApplication.instance.repository
        repository.submitProgress(item.id, position, duration)
        if (flush) repository.flushProgress()
    }

    private fun showOverlay(loading: Boolean, error: String?) {
        val visible = loading || error != null
        overlay.visibility = if (visible) View.VISIBLE else View.GONE
        spinner.visibility = if (loading) View.VISIBLE else View.GONE
        retry.visibility = if (error != null) View.VISIBLE else View.GONE
        if (error != null) {
            status.text = error
            retry.post { retry.requestFocus() }
        } else if (loading) {
            status.setText(R.string.loading)
        }
    }

    private fun fail(message: String?) {
        ready = false
        qualityMode = "highest"
        qualityDialog?.dismiss()
        scrubbing = false
        showOverlay(loading = false, error = message?.ifBlank { null } ?: getString(R.string.stream_error))
    }

    override fun onPause() {
        persistProgress(flush = true)
        super.onPause()
    }

    override fun onStop() {
        persistProgress(flush = true)
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        persistProgress(flush = true)
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        scope.cancel()
        super.onDestroy()
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_SERIES_ID = "series_id"
    }
}
