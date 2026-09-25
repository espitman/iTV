package app.itv.prototype.admin

import android.content.Context
import app.itv.prototype.core.TelewebionUrl
import app.itv.prototype.data.ImportCoordinator
import app.itv.prototype.data.LibraryRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respondBytes
import app.itv.prototype.R
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class LanDashboard(
    private val context: Context,
    private val repository: LibraryRepository,
    private val importer: ImportCoordinator,
) {
    private val authPrefs = context.getSharedPreferences("dashboard_auth", Context.MODE_PRIVATE)
    val pairing = PairingStore(
        readTokens = { authPrefs.getStringSet("tokens", emptySet())?.toSet().orEmpty() },
        writeTokens = { check(authPrefs.edit().putStringSet("tokens", it).commit()) { "ذخیرهٔ نشست ناموفق بود" } },
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = AsyncStartGate()
    private val lock = Any()
    private var engine: ApplicationEngine? = null
    private var startJob: Job? = null

    @Volatile var running: Boolean = false
        private set
    @Volatile var port: Int = 0
        private set

    fun dashboardUrl(): String? {
        if (!running || port == 0) return null
        val ip = LanAddresses.lanIpv4() ?: return null
        return "http://$ip:$port"
    }

    fun start() {
        val token = gate.beginStart() ?: return
        val job = scope.launch { startServer(token) }
        synchronized(lock) { startJob = job }
    }

    fun stop() {
        gate.stop()
        running = false
        port = 0
        val job: Job?
        val toStop: ApplicationEngine?
        synchronized(lock) {
            job = startJob
            startJob = null
            toStop = engine
            engine = null
        }
        job?.cancel()
        runCatching { toStop?.stop(200L, 500L) }
        pairing.rotate()
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private suspend fun startServer(token: Int) {
        val chosen = LanAddresses.pickPort()
        if (!gate.isCurrent(token)) return
        pairing.rotate()
        val server = embeddedServer(CIO, port = chosen, host = "0.0.0.0") {
            routing {
                get("/") { call.respondHtmlPage() }
                get("/fonts/regular.ttf") { call.respondBytes(this@LanDashboard.context.resources.openRawResource(R.font.byekan).use { it.readBytes() }, ContentType.parse("font/ttf")) }
                get("/fonts/bold.ttf") { call.respondBytes(this@LanDashboard.context.resources.openRawResource(R.font.byekanbold).use { it.readBytes() }, ContentType.parse("font/ttf")) }
                get("/api/status") { call.respondJson(statusJson()) }
                post("/api/pair") { call.handlePair() }
                post("/api/logout") { call.authed { handleLogout() } }
                post("/api/preview") { call.authed { handlePreview() } }
                get("/api/series") { call.authed { handleList() } }
                post("/api/series") { call.authed { handleAdd() } }
                patch("/api/series/{id}") { call.authed { handleRename() } }
                delete("/api/series/{id}") { call.authed { handleDelete() } }
                post("/api/series/{id}/refresh") { call.authed { handleRefresh() } }
            }
        }
        try {
            if (!gate.isCurrent(token)) {
                disposeServer(server)
                return
            }
            server.start(wait = false)
            val bound = boundPort(server, chosen)
            val published = synchronized(lock) {
                if (!gate.isCurrent(token)) {
                    false
                } else {
                    engine = server
                    port = bound
                    running = true
                    true
                }
            }
            if (!published) disposeServer(server)
        } catch (cancelled: CancellationException) {
            disposeServer(server)
            throw cancelled
        } catch (_: Throwable) {
            disposeServer(server)
            if (gate.isCurrent(token)) {
                gate.markFailed(token)
                running = false
                port = 0
            }
        }
    }

    private fun disposeServer(server: ApplicationEngine) {
        synchronized(lock) {
            if (engine === server) engine = null
        }
        runCatching { server.stop(200L, 500L) }
    }

    private suspend fun boundPort(server: ApplicationEngine, fallback: Int): Int {
        val resolved = runCatching {
            server.resolvedConnectors().firstOrNull()?.port
        }.getOrNull()
        return resolved?.takeIf { it > 0 } ?: fallback
    }

    private suspend fun ApplicationCall.respondHtmlPage() {
        if (!sameOrigin()) return
        val html = context.assets.open("dashboard.html").bufferedReader().use { it.readText() }
        respondText(html, ContentType.Text.Html)
    }

    private suspend fun ApplicationCall.handlePair() {
        if (!sameOrigin()) return
        val pin = JSONObject(receiveText().ifBlank { "{}" }).optString("pin")
        runCatching { pairing.pair(pin) }
            .onSuccess { token -> respondJson(JSONObject().put("token", token)) }
            .onFailure { fail(HttpStatusCode.Forbidden, it.message) }
    }

    private suspend fun ApplicationCall.handleLogout() {
        pairing.revoke(request.header("X-ITV-Token"))
        respondJson(JSONObject().put("ok", true))
    }

    private suspend fun ApplicationCall.handlePreview() {
        val source = parseUrl(JSONObject(receiveText().ifBlank { "{}" }).optString("url"))
            ?: return
        val preview = importer.preview(source)
        respondJson(
            JSONObject()
                .put("kind", preview.kind.path)
                .put("kindLabel", if (preview.isMovie) "فیلم سینمایی" else preview.kind.label)
                .put("isMovie", preview.isMovie)
                .put("durationMinutes", preview.durationMinutes)
                .put("sourceId", preview.sourceId)
                .put("title", preview.title)
                .put("posterUrl", preview.posterUrl ?: JSONObject.NULL)
                .put("description", preview.description ?: JSONObject.NULL)
                .put("seasonCount", preview.seasonCount ?: JSONObject.NULL)
                .put("episodeCount", preview.episodeCount ?: JSONObject.NULL),
        )
    }

    private suspend fun ApplicationCall.handleAdd() {
        val source = parseUrl(JSONObject(receiveText().ifBlank { "{}" }).optString("url"))
            ?: return
        val id = importer.add(source)
        respondJson(JSONObject().put("id", id))
    }

    private suspend fun ApplicationCall.handleList() {
        val items = JSONArray()
        repository.library().forEach { series ->
            items.put(
                JSONObject()
                    .put("id", series.id)
                    .put("title", series.title)
                    .put("sourceTitle", series.sourceTitle)
                    .put("localTitle", series.localTitle ?: JSONObject.NULL)
                    .put("kind", series.kind.path)
                    .put("isMovie", series.isMovie)
                    .put("durationMinutes", series.durationMinutes)
                    .put("sourceId", series.sourceId)
                    .put("seasonCount", series.presentSeasonCount)
                    .put("episodeCount", series.presentEpisodes.size)
                    .put("importState", series.importState.name)
                    .put("importError", series.importError ?: JSONObject.NULL)
                    .put("posterUrl", series.posterUrl ?: JSONObject.NULL),
            )
        }
        respondJson(JSONObject().put("items", items))
    }

    private suspend fun ApplicationCall.handleRename() {
        val id = parameters["id"]?.toLongOrNull() ?: return fail(HttpStatusCode.BadRequest, "شناسه نامعتبر است")
        val title = JSONObject(receiveText().ifBlank { "{}" }).optString("localTitle")
        repository.rename(id, title)
        respondJson(JSONObject().put("ok", true))
    }

    private suspend fun ApplicationCall.handleDelete() {
        val id = parameters["id"]?.toLongOrNull() ?: return fail(HttpStatusCode.BadRequest, "شناسه نامعتبر است")
        repository.deleteSeries(id)
        respondJson(JSONObject().put("ok", true))
    }

    private suspend fun ApplicationCall.handleRefresh() {
        val id = parameters["id"]?.toLongOrNull() ?: return fail(HttpStatusCode.BadRequest, "شناسه نامعتبر است")
        importer.refresh(id)
        respondJson(JSONObject().put("ok", true))
    }

    private suspend fun ApplicationCall.authed(block: suspend ApplicationCall.() -> Unit) {
        if (!sameOrigin()) return
        if (!pairing.accepts(request.header("X-ITV-Token"))) {
            fail(HttpStatusCode.Unauthorized, "ابتدا با کد اتصال وارد شوید")
            return
        }
        runCatching { block() }.onFailure { fail(HttpStatusCode.BadRequest, it.message) }
    }

    private suspend fun ApplicationCall.parseUrl(raw: String): app.itv.prototype.core.AllowedSource? {
        val parsed = TelewebionUrl.parse(raw)
        return parsed.getOrElse {
            fail(HttpStatusCode.BadRequest, it.message ?: "نشانی نامعتبر است")
            return null
        }
    }

    private suspend fun ApplicationCall.sameOrigin(): Boolean {
        val origin = request.header("Origin") ?: return true
        val host = request.header("Host").orEmpty()
        if (host.isBlank()) {
            fail(HttpStatusCode.Forbidden, "مبدأ نامعتبر است")
            return false
        }
        val allowed = origin == "http://$host" || origin == "https://$host"
        if (!allowed) {
            fail(HttpStatusCode.Forbidden, "درخواست از مبدأ دیگر پذیرفته نمی‌شود")
            return false
        }
        return request.path().startsWith("/")
    }

    private fun statusJson(): JSONObject =
        JSONObject()
            .put("running", running)
            .put("port", port)
            .put("url", dashboardUrl() ?: JSONObject.NULL)

    private suspend fun ApplicationCall.respondJson(body: JSONObject, status: HttpStatusCode = HttpStatusCode.OK) {
        respondText(body.toString(), ContentType.Application.Json, status)
    }

    private suspend fun ApplicationCall.fail(status: HttpStatusCode, message: String?) {
        respondJson(JSONObject().put("error", message ?: "خطا"), status)
    }
}
