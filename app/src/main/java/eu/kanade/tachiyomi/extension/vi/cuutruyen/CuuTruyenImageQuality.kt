package eu.kanade.tachiyomi.extension.vi.cuutruyen

import java.net.URLEncoder
import kotlin.math.roundToInt

const val OUTPUT_IMAGE_QUALITY = 85
const val OUTPUT_IMAGE_MIME = "image/webp"

/**
 * Mức chất lượng ảnh trang. Đo thực tế trên 1 trang 1024x1450 (gốc ~400 KB):
 * Cân bằng ~134 KB (-66%), Tiết kiệm ~89 KB (-78%).
 * Ảnh được resize + chuyển WebP bởi wsrv.nl trước khi tải về máy.
 */
enum class ImageQuality(val key: String, val label: String, val maxWidth: Int, val quality: Int) {
    ORIGINAL("original", "Nguồn (gốc, tốn data nhất)", 0, 0),
    BALANCED("balanced", "Cân bằng (~-66% data)", 900, 70),
    SAVER("saver", "Tiết kiệm (~-78% data)", 720, 60),
    ;

    /** `wsrv.nl` nhận `host/path` không cần scheme; `we` = không phóng to ảnh nhỏ hơn maxWidth. */
    fun proxyUrl(originalUrl: String): String {
        val target = originalUrl.substringBefore("?").substringAfter("://")
        return "https://wsrv.nl/?url=${URLEncoder.encode(target, "UTF-8")}&w=$maxWidth&we&q=$quality&output=webp"
    }

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: ORIGINAL
    }
}

/**
 * Co một dải [top, top+height) theo tỉ lệ [scale] bằng cách làm tròn *biên*, không làm tròn
 * chiều cao, nên hai dải liền kề luôn dùng chung một biên (không hở, không chồng 1px).
 */
fun scaleSpan(top: Int, height: Int, scale: Double): Pair<Int, Int> =
    (top * scale).roundToInt() to ((top + height) * scale).roundToInt()
