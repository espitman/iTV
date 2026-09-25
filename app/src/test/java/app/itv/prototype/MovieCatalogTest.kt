package app.itv.prototype

import app.itv.prototype.core.SourceKind
import app.itv.prototype.core.TelewebionUrl
import app.itv.prototype.data.TelewebionClient
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class MovieCatalogTest {
    private fun client(type: String) = TelewebionClient(getJson = { url ->
        assertFalse("A movie must not need the serial endpoint", url.contains("get-serial"))
        JSONObject().put("body", JSONObject().put("content", JSONArray().put(JSONObject()
            .put("content_id", "0xc20b845").put("content_type", type)
            .put("persian_title", "مصادره").put("duration", 87)
            .put("media", JSONObject().put("vertical_poster", "/sites/default/poster.jpg").put("main_big_poster", "/sites/default/wide.jpg"))
            .put("titraje", JSONObject().put("last_titraje_start", 5222))
            .put("stream", JSONObject().put("telewebion", "https://cdna.telewebion.net/namayesh/episode/0x16b631c3/playlist.m3u8")))))
    })
    @Test fun movieHasOnePlayableItemWithoutEpisodeNumber() {
        val client = client("MOVIE")
        val movie = client.loadMovie("0xc20b845")
        assertEquals("0xc20b845", movie.sourceEpisodeId)
        assertNull(movie.displayNumber)
        assertEquals("مصادره", movie.title)
        assertEquals(5222, movie.lastTitrajeStartSec)
        val (preview, seasons) = client.loadProductSeasons("0xc20b845")
        assertTrue(preview.isMovie)
        assertEquals(87, preview.durationMinutes)
        assertEquals("https://gateway.telewebion.net/sites/default/poster.jpg", preview.posterUrl)
        assertEquals("https://gateway.telewebion.net/sites/default/wide.jpg", preview.backdropUrl)
        assertTrue(seasons.isEmpty())
        assertTrue(client.resolveStream(SourceKind.PRODUCT, movie.sourceEpisodeId).url.endsWith("playlist.m3u8"))
    }
    @Test fun sameProductIdIsNotEnoughToAssumeMovie() {
        assertFalse(client("SERIAL").loadProductSeasons("0xc20b845").first.isMovie)
        assertFalse(client("").loadProductSeasons("0xc20b845").first.isMovie)
    }
}
