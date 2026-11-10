// Chạy: kotlinc app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyenFallback.kt \
//         scripts/CheckFallback.kt -include-runtime -d /tmp/cfb.jar && java -cp /tmp/cfb.jar eu.kanade.tachiyomi.extension.vi.cuutruyen.CheckFallbackKt
//
// Hợp đồng chữ ký hàm thuần trong CuuTruyenFallback.kt (T004 phải cài đặt khớp 100%):
// 1. fun mirrorHostOf(host: String): String?
// 2. fun swapUrlHost(url: String, newHost: String): String
// 3. fun shouldRetryMirror(proxyPref: String, alreadyRetried: Boolean, isIoException: Boolean, httpCode: Int?): Boolean
// 4. fun shouldFallbackWsrv(host: String, srcParam: String?, isIoException: Boolean, httpCode: Int?): Boolean
// 5. fun buildWsrvFallbackUrl(srcUrl: String, widthParam: String?, heightParam: String?, drmParam: String?): String
package eu.kanade.tachiyomi.extension.vi.cuutruyen

fun main() {
    // 1. mirrorHostOf: đối xứng hai chiều cho API và ảnh; host lạ -> null
    check(mirrorHostOf("cuutruyen.net") == "hetcuutruyen.net")
    check(mirrorHostOf("hetcuutruyen.net") == "cuutruyen.net")
    check(mirrorHostOf("storage-bravo.cuutruyen.net") == "storage-charlie.cuutruyen.net")
    check(mirrorHostOf("storage-charlie.cuutruyen.net") == "storage-bravo.cuutruyen.net")
    check(mirrorHostOf("wsrv.nl") == null)
    check(mirrorHostOf("light-pig-37.tachibana-shin.deno.net") == null)
    check(mirrorHostOf("example.com") == null)

    // 2. swapUrlHost: đổi đúng phần host, giữ nguyên scheme, path, query, fragment
    check(
        swapUrlHost("https://cuutruyen.net/api/v2/mangas/123?page=1#section", "hetcuutruyen.net") ==
            "https://hetcuutruyen.net/api/v2/mangas/123?page=1#section",
    )
    check(
        swapUrlHost("http://storage-bravo.cuutruyen.net/file/img.jpg?ct_w=1024&ct_h=1450", "storage-charlie.cuutruyen.net") ==
            "http://storage-charlie.cuutruyen.net/file/img.jpg?ct_w=1024&ct_h=1450",
    )

    // 3. shouldRetryMirror: tắt khi bật proxy, tối đa 1 lần, chỉ retry khi IOException hoặc HTTP 403/5xx (không retry 429)
    check(!shouldRetryMirror("https://light-pig-37.tachibana-shin.deno.net", alreadyRetried = false, isIoException = true, httpCode = null))
    check(!shouldRetryMirror("https://light-pig-37.tachibana-shin.deno.net", alreadyRetried = false, isIoException = false, httpCode = 503))
    check(!shouldRetryMirror("", alreadyRetried = true, isIoException = true, httpCode = null))
    check(!shouldRetryMirror("", alreadyRetried = true, isIoException = false, httpCode = 502))
    check(shouldRetryMirror("", alreadyRetried = false, isIoException = true, httpCode = null))
    check(shouldRetryMirror("", alreadyRetried = false, isIoException = false, httpCode = 403))
    check(shouldRetryMirror("", alreadyRetried = false, isIoException = false, httpCode = 500))
    check(shouldRetryMirror("", alreadyRetried = false, isIoException = false, httpCode = 502))
    check(shouldRetryMirror("", alreadyRetried = false, isIoException = false, httpCode = 599))
    check(!shouldRetryMirror("", alreadyRetried = false, isIoException = false, httpCode = 200))
    check(!shouldRetryMirror("", alreadyRetried = false, isIoException = false, httpCode = 404))
    check(!shouldRetryMirror("", alreadyRetried = false, isIoException = false, httpCode = 429))

    // 4. shouldFallbackWsrv: kích hoạt cho cả IOException lẫn HTTP lỗi khi host == wsrv.nl và có ct_src
    val sampleSrc = "https://storage-bravo.cuutruyen.net/file/page1.jpg"
    check(shouldFallbackWsrv("wsrv.nl", sampleSrc, isIoException = true, httpCode = null))
    check(shouldFallbackWsrv("wsrv.nl", sampleSrc, isIoException = false, httpCode = null))
    check(shouldFallbackWsrv("wsrv.nl", sampleSrc, isIoException = false, httpCode = 502))
    check(shouldFallbackWsrv("wsrv.nl", sampleSrc, isIoException = false, httpCode = 403))
    check(shouldFallbackWsrv("wsrv.nl", sampleSrc, isIoException = false, httpCode = 429))
    check(!shouldFallbackWsrv("wsrv.nl", sampleSrc, isIoException = false, httpCode = 200))
    check(!shouldFallbackWsrv("wsrv.nl", sampleSrc, isIoException = false, httpCode = 204))
    check(!shouldFallbackWsrv("wsrv.nl", null, isIoException = true, httpCode = null))
    check(!shouldFallbackWsrv("wsrv.nl", "", isIoException = true, httpCode = null))
    check(!shouldFallbackWsrv("storage-bravo.cuutruyen.net", sampleSrc, isIoException = true, httpCode = null))

    // 5. buildWsrvFallbackUrl: bảo toàn ct_w, ct_h, ct_drm trên URL ảnh gốc
    val fallbackUrl = buildWsrvFallbackUrl(sampleSrc, "1024", "1450", "abc+def==")
    check(fallbackUrl.startsWith("https://storage-bravo.cuutruyen.net/file/page1.jpg?")) { fallbackUrl }
    check("storage-bravo.cuutruyen.net" in fallbackUrl) { fallbackUrl }
    check("ct_w=1024" in fallbackUrl) { fallbackUrl }
    check("ct_h=1450" in fallbackUrl) { fallbackUrl }
    check("ct_drm=" in fallbackUrl) { fallbackUrl }

    println("OK")
}
