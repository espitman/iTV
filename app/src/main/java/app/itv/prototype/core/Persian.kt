package app.itv.prototype.core

fun toAsciiDigits(value: String): String {
    val chars = CharArray(value.length) { index ->
        when (val char = value[index]) {
            in '۰'..'۹' -> ('0'.code + (char - '۰')).toChar()
            in '٠'..'٩' -> ('0'.code + (char - '٠')).toChar()
            else -> char
        }
    }
    return String(chars)
}

fun persianDigits(value: Int): String = persianDigits(value.toString())

fun persianDigits(value: Long): String = persianDigits(value.toString())

fun persianDigits(value: String): String {
    val chars = CharArray(value.length) { index ->
        val char = value[index]
        if (char in '0'..'9') ('۰'.code + (char - '0')).toChar() else char
    }
    return String(chars)
}

fun persianClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    val raw = if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
    return persianDigits(raw)
}

fun firstNumberToken(value: String): String? {
    return Regex("\\d+").find(toAsciiDigits(value))?.value
}
