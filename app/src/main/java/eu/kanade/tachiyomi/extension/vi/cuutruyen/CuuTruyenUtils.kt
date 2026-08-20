package eu.kanade.tachiyomi.extension.vi.cuutruyen

import kotlinx.serialization.json.Json
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URLEncoder

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

inline fun <reified T> Response.parseAs(): T = json.decodeFromString(body.string())

fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

/**
 * Mirrors `helpers::rewrite_storage_url` from the original Rust source: the API sometimes
 * returns old/broken CDN hostnames that need to be swapped for the current ones.
 */
fun rewriteStorageUrl(url: String): String {
    var result = url
    STORAGE_REPLACEMENTS.forEach { (old, new) -> result = result.replace(old, new) }
    return result
}

private val STORAGE_REPLACEMENTS = listOf(
    "storage-ct.lrclib.net" to "storage-bravo.cuutruyen.net",
    "storage-ct-riften.site" to "storage-charlie.cuutruyen.net",
)

/**
 * Same behaviour as the Rust source's `CuuMangaDetails::description`: the API returns the
 * synopsis as an HTML blob whose first line duplicates the title, so it's dropped.
 *
 * Jsoup's wholeText()/text() only preserve line breaks that are already literal newline
 * characters or <br> tags reduced to one beforehand; block-level tags like <p>/<div> get
 * concatenated with ZERO separation between them (verified with a standalone Jsoup test
 * harness: "<p>A</p><p>B</p>".wholeText() == "AB", not "A\nB"). Marking each <br>/<p>/<div>
 * boundary with a literal "\n" placeholder text node before extraction -- and only replacing
 * it back to a real newline afterwards -- survives Jsoup's own whitespace normalization,
 * which a raw inserted '\n' character would not.
 */
fun parseDescription(html: String?): String? {
    if (html.isNullOrBlank()) return null
    val doc = Jsoup.parse(html)
    doc.select("br").append("\\n")
    doc.select("p, div").prepend("\\n")
    val text = doc.text().split("\\n").joinToString("\n") { it.trim() }.trim()
    val firstBreak = text.indexOf('\n')
    return if (firstBreak >= 0) text.substring(firstBreak + 1).trim() else text
}

/** Title-cases every word in a tag, e.g. "hành động" -> "Hành Động". */
fun titleCase(value: String): String =
    value.split(" ").joinToString(" ") { word ->
        if (word.isEmpty()) word else word.replaceFirstChar { it.uppercase() }
    }

/**
 * Parses the RFC3339/ISO-8601 timestamps returned by the API (e.g. "2024-01-15T10:30:00.000Z")
 * into epoch milliseconds. Uses java.time, enabled on minSdk 21 via core library desugaring
 * (see the `coreLibraryDesugaring` dependency in app/build.gradle.kts).
 */
fun parseIsoDate(value: String): Long = try {
    java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
} catch (e: Exception) {
    try {
        java.time.Instant.parse(value).toEpochMilli()
    } catch (e2: Exception) {
        0L
    }
}
