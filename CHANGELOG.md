# Changelog — Sửa lỗi mô tả 1 dòng, hoàn tất migrate AGP 9, cập nhật CI (2026-08-18)

## 1. Sửa lỗi mô tả truyện dồn hết vào 1 dòng (báo lỗi kèm screenshot)

**Nguyên nhân xác nhận bằng thực nghiệm** (không suy đoán): viết harness Java độc lập chạy
Jsoup thật (cài qua `apt install libjsoup-java`) test 4 kịch bản. Kết quả:
`Jsoup.parse("<p>A</p><p>B</p>").wholeText()` → `"AB"` — **dính liền hoàn toàn, không phải chỉ
mất định dạng**. Code cũ chỉ convert `<br>` → `\n` bằng regex trước khi parse; khi API trả về mô
tả dùng thẻ `<p>`/`<div>` (không phải `<br>`) thì không có gì được convert, và `wholeText()` nối
các đoạn sát nhau không một khoảng trắng nào.

**Fix**: `CuuTruyenUtils.kt::parseDescription` — dùng kỹ thuật `select("br").append("\\n")` +
`select("p, div").prepend("\\n")` (chèn text-node placeholder trước khi extract, vì kể cả ký tự
`\n` thật chèn tay cũng bị `.text()`/`.wholeText()` chuẩn hoá mất) rồi mới thay lại thành `\n`
thật sau cùng. Đã test lại harness xác nhận cả 4 kịch bản (chỉ `<br>`, chỉ `<p>`, `<p>`+`<br>`
lồng nhau, `<div>`) đều cho kết quả đúng, không còn khoảng trắng thừa.

## 2. Hoàn tất migrate sang AGP 9 built-in Kotlin (gỡ bỏ 2 cờ opt-out tạm thời)

Theo đúng plan đã trình bày ở lượt trước — nghiên cứu từ skill chính thức `android/skills` repo
của Google:
- Gỡ `id("kotlin-android")` khỏi `plugins{}` — AGP 9 tự biên dịch Kotlin khi built-in Kotlin bật.
- **Xoá hẳn** khối `sourceSets { named("main") {...} }` — không migrate API, mà xoá thẳng vì
  toàn bộ đường dẫn khai báo (`src/main/java`, `src/main/res`, `src/main/AndroidManifest.xml`)
  đã đúng y hệt mặc định chuẩn của AGP, code chỉ đang khai báo lại cái có sẵn.
- Gỡ `android.newDsl=false` + `android.builtInKotlin=false` khỏi `gradle.properties`.

Đã đối chiếu file `build.gradle.kts` với bảng "Deprecated/Removed DSL/APIs" đầy đủ trong release
notes chính thức AGP 9.0 — không có API nào ta dùng nằm trong danh sách bị ảnh hưởng
(`signingConfigs`, `buildTypes`, `defaultConfig`, `manifestPlaceholders` đều không đổi cú pháp).

## 3. Cập nhật GitHub Actions (giải quyết warning Node 20 + setup-java v4 deprecated)

| Action | Trước | Sau |
|---|---|---|
| `actions/checkout` | v4 | **v7** |
| `actions/setup-java` | v4 (deprecated, chạy Node cũ) | **v5** (chạy Node 24, hết cảnh báo) |
| `android-actions/setup-android` | v3 | **v4** |

## Bằng chứng xác minh

- `python3 scripts/test_generate_index.py` → 11/11 PASS (không liên quan, chạy lại cho chắc).
- Harness Jsoup độc lập (Java thật, không phải Kotlin — nhưng cùng thư viện/API `select()`/
  `append()`/`prepend()`/`text()` dùng chung) — 4/4 kịch bản đúng, có log chạy thật kèm theo.
- `git diff --stat` xác nhận đúng 4 file thay đổi, không lệch phạm vi.
- Vẫn chưa build được APK thật trong sandbox này (không có Android SDK) — cần xác nhận qua
  GitHub Actions.

---

# Changelog — Sửa tiêu đề chương, scanlator, update_strategy (2026-08-17)

Thực thi theo plan `2026-08-17-cuutruyen-chapter-title-scanlator-upgrade.md`, đã duyệt trước khi
làm. 5 commit tách biệt, mỗi commit đúng 1 Task.

## Bối cảnh

Bạn phát hiện qua screenshot: danh sách chương của extension thiếu tiền tố "Ch.{số}" so với web
thật và extension MangaDex của Keiyoushi. Điều tra cho thấy đây không phải bug trong logic gốc —
Aidoku tự thêm "Ch.X" ở tầng UI client, còn Mihon thì không, nên phần Kotlin port phải tự ghép
chuỗi hoàn chỉnh. Nhân tiện rà soát thêm 2 hạng mục Important đã phát hiện ở lượt code review
trước (scanlator, update_strategy) và 1 mục dọn dẹp nhỏ.

## Thay đổi

| # | Commit | File | Nội dung |
|---|---|---|---|
| 1 | `fix: always prefix...` | `CuuTruyen.kt` | `toSChapter`: gộp 2 nhánh if/else thành 1 dòng, luôn ghép "Ch.{number}" + " - {name}" (nếu có tên) — khớp đúng quy ước Mihon/Keiyoushi, không dựa vào auto-format phía UI (Mihon không có). |
| 2 | `feat: attach translation team...` | `CuuTruyen.kt` | `fetchMangaDetails` trả thêm tên nhóm dịch (`details.team.name`) qua `MangaDetailsResult`; `getMangaUpdate` gắn vào từng `SChapter.scanlator` sau khi 2 request song song hoàn tất — **không** làm chậm lại luồng tải song song đã tối ưu ở lượt trước. |
| 3 | `feat: skip completed/dropped...` | `CuuTruyen.kt` | `SManga.update_strategy = ONLY_FETCH_ONCE` khi trạng thái Hoàn Thành/Drop, `ALWAYS_UPDATE` các trường hợp còn lại — giảm tải cuutruyen.net khi Mihon/Suwayomi refresh thư viện hàng loạt. |
| 4 | `chore: derive extensionLib...` | `build.gradle.kts`, `AndroidManifest.xml` | `tachiyomix.extensionLib` giờ lấy từ `manifestPlaceholders["extensionLib"] = libVersion` thay vì hard-code `"1.6"` riêng — 1 nguồn sự thật duy nhất. |
| 5 | `docs: record why deep-link...` | `README.md` | Ghi chú kỹ thuật giải thích tại sao chưa làm deep-link: `ResolvableSource` có thật trên cả 2 platform nhưng thiếu trong `tachiyomix` JitPack; tự khai báo lại cục bộ sẽ âm thầm không hoạt động do cơ chế `ChildFirstPathClassLoader`. |

**Không đổi**: `CuuTruyenDto.kt`, `CuuTruyenFilters.kt`, `CuuTruyenImageInterceptor.kt`,
`CuuTruyenUtils.kt` — ngoài phạm vi của 5 Task trên.

## Bằng chứng xác minh

- Thuật toán ghép tên chương: test Python với 6 case lấy trực tiếp từ screenshot bạn gửi — RED
  (logic cũ fail đúng 2/2 case) → GREEN (logic mới pass 6/6 case).
- Mọi khối code "trước khi sửa" trong plan đã đối chiếu byte-for-byte với file thật trước khi
  viết plan, và lại một lần nữa trước khi thực thi từng Task.
- `SChapter.scanlator`, `SManga.update_strategy`, enum `UpdateStrategy` — xác nhận tồn tại đúng
  kiểu dữ liệu trong `source-api` thật của `mihonapp/mihon` (grep trực tiếp, có output ở trên).
- `git diff --stat` xác nhận đúng 4 file thay đổi, không lệch phạm vi Task nào.
- `scripts/test_generate_index.py`: 11/11 PASS (không liên quan tới thay đổi lần này nhưng chạy
  lại để chắc chắn không có tác dụng phụ ngoài ý muốn).
- **Chưa build được APK thật** trong sandbox này (không có Gradle/Android SDK) — cần chạy qua
  GitHub Actions để có xác nhận biên dịch cuối cùng.

---

# Changelog — Hiện đại hoá toàn diện lên tachiyomix 1.6

Chỉ nhắm tới **Mihon** và **Suwayomi-Server** bản mới nhất — bỏ hẳn hỗ trợ song song định dạng cũ
(`extensions-lib 1.4`, `index.min.json`, `repo.json`) theo yêu cầu.

## Vì sao phải làm việc này (bối cảnh)

Build CI báo lỗi `Could not find com.github.tachiyomiorg:extensions-lib:1.4` và
`com.github.inorichi.injekt:injekt-core:65b0440`. Truy nguyên gốc: cả 2 org GitHub gốc
(`tachiyomiorg`, và tác giả `inorichi`) đã ngừng hoạt động từ vụ Kakao Entertainment gửi thư cảnh
cáo pháp lý cho Tachiyomi (2024) — JitPack không còn build lại được các coordinate đó nữa.

Đối chiếu trực tiếp mã nguồn thật của `mihonapp/mihon` và `Suwayomi/Suwayomi-Server` (không suy
đoán) xác nhận cả hai đã hỗ trợ đầy đủ `tachiyomix 1.6` (API `suspend`) và định dạng repo mới
(`index.pb`/`NetworkExtensionStore`) — nên quyết định modernize toàn diện thay vì chỉ vá coordinate.

## File sửa

| File | Thay đổi chính |
|---|---|
| `app/src/.../CuuTruyen.kt` | Viết lại 5 hàm `*Request`/`*Parse` (Observable/RxJava) thành `suspend fun getPopularManga/getLatestUpdates/getSearchManga/getMangaUpdate/getPageList` (API tachiyomix 1.6). Gỡ cơ chế mượn header `X-CuuTruyen-Manga-Id` (không cần nữa — `getMangaUpdate` nhận thẳng `manga: SManga`). `getMangaUpdate` chạy tải chi tiết truyện + danh sách chương **song song** bằng `coroutineScope`/`async` khi cả 2 được yêu cầu. |
| `app/build.gradle.kts` | `libVersion` 1.4→1.6. `compileOnly` đổi từ `tachiyomiorg:extensions-lib`+`inorichi.injekt` sang `mihonapp:tachiyomix:1.6`+`mihonapp:injekt:91edab2317` (đúng bảng "App Dependency Requirements" chính thức của tachiyomix). Bump `okhttp` 4.12.0→5.4.0, `kotlinx-serialization-json` giữ 1.7.3 (khớp đúng yêu cầu tachiyomix, không phải version mới hơn của riêng Mihon), `jsoup` 1.17.2→1.22.2. Thêm `kotlinx-coroutines-core:1.10.2`. **Xoá hẳn** `io.reactivex:rxjava` (không còn dùng). |
| `build.gradle.kts` (root) | Bump Kotlin Gradle plugin 2.1.0→2.4.0 (yêu cầu tối thiểu của tachiyomix 1.6). |
| `app/src/main/AndroidManifest.xml` | Thay `tachiyomi.extension.nsfw` (thang 0/1) bằng `tachiyomix.name` + `tachiyomix.contentWarning` (thang 0/1/2) + `tachiyomix.extensionLib=1.6`, đúng theo README chính thức của `mihonapp/tachiyomix`. |
| `scripts/generate_index.py` | TDD: viết test mới trước (RED) rồi xoá hẳn `build_legacy_entry`/`build_repo_json` và toàn bộ output `index.min.json`/`repo.json` (GREEN), chỉ còn sinh `index.pb` + `index.json`. |
| `scripts/test_generate_index.py` | Viết lại theo schema mới: xác nhận không còn field cũ (`pkg`/`apk`/`code`/`baseUrl`), `extensionLib="1.6"`, round-trip protobuf đầy đủ. |
| `.github/workflows/build.yml` | Bỏ bước sinh/publish `repo.json`, đơn giản hoá "Assemble repo folder" (không còn tham số `--short-name`). |
| `README.md` | Viết lại toàn bộ hướng dẫn: 1 URL `index.pb` duy nhất cho cả Mihon lẫn Suwayomi, giải thích các quyết định kỹ thuật (giữ `genre`/`chapter_number`, gỡ header-smuggling, chạy song song). |

**Không đổi**: `CuuTruyenDto.kt`, `CuuTruyenFilters.kt`, `CuuTruyenImageInterceptor.kt`,
`CuuTruyenUtils.kt` — đã kiểm chứng OkHttp 5.x giữ nguyên API lõi (`Interceptor`/`Response`/
`MediaType`) so với 4.x (xác nhận trực tiếp từ phát biểu của tác giả OkHttp), và `genre`/
`chapter_number` (không phải `genres`/`number` mới hơn) vẫn là field thật cả 2 app đang đọc.

## Bằng chứng xác minh (Bước 5, TDD đầy đủ)

**Phần Python (`generate_index.py`) — TDD RED→GREEN→REFACTOR đầy đủ:**
```
RED:   1/11 test fail đúng chỗ cần fail (script cũ còn sinh index.min.json)
GREEN: python3 scripts/test_generate_index.py → 11/11 PASS
```
`index.pb` sinh ra được decode **bằng `protoc` + thư viện `protobuf` chính thức của Google**
(không dùng lại decoder tự viết trong test) — toàn bộ field, chuỗi "Cứu Truyện" có dấu, enum
`CONTENT_WARNING_NSFW`, ID nguồn 64-bit đều khớp chính xác.

**Phần Kotlin — không có Gradle/Android SDK trong sandbox này nên không compile được thật.**
Thay vào đó, đã đối chiếu **thủ công, từng dòng** mọi symbol/chữ ký hàm dùng trong `CuuTruyen.kt`
với đúng mã nguồn `tachiyomix-src` (tải trực tiếp từ `mihonapp/tachiyomix`, không suy đoán):
- 5 hàm `suspend fun` override khớp chính xác chữ ký khai báo trong `Source.kt`/`CatalogueSource.kt`
- Toàn bộ 19 import đều trỏ đúng symbol thật tồn tại trong stub (`awaitSuccess`, `GET`,
  `ConfigurableSource`, `FilterList`, `MangasPage`, `Page`, `SChapter`, `SManga`, `SMangaUpdate`,
  `HttpSource`...)
- `client`/`headers`/`versionId`/`id`/`baseUrl`/`name`/`lang`/`supportsLatest` — mọi property
  override đều khớp đúng kiểu dữ liệu khai báo trong stub
- Ngoặc `{}`/`()` cân bằng, không file nào bị cắt cụt

**Giới hạn còn lại**: đây KHÔNG thay thế cho việc build thật — vẫn cần chạy qua GitHub Actions
(đã sửa workflow) để có bằng chứng biên dịch + build APK thành công cuối cùng, vì sandbox này
không có mạng tới Maven Central/Google Maven/JitPack.
