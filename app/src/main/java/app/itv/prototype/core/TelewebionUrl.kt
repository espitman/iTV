package app.itv.prototype.core

import java.net.URI

data class AllowedSource(
    val kind: SourceKind,
    val sourceId: String,
) {
    val pageUrl: String = "https://telewebion.net/${kind.path}/$sourceId"
}

object TelewebionUrl {
    private val idPattern = Regex("^0x[0-9a-fA-F]+$")

    fun parse(raw: String): Result<AllowedSource> = runCatching {
        val ascii = toAsciiDigits(raw.trim())
        require(ascii.isNotEmpty()) { "نشانی خالی است" }
        val uri = runCatching { URI(ascii) }.getOrElse { error("نشانی نامعتبر است") }
        val scheme = uri.scheme?.lowercase().orEmpty()
        require(scheme == "https") { "فقط نشانی https مجاز است" }
        val host = uri.host?.lowercase()?.trim('.').orEmpty()
        require(host == "telewebion.net") { "فقط telewebion.net مجاز است" }
        require(uri.userInfo == null) { "نشانی نامعتبر است" }
        val port = uri.port
        require(port == -1 || port == 443) { "نشانی نامعتبر است" }
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        require(segments.size == 2 || segments.size == 3) { "مسیر برنامه یا محصول ناقص است" }
        val kind = when (segments[0].lowercase()) {
            "program" -> SourceKind.PROGRAM
            "product" -> SourceKind.PRODUCT
            else -> error("فقط /program یا /product مجاز است")
        }
        val sourceId = segments[1]
        require(idPattern.matches(sourceId)) { "شناسه باید شبیه 0x و هگزادسیمال باشد" }
        if (segments.size == 3) {
            require(segments[2].equals("archive", ignoreCase = true)) { "مسیر برنامه یا محصول نامعتبر است" }
        }
        AllowedSource(kind, sourceId.lowercase())
    }
}
