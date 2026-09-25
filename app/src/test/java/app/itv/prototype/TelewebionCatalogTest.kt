package app.itv.prototype

import app.itv.prototype.core.CatalogPaging
import app.itv.prototype.core.SourceKind
import app.itv.prototype.data.CatalogMapper
import app.itv.prototype.data.TelewebionClient
import app.itv.prototype.data.TelewebionImageKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelewebionCatalogTest {
    @Test
    fun productSerialPartsReadFromContentArray() {
        val root = productSerialRoot(
            JSONArray()
                .put(productPart("0xc212146", 1, "زیرخاکی", "5539c481-de25-4879-84f1-a70ae1bf599c"))
                .put(productPart("0xc212148", 2, "زیرخاکی", "474d3463-1533-4a16-b7a0-6c933413cf2d")),
        )
        val client = clientFor { root }
        val page = client.loadProductSeasonPage("0xc20bf81", 1, 0)
        assertEquals(2, page.rowCount)
        assertFalse(page.finished)
        assertEquals(listOf("0xc212146", "0xc212148"), page.episodes.map { it.sourceEpisodeId })
        assertEquals(listOf("1", "2"), page.episodes.map { it.displayNumber })
        assertEquals(listOf("زیرخاکی", "زیرخاکی"), page.episodes.map { it.title })
        assertEquals(
            "https://static.telewebion.net/vodBannerImages/5539c481-de25-4879-84f1-a70ae1bf599c/default",
            page.episodes[0].imageUrl,
        )
        assertFalse(page.episodes.any { it.title.startsWith("0x") })
    }

    @Test
    fun productSerialEmptyPartsAreFinishedNotMalformed() {
        val client = clientFor { productSerialRoot(JSONArray()) }
        val page = client.loadProductSeasonPage("0xc20bf81", 1, 100)
        assertTrue(page.finished)
        assertEquals(0, page.rowCount)
        assertTrue(page.episodes.isEmpty())
    }

    @Test
    fun productSerialMalformedContentFailsClearly() {
        val missing = JSONObject().put("body", JSONObject())
        val objectWithoutParts = JSONObject().put(
            "body",
            JSONObject().put("content", JSONObject().put("persian_title", "زیرخاکی")),
        )
        val clientMissing = clientFor { missing }
        val clientObject = clientFor { objectWithoutParts }
        val missingError = runCatching { clientMissing.loadProductSeasonPage("0xc20bf81", 1, 0) }
        val objectError = runCatching { clientObject.loadProductSeasonPage("0xc20bf81", 1, 0) }
        assertTrue(missingError.exceptionOrNull()?.message.orEmpty().contains("ناقص"))
        assertTrue(objectError.exceptionOrNull()?.message.orEmpty().contains("ناقص"))
    }

    @Test
    fun productMetaUsesPersianTitleStorySeasonsAndMedia() {
        val content = JSONObject()
            .put("persian_title", "زیرخاکی")
            .put("story", "داستان فریبرز")
            .put("sorted_seasons", JSONArray().put(1).put(2).put(3).put(4))
            .put(
                "media",
                JSONObject().put("horizontal_big_poster", "4acb2fcf-c93d-402d-ae63-4a821123e70f"),
            )
        val (title, poster, description) = CatalogMapper.productMeta(content)
        val seasons = CatalogMapper.seasonsFromContent(content)
        assertEquals("زیرخاکی", title)
        assertEquals("داستان فریبرز", description)
        assertEquals(listOf(1, 2, 3, 4), seasons.map { it.seasonNumber })
        assertEquals(
            "https://static.telewebion.net/vodBannerImages/4acb2fcf-c93d-402d-ae63-4a821123e70f/default",
            poster,
        )
    }

    @Test
    fun resolveProductStreamReadsNestedTitraje() {
        val root = JSONObject().put(
            "body",
            JSONObject().put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("content_id", "0xc212146")
                        .put("persian_title", "زیرخاکی")
                        .put(
                            "titraje",
                            JSONObject()
                                .put("first_titraje_start", 210)
                                .put("first_titraje_end", 307)
                                .put("last_titraje_start", 2514),
                        )
                        .put(
                            "stream",
                            JSONObject().put(
                                "telewebion",
                                "https://cdna.telewebion.net/ifilm/episode/0xff56cd3/playlist.m3u8",
                            ),
                        ),
                ),
            ),
        )
        val client = clientFor { root }
        val stream = client.resolveStream(SourceKind.PRODUCT, "0xc212146")
        assertEquals("https://cdna.telewebion.net/ifilm/episode/0xff56cd3/playlist.m3u8", stream.url)
        assertEquals(210, stream.firstTitrajeStartSec)
        assertEquals(307, stream.firstTitrajeEndSec)
        assertEquals(2514, stream.lastTitrajeStartSec)
    }

    @Test
    fun programMetaUsesProgramImageFolderAndEpisodeUsesEpisodeFolder() {
        val programImage = "fbbd3f22-c6d9-4da2-b40b-689e71a1210d"
        val episodeImage = "107b7a07-afeb-4a5c-b82e-14d563802c9a"
        val root = programRoot(
            title = "دختران",
            image = programImage,
            episodes = JSONArray().put(
                JSONObject()
                    .put("EpisodeID", "0x2993b68")
                    .put("title", "قسمت 28")
                    .put("image", episodeImage),
            ),
        )
        val (title, poster) = CatalogMapper.programMeta(root)
        val episodes = CatalogMapper.programEpisodes(root.getJSONObject("body").getJSONArray("queryProgram").getJSONObject(0).getJSONArray("episodes"), 0)
        assertEquals("دختران", title)
        assertEquals("https://static.telewebion.net/programImages/$programImage/default", poster)
        assertEquals("https://static.telewebion.net/episodeImages/$episodeImage/default", episodes.single().imageUrl)
        assertEquals("28", episodes.single().displayNumber)
    }

    @Test
    fun malformedQueryProgramFailsAndEmptyEpisodesAreSeparate() {
        val malformed = JSONObject().put("body", JSONObject())
        val empty = programRoot("دختران", "fbbd3f22-c6d9-4da2-b40b-689e71a1210d", JSONArray())
        val missing = clientFor { malformed }
        val emptyClient = clientFor { empty }
        val error = runCatching { missing.loadProgramPage("0x296b3ea", 0) }
        assertTrue(error.exceptionOrNull()?.message.orEmpty().contains("ناقص"))
        val page = emptyClient.loadProgramPage("0x296b3ea", 0)
        assertTrue(page.finished)
        assertEquals(0, page.rowCount)
        assertEquals("دختران", page.title)
    }

    @Test
    fun exactHundredRowBoundaryHasNextOffsetWithoutPrefetch() {
        assertEquals(100, CatalogPaging.nextOffset(0, 100))
        assertEquals(null, CatalogPaging.nextOffset(0, 99))
        assertEquals(null, CatalogPaging.nextOffset(0, 19))
        assertEquals(null, CatalogPaging.nextOffset(100, 0))
        assertEquals(200, CatalogPaging.nextOffset(100, 100))
        val walked = walkOffsets(mapOf(0 to 100, 100 to 100, 200 to 3))
        assertEquals(listOf(0, 100, 200), walked)
        val ids = (0 until 203).map { "e$it" }
        assertEquals(203, ids.size)
        assertEquals(ids, ids.distinct())
    }

    @Test
    fun clientProgramAndProductPagesKeepSourceOrderAcrossHundredBoundary() {
        val programPages = mapOf(
            0 to programRoot("نمونه", "fbbd3f22-c6d9-4da2-b40b-689e71a1210d", programEpisodes(0, 100)),
            100 to programRoot("نمونه", "fbbd3f22-c6d9-4da2-b40b-689e71a1210d", JSONArray()),
        )
        val productPages = mapOf(
            0 to productSerialRoot(productParts(0, 100)),
            100 to productSerialRoot(JSONArray()),
        )
        val programClient = TelewebionClient { url ->
            val offset = Regex("Offset=(\\d+)").find(url)!!.groupValues[1].toInt()
            programPages.getValue(offset)
        }
        val productClient = TelewebionClient { url ->
            val offset = Regex("offset=(\\d+)").find(url)!!.groupValues[1].toInt()
            productPages.getValue(offset)
        }
        val firstProgram = programClient.loadProgramPage("0x1b29939", 0)
        val secondProgram = programClient.loadProgramPage("0x1b29939", 100)
        assertEquals(100, firstProgram.rowCount)
        assertEquals((0 until 100).toList(), firstProgram.episodes.map { it.sourceOrder })
        assertEquals((0 until 100).map { "e$it" }, firstProgram.episodes.map { it.sourceEpisodeId })
        assertTrue(secondProgram.finished)
        assertEquals(100, CatalogPaging.nextOffset(0, firstProgram.rowCount))
        assertEquals(null, CatalogPaging.nextOffset(100, secondProgram.rowCount))

        val firstProduct = productClient.loadProductSeasonPage("0xc20bf81", 1, 0)
        val secondProduct = productClient.loadProductSeasonPage("0xc20bf81", 1, 100)
        assertEquals(100, firstProduct.rowCount)
        assertEquals((0 until 100).map { "c$it" }, firstProduct.episodes.map { it.sourceEpisodeId })
        assertEquals((0 until 100).map { (it + 1).toString() }, firstProduct.episodes.map { it.displayNumber })
        assertTrue(secondProduct.finished)
        assertEquals(firstProduct.episodes.map { it.sourceEpisodeId }, firstProduct.episodes.map { it.sourceEpisodeId }.distinct())
    }

    @Test
    fun imageUrlDoesNotFabricateUnknownPaths() {
        assertEquals(null, CatalogMapper.imageUrl("/other/files/poster.jpg", TelewebionImageKind.EPISODE))
        assertEquals(
            "https://gateway.telewebion.net/sites/default/files/images/poster/1399/poster.jpg",
            CatalogMapper.imageUrl("/sites/default/files/images/poster/1399/poster.jpg", TelewebionImageKind.VOD),
        )
    }

    @Test
    fun imageUrlAcceptsSafeOpaqueTokensAndRejectsUnsafeOnes() {
        val token = "ZDQ4ZTc0MTUzMDM3NThjZDhjNDc3OTFiM2Y1NDczNGNmNTU5MjM5ZWVjMTgxNzYyNDE2ZDY3MjMyZWUzNmQyMg"
        val background = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3ODkwYWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo"
        assertEquals(
            "https://static.telewebion.net/programImages/$token/default",
            CatalogMapper.imageUrl(token, TelewebionImageKind.PROGRAM),
        )
        val prefersImage = programRoot("نمونه", token, JSONArray()).also { root ->
            root.getJSONObject("body")
                .getJSONArray("queryProgram")
                .getJSONObject(0)
                .put("background", background)
        }
        assertEquals(
            "https://static.telewebion.net/programImages/$token/default",
            CatalogMapper.programMeta(prefersImage).second,
        )
        val usesBackground = programRoot("نمونه", "../bad", JSONArray()).also { root ->
            root.getJSONObject("body")
                .getJSONArray("queryProgram")
                .getJSONObject(0)
                .put("background", background)
        }
        assertEquals(
            "https://static.telewebion.net/programImages/$background/default",
            CatalogMapper.programMeta(usesBackground).second,
        )
        assertEquals(null, CatalogMapper.imageUrl("../secret", TelewebionImageKind.PROGRAM))
        assertEquals(null, CatalogMapper.imageUrl("abc/def", TelewebionImageKind.PROGRAM))
        assertEquals(null, CatalogMapper.imageUrl("token?x=1", TelewebionImageKind.PROGRAM))
        assertEquals(null, CatalogMapper.imageUrl("token#frag", TelewebionImageKind.PROGRAM))
        assertEquals(null, CatalogMapper.imageUrl("evil.com", TelewebionImageKind.PROGRAM))
        assertEquals(null, CatalogMapper.imageUrl("./default", TelewebionImageKind.PROGRAM))
        assertEquals(null, CatalogMapper.imageUrl("token&host=evil.com", TelewebionImageKind.PROGRAM))
    }

    private fun walkOffsets(rowsByOffset: Map<Int, Int>): List<Int> {
        var offset = 0
        val seen = mutableListOf<Int>()
        while (true) {
            seen += offset
            offset = CatalogPaging.nextOffset(offset, rowsByOffset[offset] ?: 0) ?: break
        }
        return seen
    }

    private fun clientFor(root: () -> JSONObject) = TelewebionClient { root() }

    private fun productSerialRoot(parts: JSONArray) = JSONObject().put(
        "body",
        JSONObject().put("content", JSONArray().put(JSONObject().put("serial_parts", parts))),
    )

    private fun productPart(id: String, episode: Int, title: String, image: String) = JSONObject()
        .put("content_id", id)
        .put("episode", episode)
        .put("persian_title", title)
        .put("season", 1)
        .put("story", "خلاصه کوتاه")
        .put("media", JSONObject().put("special_image_part", image))

    private fun productParts(start: Int, count: Int): JSONArray {
        val array = JSONArray()
        repeat(count) { index ->
            array.put(productPart("c${start + index}", start + index + 1, "زیرخاکی", "5539c481-de25-4879-84f1-a70ae1bf599c"))
        }
        return array
    }

    private fun programRoot(title: String, image: String, episodes: JSONArray) = JSONObject().put(
        "body",
        JSONObject().put(
            "queryProgram",
            JSONArray().put(
                JSONObject()
                    .put("title", title)
                    .put("image", image)
                    .put("episodes", episodes),
            ),
        ),
    )

    private fun programEpisodes(start: Int, count: Int): JSONArray {
        val array = JSONArray()
        repeat(count) { index ->
            array.put(
                JSONObject()
                    .put("EpisodeID", "e${start + index}")
                    .put("title", "قسمت ${start + index + 1}")
                    .put("image", "107b7a07-afeb-4a5c-b82e-14d563802c9a"),
            )
        }
        return array
    }
}
