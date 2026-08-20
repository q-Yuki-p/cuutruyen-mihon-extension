package eu.kanade.tachiyomi.extension.vi.cuutruyen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream

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
        val request = chain.request()
        val response = chain.proceed(request)

        val drmData = request.url.queryParameter(DRM_PARAM)
        val width = request.url.queryParameter(WIDTH_PARAM)?.toIntOrNull() ?: 0
        val height = request.url.queryParameter(HEIGHT_PARAM)?.toIntOrNull() ?: 0

        if (drmData.isNullOrEmpty() || width <= 0 || height <= 0 || !response.isSuccessful) {
            return response
        }

        // BitmapFactory.decodeStream() never closes the stream it's given, and this
        // interceptor runs on every single page image request -- leaving it open would
        // leak the underlying OkHttp connection on every page of every chapter read.
        val original = response.body.byteStream().use { BitmapFactory.decodeStream(it) }
            ?: return response

        val descrambled = try {
            descramble(original, width, height, drmData)
        } catch (e: Exception) {
            original
        }

        val output = ByteArrayOutputStream()
        descrambled.compress(Bitmap.CompressFormat.JPEG, 95, output)
        val bytes = output.toByteArray()

        return response.newBuilder()
            .body(bytes.toResponseBody("image/jpeg".toMediaType()))
            .build()
    }

    private fun descramble(source: Bitmap, width: Int, height: Int, drmData: String): Bitmap {
        val decoded = Base64.decode(drmData.replace("\n", ""), Base64.DEFAULT)
        val keyBytes = DRM_KEY.toByteArray(Charsets.US_ASCII)
        val xored = ByteArray(decoded.size) { i ->
            (decoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        val layout = String(xored, Charsets.UTF_8)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
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

            val srcRect = Rect(0, sy, width, sy + partHeight)
            val dstRect = Rect(0, dy, width, dy + partHeight)
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
    }
}
