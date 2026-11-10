package eu.kanade.tachiyomi.extension.vi.cuutruyen

import java.io.IOException
import java.net.URLEncoder

private val MIRROR_HOSTS = mapOf(
    "cuutruyen.net" to "hetcuutruyen.net",
    "hetcuutruyen.net" to "cuutruyen.net",
    "storage-bravo.cuutruyen.net" to "storage-charlie.cuutruyen.net",
    "storage-charlie.cuutruyen.net" to "storage-bravo.cuutruyen.net",
)

private val URL_HOST_REGEX = Regex("^(https?://)([^/?#:]+)(.*)$")

/** Trả về host đối ứng cho API và máy chủ ảnh; trả về `null` nếu host không thuộc bảng cặp. */
fun mirrorHostOf(host: String): String? = MIRROR_HOSTS[host]

/** Đổi phần host của URL sang [newHost], giữ nguyên scheme, port, path, query và fragment. */
fun swapUrlHost(url: String, newHost: String): String =
    URL_HOST_REGEX.replace(url) { "${it.groupValues[1]}$newHost${it.groupValues[3]}" }

/**
 * Quyết định có thử lại sang miền đối ứng hay không:
 * - Tắt khi người dùng bật proxy ([proxyPref] khác rỗng).
 * - Mỗi lượt thực thi chỉ thử lại tối đa 1 lần ([alreadyRetried] == false).
 * - Chỉ kích hoạt khi gặp `IOException` hoặc HTTP 403 / 5xx (không retry khi 429).
 */
fun shouldRetryMirror(
    proxyPref: String,
    alreadyRetried: Boolean,
    isIoException: Boolean,
    httpCode: Int?,
): Boolean {
    if (proxyPref.isNotEmpty() || alreadyRetried) return false
    if (isIoException) return true
    return httpCode != null && (httpCode == 403 || httpCode in 500..599)
}

/** Quyết định có chuyển từ proxy ảnh `wsrv.nl` về URL ảnh gốc (`ct_src`) hay không. */
fun shouldFallbackWsrv(
    host: String,
    srcParam: String?,
    isIoException: Boolean,
    httpCode: Int?,
): Boolean {
    if (host != "wsrv.nl" || srcParam.isNullOrEmpty()) return false
    return isIoException || httpCode == null || httpCode !in 200..299
}

/** Tạo URL ảnh gốc kèm đầy đủ các tham số ghép ảnh `ct_w`, `ct_h`, `ct_drm`. */
fun buildWsrvFallbackUrl(
    srcUrl: String,
    widthParam: String?,
    heightParam: String?,
    drmParam: String?,
): String {
    val params = buildList {
        if (widthParam != null) add("ct_w=${URLEncoder.encode(widthParam, "UTF-8")}")
        if (heightParam != null) add("ct_h=${URLEncoder.encode(heightParam, "UTF-8")}")
        if (drmParam != null) add("ct_drm=${URLEncoder.encode(drmParam, "UTF-8")}")
    }
    if (params.isEmpty()) return srcUrl
    val sep = if ('?' in srcUrl) '&' else '?'
    return "$srcUrl$sep${params.joinToString("&")}"
}

/**
 * Thực thi request với cơ chế tự động chuyển sang host đối ứng tối đa 1 lần khi gặp `IOException`
 * hoặc HTTP 403/5xx. Đóng response lỗi trước khi retry và báo rõ cả hai miền khi đều thất bại.
 */
inline fun <R> executeWithMirrorFallback(
    url: String,
    host: String,
    proxyPref: String,
    getCode: (R) -> Int,
    closeResponse: (R) -> Unit,
    proceed: (String) -> R,
): R {
    val mirrorHost = mirrorHostOf(host)
    if (mirrorHost == null || proxyPref.isNotEmpty()) {
        return proceed(url)
    }

    var alreadyRetried = false
    val firstFailureDesc = try {
        val response = proceed(url)
        val code = getCode(response)
        if (!shouldRetryMirror(proxyPref, alreadyRetried, isIoException = false, httpCode = code)) {
            return response
        }
        closeResponse(response)
        "HTTP $code"
    } catch (e: IOException) {
        if (!shouldRetryMirror(proxyPref, alreadyRetried, isIoException = true, httpCode = null)) {
            throw e
        }
        e.message ?: "IOException"
    }

    alreadyRetried = true
    val mirrorUrl = swapUrlHost(url, mirrorHost)
    val secondResponse = try {
        proceed(mirrorUrl)
    } catch (e: IOException) {
        throw IOException(
            "Không thể truy cập cả hai miền ($host [$firstFailureDesc], $mirrorHost [${e.message ?: "IOException"}])",
            e,
        )
    }

    val secondCode = getCode(secondResponse)
    if (secondCode == 403 || secondCode in 500..599) {
        closeResponse(secondResponse)
        throw IOException(
            "Không thể truy cập cả hai miền ($host [$firstFailureDesc], $mirrorHost [HTTP $secondCode])",
        )
    }
    return secondResponse
}
