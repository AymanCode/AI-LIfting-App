package com.ayman.ecolift.web

private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

private var locationSearch: String = ""

/**
 * [AndroidHost.install] passes the activity intent query here.
 * `?date=YYYY-MM-DD` opens the Log tab on that day. Anything malformed is ignored.
 */
fun installLogSearch(search: String) {
    locationSearch = search
}

fun logDateFromSearch(search: String): String? {
    val query = search.removePrefix("?")
    if (query.isEmpty()) return null
    val raw = query.split("&")
        .map { it.split("=", limit = 2) }
        .firstOrNull { it.size == 2 && it[0] == "date" }
        ?.get(1)
        ?: return null
    val decoded = raw.replace("%2D", "-", ignoreCase = true).trim()
    return decoded.takeIf { ISO_DATE.matches(it) }
}

fun logDateFromLocation(): String? = logDateFromSearch(locationSearch)
