package eu.kanade.tachiyomi.extension.vi.cuutruyen

import eu.kanade.tachiyomi.source.model.Filter

/**
 * A tag that can be included ("and") or excluded ("not") in a search, exactly like the
 * `canExclude` multi-select filters from the original Aidoku source's filters.json.
 * Long-press (or tap twice) in the Mihon filter sheet to cycle ignore -> include -> exclude.
 */
class TagFilter(name: String) : Filter.TriState(name)

class TagGroupFilter(name: String, tags: List<String>) :
    Filter.Group<TagFilter>(name, tags.map { TagFilter(it) })

/**
 * Extra listings the original extension exposed as separate tabs ("Nổi bật", "Top tuần",
 * "Top tháng", "Truyện việt"). Classic Tachiyomi/Mihon sources only get a Popular + Latest tab,
 * so the rest are folded into this sort option, used only when the search query is empty.
 */
class SortFilter : Filter.Select<String>(
    "Sắp xếp (khi không tìm kiếm)",
    arrayOf("Mới cập nhật", "Nổi bật", "Top tuần", "Top tháng", "Truyện Việt"),
)

object CuuTruyenFilters {

    private val boTruyen = listOf("Manga", "Manhua", "Manhwa", "Web Comic", "Webtoon")

    private val trangThai = listOf("Đang Tiến Hành", "Đã Hoàn Thành", "Tạm Ngưng", "Drop")

    private val nhanKhauHoc = listOf("Shounen", "Shoujo", "Seinen", "Josei")

    private val theLoai = listOf(
        "4 Koma", "Action", "Adaption", "Adventure", "Aliens", "Animals", "Anime", "Atlus",
        "Award Winning", "Comedy", "Cooking", "Crime", "Crossdressing", "Dark Fantasy",
        "Databook", "Demons", "Delinquents", "Doujinshi", "Drama", "Ecchi", "Fantasy",
        "Full Color", "Gender Blender", "Genderswap", "Girl Love", "Ghosts", "Gore", "Gyaru",
        "Harem", "Historical", "Horror", "Idol", "Isekai", "Loli", "Long Strip", "Lgbt",
        "Magic", "Martial Arts", "Mecha", "Medical", "Military", "Monster Girls", "Monsters",
        "Mystery", "Ninja", "Oneshot", "Philosophical", "Police", "Reincarnation",
        "Psychological", "Romance", "Rpg", "Samurai", "School Life", "Sci fi", "Sega",
        "Slice Of Life", "Smut", "Sport", "Supernatural", "Survival", "Time Travel",
        "Thriller", "Tragedy", "Vampires", "Video Games", "Virtual Reality", "Wholesome",
        "Yandere", "Yakuza", "Yaoi", "Yonkoma", "Yuri", "Zombie", "Ẩm Thực", "Bạo Lực",
        "Bỉ Ẩn", "Bi Kịch", "Cảnh Sát", "Chất Lượng Cao", "Chính Kịch", "Chinh Trị",
        "Chuyển Sinh", "Chuyển Thể", "Có Màu", "Cổng Sở", "Đời Thường", "Động Vật",
        "Du Hành Thời Gian", "Game", "Giật Gân", "Hài Hước", "Hành Động", "Hậu Tận Thế",
        "Hệ Thống", "Học Đường", "Khoa Học", "Khoa Học Viễn Tưởng", "Khỏa Thân", "Kinh Dị",
        "Lãng Mạn", "Lịch Sử", "Máu Me", "Miễn Bản Quyển", "Nam Biến Nữ", "Nam Giả Nữ",
        "Nam x Nam", "Ngọt Ngào", "Nữ Giả Nam", "Phép Thuật", "Phiêu Lưu", "Quái Vật",
        "Quân Đội", "Romcom", "Sát Thủ", "Siêu Nhiên", "Sinh Tồn", "Tâm Lý", "Thể Thao",
        "Thiếu Niên", "Tình Dục", "Tình Yêu", "Tình Yêu Không Được Đáp Lại",
        "Tình Yêu Thuần Khiết", "Toán Học", "Tội Phạm", "Trap", "Trinh Thám", "Trung Cổ",
        "Truyện Việt", "Tu Tiên", "Tuyển Tập", "Việt Nam", "Vô Cp", "Võ Thuật", "Xuyên Không",
        "Y Học",
    )

    fun list(): List<Filter<*>> = listOf(
        SortFilter(),
        Filter.Separator(),
        TagGroupFilter("Bộ Truyện", boTruyen),
        // Original Aidoku source's group title has a typo ("Trạng trái"); corrected here.
        TagGroupFilter("Trạng thái", trangThai),
        TagGroupFilter("Nhân khẩu học", nhanKhauHoc),
        TagGroupFilter("Thể loại", theLoai),
    )

    /**
     * Rebuilds the `tags` query value the same way the original Rust source did:
     * every included tag is quoted and "and"-ed together, every excluded tag is
     * quoted, prefixed with "not " and also "and"-ed in.
     */
    fun buildTagsParam(filters: List<Filter<*>>): String? {
        val included = mutableListOf<String>()
        val excluded = mutableListOf<String>()

        filters.filterIsInstance<TagGroupFilter>().forEach { group ->
            group.state.forEach { tag ->
                when {
                    tag.isIncluded() -> included.add(tag.name)
                    tag.isExcluded() -> excluded.add(tag.name)
                }
            }
        }

        if (included.isEmpty() && excluded.isEmpty()) return null

        val parts = mutableListOf<String>()
        if (included.isNotEmpty()) {
            parts.add(included.joinToString(" and ") { "\"$it\"" })
        }
        if (excluded.isNotEmpty()) {
            parts.add(excluded.joinToString(" and ") { "not \"$it\"" })
        }
        return parts.joinToString(" and ")
    }
}
