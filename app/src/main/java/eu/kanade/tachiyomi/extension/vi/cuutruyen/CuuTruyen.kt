package eu.kanade.tachiyomi.extension.vi.cuutruyen

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Kotlin/Mihon port of the Aidoku "vi.cuutruyen" source (https://cuutruyen.net), originally
 * written in Rust by @c0ntens. See README.md in this repository for a full mapping between
 * the two implementations.
 *
 * Targets tachiyomix 1.6 (suspend API) directly -- verified field-for-field against the real,
 * currently-shipping source-api bundled in both mihonapp/mihon and Suwayomi/Suwayomi-Server.
 */
class CuuTruyen : HttpSource(), ConfigurableSource {

    override val name = "Cứu Truyện"

    override val lang = "vi"

    // Bump this if cuutruyen.net ever changes its API/urls in an incompatible way.
    override val versionId = 1

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0)
    }

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(CuuTruyenImageInterceptor())
        .build()

    // ------------------------------------------------------------------------------------
    // Base URL / mirrors / proxy (mirrors BaseUrlProvider::get_base_url from the Rust source)
    // ------------------------------------------------------------------------------------

    private fun apiBaseUrl(): String {
        val domain = preferences.getString(PREF_DOMAIN_KEY, DOMAIN_DEFAULT)!!
        val proxy = preferences.getString(PREF_PROXY_KEY, "")!!
        return if (proxy.isEmpty()) domain else "$proxy/?url=$domain"
    }

    override val baseUrl: String get() = apiBaseUrl()

    private fun apiUrl(path: String) = "${apiBaseUrl()}$path"

    // ------------------------------------------------------------------------------------
    // Popular ("Nổi bật" / top of all time)
    // ------------------------------------------------------------------------------------

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.newCall(
            GET(apiUrl("/api/v2/mangas/top?duration=all&page=$page&per_page=24"), headers),
        ).awaitSuccess()
        return parseMangaList(response, page)
    }

    // ------------------------------------------------------------------------------------
    // Latest updates
    // ------------------------------------------------------------------------------------

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.newCall(
            GET(apiUrl("/api/v2/mangas/recently_updated?page=$page&per_page=30"), headers),
        ).awaitSuccess()
        return parseMangaList(response, page)
    }

    // ------------------------------------------------------------------------------------
    // Search (also covers the extra listings the Aidoku source exposed as separate tabs)
    // ------------------------------------------------------------------------------------

    override fun getFilterList(): FilterList = FilterList(CuuTruyenFilters.list())

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val tags = CuuTruyenFilters.buildTagsParam(filters)
        val sort = filters.filterIsInstance<SortFilter>().firstOrNull()?.state ?: 0

        val url = when {
            query.isNotBlank() || tags != null -> {
                buildString {
                    append(apiUrl("/api/v2/mangas/search?"))
                    if (query.isNotBlank()) append("q=${encode(query)}&")
                    if (tags != null) append("tags=${encode(tags)}&")
                    append("page=$page&per_page=24")
                }
            }
            sort == 1 -> apiUrl("/api/v2/mangas/top?duration=all&page=$page&per_page=24")
            sort == 2 -> apiUrl("/api/v2/mangas/top?duration=week&page=$page&per_page=24")
            sort == 3 -> apiUrl("/api/v2/mangas/top?duration=month&page=$page&per_page=24")
            sort == 4 -> apiUrl("/api/v2/mangas/search?tags=${encode("\"Truyện Việt\"")}&page=$page&per_page=24")
            else -> apiUrl("/api/v2/mangas/recently_updated?page=$page&per_page=30")
        }

        val response = client.newCall(GET(url, headers)).awaitSuccess()
        return parseMangaList(response, page)
    }

    private fun parseMangaList(response: Response, page: Int): MangasPage {
        val envelope = response.parseAs<CuuResponse<List<CuuMangaShort>>>()
        val totalPages = envelope._metadata?.total_pages
        return MangasPage(
            envelope.data.map { it.toSManga() },
            totalPages != null && page < totalPages,
        )
    }

    private fun CuuMangaShort.toSManga() = SManga.create().apply {
        url = "/api/v2/mangas/$id"
        title = name
        thumbnail_url = cover_url?.let { rewriteStorageUrl(it) }
    }

    // ------------------------------------------------------------------------------------
    // Manga details + chapter list (combined suspend API -- fetched in parallel when both
    // are requested, since getMangaUpdate hands us manga directly and no longer needs the
    // manga id smuggled through a request header the way the old chapterListParse(Response)
    // split required).
    // ------------------------------------------------------------------------------------

    override fun getMangaUrl(manga: SManga): String {
        val id = manga.url.substringAfterLast("/")
        return "https://truycapcuutruyen.pages.dev/mangas/$id"
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.url.substringAfterLast("/")

        val detailsDeferred = if (fetchDetails) async { fetchMangaDetails(manga.url) } else null
        val chaptersDeferred = if (fetchChapters) async { fetchChapterList(manga.url, mangaId) } else null

        val detailsResult = detailsDeferred?.await()
        val newChapters = chaptersDeferred?.await()?.let { list ->
            val scanlator = detailsResult?.scanlator
            if (scanlator != null) list.onEach { it.scanlator = scanlator } else list
        }

        SMangaUpdate(
            detailsResult?.manga ?: manga,
            newChapters ?: chapters,
        )
    }

    private data class MangaDetailsResult(val manga: SManga, val scanlator: String?)

    private suspend fun fetchMangaDetails(mangaUrl: String): MangaDetailsResult {
        val response = client.newCall(GET(apiUrl(mangaUrl), headers)).awaitSuccess()
        val details = response.parseAs<CuuResponse<CuuMangaDetails>>().data
        val tags = details.tags.map { titleCase(it.name) }

        val manga = SManga.create().apply {
            url = "/api/v2/mangas/${details.id}"
            title = details.name
            thumbnail_url = details.cover_url?.let { rewriteStorageUrl(it) }
            author = details.author.name.ifBlank { null }
            description = parseDescription(details.full_description)
            genre = tags.joinToString(", ")
            status = when {
                tags.any { it == "Đã Hoàn Thành" } -> SManga.COMPLETED
                tags.any { it == "Đang Tiến Hành" } -> SManga.ONGOING
                tags.any { it == "Tạm Ngưng" } -> SManga.ON_HIATUS
                tags.any { it == "Drop" } -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            // Truyện đã xong/bị drop chắc chắn không còn chương mới -- bỏ qua khi Mihon/Suwayomi
            // refresh hàng loạt thư viện, giảm tải cho cuutruyen.net.
            update_strategy = if (status == SManga.COMPLETED || status == SManga.CANCELLED) {
                UpdateStrategy.ONLY_FETCH_ONCE
            } else {
                UpdateStrategy.ALWAYS_UPDATE
            }
        }
        return MangaDetailsResult(manga, details.team.name.ifBlank { null })
    }

    private suspend fun fetchChapterList(mangaUrl: String, mangaId: String): List<SChapter> {
        val response = client.newCall(GET(apiUrl("$mangaUrl/chapters"), headers)).awaitSuccess()
        return response.parseAs<CuuResponse<List<CuuChapter>>>().data.map { it.toSChapter(mangaId) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (id, mangaId) = chapter.url.let {
            it.substringAfterLast("/").substringBefore("?") to it.substringAfter("m=", "")
        }
        return if (mangaId.isNotEmpty()) {
            "https://truycapcuutruyen.pages.dev/mangas/$mangaId/chapters/$id"
        } else {
            apiUrl(chapter.url)
        }
    }

    private fun CuuChapter.toSChapter(mangaId: String, scanlator: String? = null): SChapter {
        val parsedNumber = number.toFloatOrNull() ?: -1f
        // Mihon không tự thêm "Ch.X" như Aidoku làm ở tầng UI -- phải ghép tay ở đây, luôn luôn,
        // bất kể number có parse được thành số hay không (khớp đúng những gì trang web thật và
        // extension MangaDex của Keiyoushi hiển thị).
        val chapterName = if (name.isNullOrEmpty()) "Ch.$number" else "Ch.$number - $name"
        return SChapter.create().apply {
            // The ?m= suffix is still needed here (not just for the old chapterListParse
            // header trick, which is gone): getChapterUrl(chapter) only receives the chapter,
            // not the manga, so the manga id has to travel inside the chapter's own url.
            url = "/api/v2/chapters/$id?m=$mangaId"
            this.name = chapterName
            chapter_number = parsedNumber
            date_upload = parseIsoDate(created_at)
            this.scanlator = scanlator
        }
    }

    // ------------------------------------------------------------------------------------
    // Pages (+ DRM metadata handed off to CuuTruyenImageInterceptor)
    // ------------------------------------------------------------------------------------

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.newCall(
            GET(apiUrl(chapter.url.substringBefore("?")), headers),
        ).awaitSuccess()
        val pages = response.parseAs<CuuResponse<CuuChapterPages>>().data.pages

        return pages.mapIndexed { index, p ->
            val base = rewriteStorageUrl(p.image_url)
            val sep = if (base.contains("?")) "&" else "?"
            val url = buildString {
                append(base)
                append(sep)
                append("${CuuTruyenImageInterceptor.WIDTH_PARAM}=${p.width ?: 0}")
                append("&${CuuTruyenImageInterceptor.HEIGHT_PARAM}=${p.height ?: 0}")
                append("&${CuuTruyenImageInterceptor.DRM_PARAM}=${encode(p.drm_data.orEmpty())}")
            }
            Page(index, imageUrl = url)
        }
    }

    // ------------------------------------------------------------------------------------
    // Preferences
    // ------------------------------------------------------------------------------------

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = "Miền (domain)"
            entries = arrayOf("cuutruyen.net", "hetcuutruyen.net")
            entryValues = arrayOf("https://cuutruyen.net", "https://hetcuutruyen.net")
            setDefaultValue(DOMAIN_DEFAULT)
            summary = "%s"
        }.also { screen.addPreference(it) }

        ListPreference(screen.context).apply {
            key = PREF_PROXY_KEY
            title = "Proxy"
            entries = arrayOf("Tắt", "Shin Proxy")
            entryValues = arrayOf("", "https://light-pig-37.tachibana-shin.deno.net")
            setDefaultValue("")
            summary = "%s"
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_PROXY_KEY = "pref_proxy"
        private const val DOMAIN_DEFAULT = "https://cuutruyen.net"
    }
}
