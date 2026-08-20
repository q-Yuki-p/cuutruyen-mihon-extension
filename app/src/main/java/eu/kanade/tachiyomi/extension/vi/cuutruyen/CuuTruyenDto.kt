package eu.kanade.tachiyomi.extension.vi.cuutruyen

import kotlinx.serialization.Serializable

/**
 * Generic API envelope used by every endpoint of the Cứu Truyện v2 API.
 * `_metadata.total_pages` is only present on paginated list endpoints.
 */
@Serializable
data class CuuResponse<T>(
    val data: T,
    val _metadata: CuuMeta? = null,
)

@Serializable
data class CuuMeta(
    val total_pages: Int? = null,
)

@Serializable
data class CuuMangaShort(
    val id: Int,
    val name: String,
    val cover_url: String? = null,
)

@Serializable
data class CuuName(
    val name: String = "",
)

@Serializable
data class CuuMangaDetails(
    val id: Int,
    val name: String,
    val cover_url: String? = null,
    val full_description: String? = null,
    val is_nsfw: Boolean = false,
    val author: CuuName = CuuName(),
    val team: CuuName = CuuName(),
    val tags: List<CuuName> = emptyList(),
)

@Serializable
data class CuuChapter(
    val id: Int,
    val number: String,
    val name: String? = null,
    val created_at: String,
)

@Serializable
data class CuuChapterPages(
    val pages: List<CuuPage> = emptyList(),
)

@Serializable
data class CuuPage(
    val image_url: String,
    val drm_data: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)
