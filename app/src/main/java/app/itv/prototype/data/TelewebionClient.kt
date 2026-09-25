package app.itv.prototype.data

import app.itv.prototype.core.AllowedSource
import app.itv.prototype.core.CatalogPage
import app.itv.prototype.core.CatalogPaging
import app.itv.prototype.core.CatalogPreview
import app.itv.prototype.core.CatalogRows
import app.itv.prototype.core.IncomingSeason
import app.itv.prototype.core.PlaybackRules
import app.itv.prototype.core.ResolvedStream
import app.itv.prototype.core.SourceKind
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TelewebionClient(
    private val userAgent: String = USER_AGENT,
    private val getJson: (String) -> JSONObject = ::defaultGet,
) {
    fun preview(source: AllowedSource): CatalogPreview {
        return when (source.kind) {
            SourceKind.PROGRAM -> {
                val root = getJson(programEpisodesUrl(source.sourceId, 0))
                val program = CatalogMapper.requireProgram(root)
                val episodes = program.optJSONArray("episodes") ?: JSONArray()
                val (title, poster) = CatalogMapper.programMeta(root)
                CatalogPreview(
                    kind = source.kind,
                    sourceId = source.sourceId,
                    title = title.ifBlank { source.sourceId },
                    posterUrl = poster,
                    description = null,
                    seasonCount = if (episodes.length() == 0) 0 else 1,
                    episodeCount = if (episodes.length() == 0) 0 else null,
                )
            }
            SourceKind.PRODUCT -> {
                val content = productContent(source.sourceId)
                val (title, poster, description) = CatalogMapper.productMeta(content)
                val seasons = CatalogMapper.seasonsFromContent(content)
                CatalogPreview(
                    kind = source.kind,
                    sourceId = source.sourceId,
                    title = title.ifBlank { source.sourceId },
                    posterUrl = poster,
                    description = description,
                    seasonCount = seasons.size,
                    episodeCount = if (CatalogMapper.isMovie(content)) 1 else null,
                    isMovie = CatalogMapper.isMovie(content),
                    durationMinutes = content.optInt("duration", 0),
                    backdropUrl = CatalogMapper.productBackdrop(content),
                )
            }
        }
    }

    fun loadProgramPage(sourceId: String, offset: Int): CatalogPage {
        val root = getJson(programEpisodesUrl(sourceId, offset))
        val program = CatalogMapper.requireProgram(root)
        val raw = program.optJSONArray("episodes") ?: JSONArray()
        val episodes = CatalogMapper.programEpisodes(raw, offset)
        val (title, poster) = CatalogMapper.programMeta(root)
        val seasons = if (raw.length() == 0) {
            emptyList()
        } else {
            listOf(IncomingSeason(seasonNumber = 1, sourceOrder = 0, title = null))
        }
        return CatalogPage(
            title = title,
            posterUrl = poster,
            description = null,
            seasons = seasons,
            episodes = episodes,
            finished = raw.length() == 0,
            rowCount = raw.length(),
        )
    }

    fun loadProductSeasons(sourceId: String): Pair<CatalogPreview, List<IncomingSeason>> {
        val content = productContent(sourceId)
        val (title, poster, description) = CatalogMapper.productMeta(content)
        val seasons = CatalogMapper.seasonsFromContent(content)
        val preview = CatalogPreview(
            kind = SourceKind.PRODUCT,
            sourceId = sourceId,
            title = title.ifBlank { sourceId },
            posterUrl = poster,
            description = description,
            seasonCount = seasons.size,
            episodeCount = if (CatalogMapper.isMovie(content)) 1 else null,
            isMovie = CatalogMapper.isMovie(content),
            durationMinutes = content.optInt("duration", 0),
            backdropUrl = CatalogMapper.productBackdrop(content),
        )
        return preview to seasons
    }

    fun loadProductSeasonPage(sourceId: String, seasonNumber: Int, offset: Int): CatalogRows {
        val root = getJson(productSerialUrl(sourceId, seasonNumber, offset))
        val parts = CatalogMapper.productSerialParts(root)
        return CatalogRows(
            episodes = CatalogMapper.productParts(parts, seasonNumber, offset),
            rowCount = parts.length(),
        )
    }

    fun resolveStream(kind: SourceKind, sourceEpisodeId: String): ResolvedStream {
        return when (kind) {
            SourceKind.PROGRAM -> resolveProgramStream(sourceEpisodeId)
            SourceKind.PRODUCT -> resolveProductStream(sourceEpisodeId)
        }
    }

    private fun resolveProgramStream(episodeId: String): ResolvedStream {
        val root = getJson("$BASE/kandoo/episode/getEpisodeDetail/?EpisodeId=$episodeId")
        val episode = root.optJSONObject("body")
            ?.optJSONArray("queryEpisode")
            ?.optJSONObject(0)
            ?: error("جزئیات این قسمت در دسترس نیست")
        if (episode.has("enable") && !episode.optBoolean("enable", false)) {
            error("این قسمت در دسترس نیست")
        }
        val channel = episode.optJSONObject("channel")?.optString("descriptor").orEmpty()
        require(channel.isNotBlank()) { "کانال پخش این قسمت مشخص نشد" }
        val url = PlaybackRules.acceptHlsUrl("https://cdna.telewebion.net/$channel/episode/$episodeId/playlist.m3u8")
            ?: error("نشانی استریم نامعتبر است")
        return ResolvedStream(
            url = url,
            firstTitrajeStartSec = CatalogMapper.titraje(episode, "first_titraje_start"),
            firstTitrajeEndSec = CatalogMapper.titraje(episode, "first_titraje_end"),
            lastTitrajeStartSec = CatalogMapper.titraje(episode, "last_titraje_start"),
        )
    }

    private fun resolveProductStream(contentId: String): ResolvedStream {
        val content = productContent(contentId)
        val stream = content.optJSONObject("stream")?.optString("telewebion")
        val url = PlaybackRules.acceptHlsUrl(stream)
            ?: error("استریم این قسمت در دسترس نیست")
        return ResolvedStream(
            url = url,
            firstTitrajeStartSec = CatalogMapper.titraje(content, "first_titraje_start"),
            firstTitrajeEndSec = CatalogMapper.titraje(content, "first_titraje_end"),
            lastTitrajeStartSec = CatalogMapper.titraje(content, "last_titraje_start"),
        )
    }

    fun loadMovie(contentId: String) = CatalogMapper.movieEpisode(productContent(contentId))

    private fun productContent(contentId: String): JSONObject {
        val root = getJson("$BASE/kandoo/vod/content/get-content?content_id=$contentId&q=abc")
        return root.optJSONObject("body")?.optJSONArray("content")?.optJSONObject(0)
            ?: error("پاسخ محصول خالی است")
    }

    companion object {
        const val BASE = "https://gateway.telewebion.net"
        const val USER_AGENT = "iTV/0.3 AndroidTV"
        const val PAGE_SIZE = CatalogPaging.PAGE_SIZE

        fun programEpisodesUrl(programId: String, offset: Int): String =
            "$BASE/kandoo/program/getEpisodesByProgramID/?ProgramID=$programId&First=$PAGE_SIZE&Offset=$offset"

        fun productSerialUrl(contentId: String, season: Int, offset: Int): String =
            "$BASE/kandoo/vod/content/get-serial?content_id=$contentId&season=$season&first=$PAGE_SIZE&offset=$offset"

        private fun defaultGet(url: String): JSONObject {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("خطای منبع: $code")
                return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }
    }
}
