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

    @Test fun movieCreditsAreIndividualPeopleWithResolvedPortraits() {
        val avatar = "/sites/default/files/images/biography/avatar/sample.jpg"
        val content = JSONObject()
            .put("content_type", "MOVIE")
            .put("director", JSONArray().put(credit("رخشان بنی‌اعتماد", "", avatar)))
            .put("writer", JSONArray().put(credit("فرید مصطفوی", "نویسنده", "https://cdn.example/writer.jpg")))
            .put("actors", JSONArray()
                .put(credit("مهتاب کرامتی", "بازیگر"))
                .put(credit("محمدرضا فروتن", "بازیگر", "/sites/default/files/images/biography/avatar/1399/parviz parastuyi.jpg"))
                .put(JSONObject().put("person", JSONObject()).put("role", JSONObject().put("title", "بازیگر"))))
            .put("producer", JSONArray().put(credit("جهانگیر کوثری", "تهیه‌کننده")))
            .put("composer", JSONArray().put(credit("کریستف رضاعی", "آهنگساز")))
            .put("cameraman", JSONArray().put(credit("مرتضی پورصمدی", "فیلمبردار")))
            .put("editor", JSONArray().put(credit("هایده صفی‌یاری", "تدوین‌گر")))
        val people = app.itv.prototype.data.CatalogMapper.movieCredits(content)
        assertEquals(
            listOf(
                "رخشان بنی‌اعتماد",
                "فرید مصطفوی",
                "مهتاب کرامتی",
                "محمدرضا فروتن",
                "جهانگیر کوثری",
                "مرتضی پورصمدی",
                "هایده صفی‌یاری",
                "کریستف رضاعی",
            ),
            people.map { it.name },
        )
        assertEquals(
            listOf("کارگردان", "نویسنده", "بازیگر", "بازیگر", "تهیه‌کننده", "فیلمبردار", "تدوین‌گر", "آهنگساز"),
            people.map { it.role },
        )
        assertEquals("https://gateway.telewebion.net$avatar", people[0].imageUrl)
        assertEquals("https://cdn.example/writer.jpg", people[1].imageUrl)
        assertNull(people[2].imageUrl)
        assertEquals(
            "https://gateway.telewebion.net/sites/default/files/images/biography/avatar/1399/parviz%20parastuyi.jpg",
            people[3].imageUrl,
        )
        assertTrue(app.itv.prototype.data.CatalogMapper.movieCredits(JSONObject().put("content_type", "SERIAL").put("actors", JSONArray().put(credit("کسی", "بازیگر")))).isEmpty())
        assertTrue(app.itv.prototype.data.CatalogMapper.movieCredits(JSONObject().put("content_type", "MOVIE")).isEmpty())
    }

    private fun credit(name: String, role: String, image: String? = null) = JSONObject()
        .put("person", JSONObject().put("title", name).apply { if (image != null) put("image", image) })
        .put("role", JSONObject().put("title", role))
}
