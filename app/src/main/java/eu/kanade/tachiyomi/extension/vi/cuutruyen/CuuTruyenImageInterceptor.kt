package eu.kanade.tachiyomi.extension.vi.cuutruyen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Cứu Truyện scrambles some page images by chopping them into horizontal strips and
 * shuffling their vertical order. The real layout is described by a small encrypted
 * string ("drm_data") that we stashed onto the image URL as query parameters when the
 * page list was parsed (see [CuuTruyen.pageListParse]).
 *
 * This mirrors the `process_page_image` logic from the original Aidoku Rust source
 * 1:1: XOR-decrypt the base64 payload with a fixed key, then read a
 * "|dy-height|dy-height|..." description of where each source strip belongs in the
 * final image.
 */
class CuuTruyenImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var firstError: IOException? = null
        val firstResponse = try {
            chain.proceed(request)
        } catch (e: IOException) {
            firstError = e
            null
        }

        // Proxy giảm dung lượng (wsrv.nl) lỗi/quá tải -> tải lại ảnh gốc thay vì làm hỏng trang.
        val src = request.url.queryParameter(SRC_PARAM)
        val response = if (shouldFallbackWsrv(request.url.host, src, isIoException = firstError != null, httpCode = firstResponse?.code)) {
            firstResponse?.close()
            // Giữ lại tham số ct_* để ảnh gốc vẫn được ghép lại đúng.
            val fallbackUrl = buildWsrvFallbackUrl(
                srcUrl = src!!,
                widthParam = request.url.queryParameter(WIDTH_PARAM),
                heightParam = request.url.queryParameter(HEIGHT_PARAM),
                drmParam = request.url.queryParameter(DRM_PARAM),
            )
            val fallback = fallbackUrl.toHttpUrlOrNull() ?: throw IOException("URL fallback không hợp lệ: $fallbackUrl")
            request = request.newBuilder().url(fallback).build()
            chain.proceed(request)
        } else {
            firstResponse ?: throw firstError!!
        }

        val drmData = request.url.queryParameter(DRM_PARAM)
        val width = request.url.queryParameter(WIDTH_PARAM)?.toIntOrNull() ?: 0
        val height = request.url.queryParameter(HEIGHT_PARAM)?.toIntOrNull() ?: 0

        if (drmData.isNullOrEmpty() || width <= 0 || height <= 0 || !response.isSuccessful) {
            return response
        }

        val rawBytes = response.body.bytes()
        val original = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: return response.newBuilder().body(rawBytes.toResponseBody(response.body.contentType())).build()

        val descrambled = try {
            val result = descramble(original, width, height, drmData)
            if (result !== original) {
                original.recycle()
            }
            result
        } catch (e: Exception) {
            original
        }

        val bytes = try {
            val output = ByteArrayOutputStream(128 * 1024)
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            descrambled.compress(format, OUTPUT_IMAGE_QUALITY, output)
            output.toByteArray()
        } finally {
            descrambled.recycle()
        }

        return response.newBuilder()
            .body(bytes.toResponseBody(OUTPUT_IMAGE_MIME.toMediaType()))
            .build()
    }

    private fun descramble(source: Bitmap, width: Int, height: Int, drmData: String): Bitmap {
        val decoded = Base64.decode(drmData.replace("\n", ""), Base64.DEFAULT)
        val keyBytes = DRM_KEY.toByteArray(Charsets.US_ASCII)
        val xored = ByteArray(decoded.size) { i ->
            (decoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        val layout = String(xored, Charsets.UTF_8)

        // Ảnh tải về có thể đã bị proxy thu nhỏ: co mọi tọa độ dải theo tỉ lệ (1.0 nếu là ảnh gốc).
        val scale = source.width.toDouble() / width
        val outHeight = Math.round(height * scale).toInt()
        val output = Bitmap.createBitmap(source.width, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        var sy = 0
        // The first "|"-separated segment is always empty (the string starts with "|"),
        // matching the Rust code's `.split("|").skip(1)`.
        layout.split("|").drop(1).forEach { segment ->
            val parts = segment.split("-")
            if (parts.size != 2) return@forEach

            val dy = parts[0].toIntOrNull() ?: return@forEach
            val partHeight = parts[1].toIntOrNull() ?: return@forEach
            if (partHeight <= 0) return@forEach

            val (srcTop, srcBottom) = scaleSpan(sy, partHeight, scale)
            val (dstTop, dstBottom) = scaleSpan(dy, partHeight, scale)
            val srcRect = Rect(0, srcTop, source.width, srcBottom.coerceAtMost(source.height))
            val dstRect = Rect(0, dstTop, source.width, dstBottom)
            canvas.drawBitmap(source, srcRect, dstRect, null)
            sy += partHeight
        }

        return output
    }

    companion object {
        private const val DRM_KEY = "3141592653589793"
        const val DRM_PARAM = "ct_drm"
        const val WIDTH_PARAM = "ct_w"
        const val HEIGHT_PARAM = "ct_h"
        const val SRC_PARAM = "ct_src" // URL ảnh gốc, dùng để fallback khi proxy lỗi
    }
}

class MirrorFallbackInterceptor(
    private val proxyPrefProvider: () -> String = { "" },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        return executeWithMirrorFallback(
            url = req.url.toString(),
            host = req.url.host,
            proxyPref = proxyPrefProvider(),
            getCode = { it.code },
            closeResponse = { it.close() },
            proceed = { targetUrl ->
                val nextUrl = targetUrl.toHttpUrlOrNull() ?: throw IOException("URL mirror không hợp lệ: $targetUrl")
                val nextReq = if (targetUrl == req.url.toString()) req else req.newBuilder().url(nextUrl).build()
                chain.proceed(nextReq)
            },
        )
    }
}
