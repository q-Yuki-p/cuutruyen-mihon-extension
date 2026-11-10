// Chạy: kotlinc app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyenImageQuality.kt \
//         scripts/CheckImageQuality.kt -include-runtime -d /tmp/ciq.jar && java -cp /tmp/ciq.jar eu.kanade.tachiyomi.extension.vi.cuutruyen.CheckImageQualityKt
package eu.kanade.tachiyomi.extension.vi.cuutruyen

fun main() {
    // Biên liền kề phải trùng nhau ở mọi tỉ lệ -> không hở/chồng giữa các dải.
    for (scale in listOf(1.0, 0.703125, 0.8789, 0.5)) {
        var top = 0
        var prevEnd = 0
        listOf(333, 421, 77, 619).forEach { h ->
            val (a, b) = scaleSpan(top, h, scale)
            check(a == prevEnd) { "gap/overlap at scale=$scale top=$top: $a != $prevEnd" }
            prevEnd = b
            top += h
        }
        check(prevEnd == (top * scale).let { Math.round(it).toInt() }) { "total height mismatch at $scale" }
    }
    check(scaleSpan(40, 100, 1.0) == 40 to 140) // scale 1.0 = hành vi cũ

    val u = ImageQuality.SAVER.proxyUrl("https://storage-bravo.cuutruyen.net/a/b.jpg?ct_w=1&ct_drm=x%2By")
    check(u == "https://wsrv.nl/?url=storage-bravo.cuutruyen.net%2Fa%2Fb.jpg&w=720&we&q=60&output=webp") { u }
    check(ImageQuality.fromKey("bogus") == ImageQuality.ORIGINAL)
    check(OUTPUT_IMAGE_QUALITY == 85) { "Expected WebP quality 85, got $OUTPUT_IMAGE_QUALITY" }
    check(OUTPUT_IMAGE_MIME == "image/webp") { "Expected image/webp, got $OUTPUT_IMAGE_MIME" }
    check(232458.0 / 400898.0 <= 1.10)
    check(104830.0 / 89294.0 <= 1.50)
    println("OK")
}
