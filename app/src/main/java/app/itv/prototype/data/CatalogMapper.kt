package app.itv.prototype.data

import app.itv.prototype.core.IncomingEpisode
import app.itv.prototype.core.IncomingSeason
import app.itv.prototype.core.PlaybackRules
import app.itv.prototype.core.firstNumberToken
import org.json.JSONArray
import org.json.JSONObject

enum class TelewebionImageKind(val folder: String) {
    PROGRAM("programImages"),
    EPISODE("episodeImages"),
    VOD("vodBannerImages"),
}

object CatalogMapper {
    private val imageToken = Regex("^[A-Za-z0-9_-]{1,256}$")

    fun text(json: JSONObject?, vararg keys: String): String {
        if (json == null) return ""
        keys.forEach { key ->
            val value = json.opt(key) ?: return@forEach
            if (value is String && value.isNotBlank() && value != "null") return value.trim()
            if (value is Number) return value.toString()
        }
        return ""
    }

    fun imageUrl(raw: String, kind: TelewebionImageKind): String? {
        val value = raw.trim()
        if (value.isEmpty() || value == "null") return null
        if (value.startsWith("http")) return value
        if (value.startsWith("/sites/default")) return "https://gateway.telewebion.net$value"
        if (imageToken.matches(value)) return "https://static.telewebion.net/${kind.folder}/$value/default"
        return null
    }

    fun productImageUrl(item: JSONObject, episode: Boolean): String? {
        val media = item.optJSONObject("media")
        val keys = if (episode) {
            arrayOf(
                "special_image_part",
                "horizontal_small_poster",
                "horizontal_big_poster",
                "vertical_poster",
                "main_small_poster",
                "main_big_poster",
            )
        } else {
            arrayOf(
                "horizontal_big_poster",
                "vertical_poster",
                "main_small_poster",
                "main_big_poster",
                "horizontal_small_poster",
            )
        }
        if (media != null) {
            keys.forEach { key ->
                imageUrl(text(media, key), TelewebionImageKind.VOD)?.let { return it }
            }
        }
        return imageUrl(text(item, "image", "cover", "poster", "cover_image"), TelewebionImageKind.VOD)
    }

    fun displayNumber(item: JSONObject, title: String): String? {
        val explicit = text(
            item,
            "episode",
            "display_number",
            "episode_no",
            "episode_number",
            "EpisodeNo",
            "number",
        )
        if (explicit.isNotEmpty()) return firstNumberToken(explicit) ?: explicit
        return firstNumberToken(title)
    }

    fun titraje(item: JSONObject, vararg keys: String): Int {
        val nested = item.optJSONObject("titraje")
        for (key in keys) {
            for (source in listOfNotNull(nested, item)) {
                if (!source.has(key) || source.isNull(key)) continue
                val value = source.optInt(key, 0)
                if (value > 0) return value
            }
        }
        return 0
    }

    fun requireProgram(root: JSONObject): JSONObject {
        val body = root.optJSONObject("body") ?: error("پاسخ برنامه ناقص است")
        val programs = body.optJSONArray("queryProgram") ?: error("پاسخ برنامه ناقص است")
        return programs.optJSONObject(0) ?: error("پاسخ برنامه ناقص است")
    }

    fun productSerialParts(root: JSONObject): JSONArray {
        val body = root.optJSONObject("body") ?: error("پاسخ فصل ناقص است")
        val content = body.opt("content")
        val container = when (content) {
            is JSONArray -> content.optJSONObject(0) ?: error("پاسخ فصل ناقص است")
            is JSONObject -> content
            else -> null
        }
        if (container != null && container.has("serial_parts") && !container.isNull("serial_parts")) {
            return container.optJSONArray("serial_parts") ?: error("پاسخ فصل ناقص است")
        }
        error("پاسخ فصل ناقص است")
    }

    fun seasonsFromContent(content: JSONObject): List<IncomingSeason> {
        val array = content.optJSONArray("sorted_seasons") ?: JSONArray()
        return (0 until array.length()).map { index ->
            val value = array.opt(index)
            val number = when (value) {
                is Number -> value.toInt()
                is JSONObject -> value.optInt("season", value.optInt("id", index + 1))
                else -> value.toString().toIntOrNull() ?: (index + 1)
            }
            IncomingSeason(seasonNumber = number, sourceOrder = index, title = "فصل $number")
        }
    }

    fun programEpisodes(episodes: JSONArray, startOrder: Int, seasonNumber: Int = 1): List<IncomingEpisode> {
        return (0 until episodes.length()).map { index ->
            val item = episodes.getJSONObject(index)
            val title = text(item, "title", "Title", "name")
            val id = text(item, "EpisodeID", "episode_id", "id")
            IncomingEpisode(
                sourceEpisodeId = id,
                sourceOrder = startOrder + index,
                seasonNumber = seasonNumber,
                displayNumber = displayNumber(item, title),
                title = title.ifBlank { id },
                imageUrl = imageUrl(text(item, "image", "Image", "cover"), TelewebionImageKind.EPISODE),
                firstTitrajeStartSec = titraje(item, "first_titraje_start"),
                firstTitrajeEndSec = titraje(item, "first_titraje_end"),
                lastTitrajeStartSec = titraje(item, "last_titraje_start"),
            )
        }.filter { it.sourceEpisodeId.isNotBlank() }
    }

    fun productParts(parts: JSONArray, seasonNumber: Int, startOrder: Int): List<IncomingEpisode> {
        return (0 until parts.length()).map { index ->
            val item = parts.getJSONObject(index)
            val title = text(item, "persian_title", "special_title", "english_title", "title", "name")
            val id = text(item, "content_id", "contentId", "id")
            IncomingEpisode(
                sourceEpisodeId = id,
                sourceOrder = startOrder + index,
                seasonNumber = seasonNumber,
                displayNumber = displayNumber(item, title),
                title = title.ifBlank { id },
                imageUrl = productImageUrl(item, episode = true),
                firstTitrajeStartSec = PlaybackRules.positiveMarker(titraje(item, "first_titraje_start")),
                firstTitrajeEndSec = PlaybackRules.positiveMarker(titraje(item, "first_titraje_end")),
                lastTitrajeStartSec = PlaybackRules.positiveMarker(titraje(item, "last_titraje_start")),
            )
        }.filter { it.sourceEpisodeId.isNotBlank() }
    }

    fun programMeta(root: JSONObject): Pair<String, String?> {
        val program = requireProgram(root)
        val title = text(program, "title", "Title", "name", "program_title")
        val poster = imageUrl(text(program, "image", "Image"), TelewebionImageKind.PROGRAM)
            ?: imageUrl(text(program, "cover", "poster", "background"), TelewebionImageKind.PROGRAM)
        return title to poster
    }

    fun isMovie(content: JSONObject): Boolean = text(content, "content_type").equals("MOVIE", ignoreCase = true)

    fun productBackdrop(content: JSONObject): String? {
        val media = content.optJSONObject("media")
        return imageUrl(text(media, "main_big_poster", "horizontal_big_poster", "main_small_poster"), TelewebionImageKind.VOD)
    }

    fun movieEpisode(content: JSONObject): IncomingEpisode {
        require(isMovie(content)) { "این عنوان فیلم سینمایی نیست" }
        return IncomingEpisode(
            sourceEpisodeId = text(content, "content_id"), sourceOrder = 0, seasonNumber = 1,
            displayNumber = null, title = text(content, "persian_title", "title"),
            imageUrl = productBackdrop(content),
            firstTitrajeStartSec = titraje(content, "first_titraje_start"),
            firstTitrajeEndSec = titraje(content, "first_titraje_end"),
            lastTitrajeStartSec = titraje(content, "last_titraje_start"),
        )
    }

    fun productMeta(content: JSONObject): Triple<String, String?, String?> {
        val title = text(content, "persian_title", "title", "name", "Title")
        val poster = (if (isMovie(content)) imageUrl(text(content.optJSONObject("media"), "vertical_poster"), TelewebionImageKind.VOD) else null) ?: productImageUrl(content, episode = false)
        val description = text(content, "story", "description", "summary", "short_description").ifBlank { null }
        return Triple(title, poster, description)
    }
}
