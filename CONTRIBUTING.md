# Contributing

Cảm ơn bạn đã quan tâm đến `cuutruyen-mihon-extension`! File này gom lại các quyết định kỹ thuật
và lý do đằng sau chúng, để người đóng góp sau (kể cả tương lai của chính tác giả) không phải đoán
lại từ đầu.

## Cấu trúc project

| File | Vai trò |
|---|---|
| `app/src/.../CuuTruyen.kt` | Source chính — implement `HttpSource`, các hàm `suspend fun getPopularManga/getLatestUpdates/getSearchManga/getMangaUpdate/getPageList` (API `tachiyomix` 1.6). |
| `app/src/.../CuuTruyenDto.kt` | Model dữ liệu JSON trả về từ API `cuutruyen.net`. |
| `app/src/.../CuuTruyenFilters.kt` | Định nghĩa bộ lọc tìm kiếm (thể loại, trạng thái, sắp xếp). |
| `app/src/.../CuuTruyenImageInterceptor.kt` | OkHttp interceptor giải mã ảnh trang truyện bị xáo (DRM đơn giản của CuuTruyen). |
| `app/src/.../CuuTruyenUtils.kt` | Hàm tiện ích dùng chung (parse mô tả HTML, parse ngày ISO, title-case tiếng Việt...). |
| `scripts/generate_index.py` | Sinh `index.pb`/`index.json` cho repo — tự viết encoder protobuf, không phụ thuộc thư viện ngoài. |
| `scripts/test_generate_index.py` | Test cho script trên, gồm round-trip qua `protoc` thật của Google để xác nhận không tự lừa mình. |
| `.github/workflows/build.yml` | CI: build APK, ký bằng keystore cố định, sinh `index.pb`, publish lên nhánh `repo`. |

## Ghi chú kỹ thuật

- Dựa trên `com.github.mihonapp:tachiyomix:1.6` (API `suspend` mới thay cho `Observable`/RxJava
  của bản 1.4 cũ). Coordinate + bảng version `compileOnly` đi kèm (`okhttp`, `kotlinx-serialization-json`,
  `kotlinx-coroutines-core`, `jsoup`, `com.github.mihonapp:injekt`) được pin **đúng 1:1** theo bảng
  "App Dependency Requirements" chính thức trong README của `mihonapp/tachiyomix`, không suy đoán
  và **không** chạy theo phiên bản mới nhất độc lập của từng thư viện — xem phần "Cách cập nhật
  version" bên dưới, đây là điểm quan trọng nhất cần hiểu trước khi bump bất kỳ dependency nào.

- `getMangaUpdate` (gộp cả tải chi tiết truyện + danh sách chương làm 1 hàm, theo đúng API 1.6)
  chạy 2 request **song song** bằng `coroutineScope { async {...} }` khi Mihon cần cả hai, thay vì
  tuần tự như bản `*Request`/`*Parse` cũ — tải nhanh hơn thật, không chỉ đổi vỏ API.

- Cơ chế mượn header `X-CuuTruyen-Manga-Id` để mang `mangaId` từ `chapterListRequest` sang
  `chapterListParse` (bản 1.4 cũ) đã được **gỡ bỏ hoàn toàn** — API 1.6 đưa thẳng `manga: SManga`
  vào `getMangaUpdate`, không cần mượn qua header nữa. `?m=$mangaId` trong `SChapter.url` vẫn giữ
  lại vì `getChapterUrl(chapter)` (link "mở trong trình duyệt") chỉ nhận `chapter`, không nhận
  `manga`, nên vẫn cần mang `mangaId` đi kèm chapter.

- `genre`/`chapter_number` (không phải `genres`/`number` mới hơn) được giữ nguyên có chủ đích: đối
  chiếu trực tiếp `source-api/.../SManga.kt` và `SChapter.kt` **thật đang chạy** trong cả
  `mihonapp/mihon` lẫn `Suwayomi/Suwayomi-Server` (không phải nhánh `main` của `tachiyomix`, vốn
  đi trước những gì 2 app thật đã tích hợp) xác nhận cả hai app hiện tại vẫn chỉ đọc
  `genre`/`chapter_number`.

- `sources[].id` được `scripts/generate_index.py` tính bằng đúng công thức `HttpSource.generateId()`
  thật của Mihon (8 byte đầu MD5 của `"${name.lowercase()}/$lang/$versionId"`, xoá sign bit) — đối
  chiếu trực tiếp với mã nguồn `source-api/.../HttpSource.kt` của `mihonapp/mihon`, có unit test
  `scripts/test_generate_index.py` xác nhận khớp. CI (`build.yml`) tự `grep` cả `versionId` (từ
  `CuuTruyen.kt`) lẫn `libVersion`/`extVersionCode` (từ `build.gradle.kts`) rồi truyền tường minh
  vào script — không còn phụ thuộc giá trị mặc định hard-code nào, kể cả khi bạn đổi `name` hoặc
  bump `versionId` trong tương lai.

- `index.pb` được sinh bằng một encoder protobuf tự viết (không phụ thuộc thư viện ngoài) — đã
  kiểm chứng chéo bằng `protoc` + thư viện `protobuf` chính thức của Google, decode ra đúng từng
  field, xem `scripts/test_generate_index.py`.

- Cơ chế proxy (`"$proxy/?url=$domain"` rồi nối chuỗi thô, không encode) được giữ y hệt bản gốc
  vì proxy Deno đó được thiết kế riêng cho kiểu nối chuỗi này.

- **`parseDescription` chèn placeholder `"\n"` (2 ký tự, không phải newline thật) trước khi gọi
  `.text()`**: Jsoup's `.text()`/`.wholeText()` chuẩn hoá và loại bỏ mọi khoảng trắng/newline thật
  đã chèn tay, kể cả `\n` thật — đã xác nhận bằng harness Java độc lập (`Jsoup.parse("<p>A</p><p>B</p>").wholeText()`
  → `"AB"`, dính liền hoàn toàn, không chỉ mất định dạng). Cách duy nhất giữ được ranh giới dòng là
  chèn một chuỗi văn bản placeholder (`"\n"` dạng escape 2 ký tự) trước khi extract, rồi thay lại
  thành newline thật sau cùng. Đây là gotcha chung của Jsoup, không riêng gì API này — cân nhắc kỹ
  nếu định parse HTML nhiều đoạn `<p>`/`<div>` ở chỗ khác trong tương lai.

- **Deep link (mở thẳng link cuutruyen.net/hetcuutruyen.net trong Mihon) — chưa làm, có lý do
  kỹ thuật cụ thể**: API thật `ResolvableSource`/`UriType`
  (`eu.kanade.tachiyomi.source.online.ResolvableSource`, `@since extensions-lib 1.5`) tồn tại
  thật trong cả `mihonapp/mihon` lẫn `Suwayomi/Suwayomi-Server`, nhưng **thiếu** trong thư viện
  `com.github.mihonapp:tachiyomix` đã publish lên JitPack (khả năng là thiếu sót khi họ port,
  không phải bị cố tình bỏ). Không thể tự khai báo lại interface này cục bộ trong code của mình
  để "vá" — cơ chế nạp extension của Mihon dùng `ChildFirstPathClassLoader` (ưu tiên tìm class
  trong chính APK extension trước khi hỏi app cha), nên một bản khai báo cục bộ sẽ bị coi là
  class khác với class thật của Mihon dù cùng tên, khiến `filterIsInstance<ResolvableSource>()`
  phía app luôn trả về false — **âm thầm không hoạt động, không báo lỗi khi build hay khi cài**.
  Cách vá khả dĩ (source set Gradle riêng đánh dấu `compileOnly`, không đóng gói vào APK) cần build
  thật + thiết bị thật để xác nhận mới dám làm, để dành cho một lượt riêng.

## Bối cảnh lịch sử: vì sao lại là `tachiyomix` chứ không phải `extensions-lib`

Cuối 2025, CI báo lỗi `Could not find com.github.tachiyomiorg:extensions-lib:1.4` và
`com.github.inorichi.injekt:injekt-core:65b0440`. Truy nguyên gốc: cả 2 org GitHub gốc
(`tachiyomiorg`, và tác giả `inorichi`) đã ngừng hoạt động từ vụ Kakao Entertainment gửi thư cảnh
cáo pháp lý cho Tachiyomi (2024) — JitPack không còn build lại được các coordinate đó nữa. Dự án
đã migrate toàn diện sang `mihonapp/tachiyomix` (fork đang bảo trì, API `suspend` mới) và định dạng
repo `index.pb`/`NetworkExtensionStore` thay vì chỉ vá lại coordinate cũ, vì đối chiếu mã nguồn
thật xác nhận cả Mihon lẫn Suwayomi-Server đều đã hỗ trợ đầy đủ phiên bản mới.

## Cách cập nhật version (đọc trước khi bump dependency)

Các dependency `compileOnly` (`okhttp`, `kotlinx-serialization-json`, `kotlinx-coroutines-core`,
`jsoup`, `injekt`) chỉ là **stub biên dịch** — implementation thật nằm trong app host (Mihon/
Suwayomi) lúc chạy, extension không đóng gói chúng vào APK. Vì vậy đừng bump các thư viện này lên
"bản mới nhất" của riêng chúng; hãy mở lại README của
[`mihonapp/tachiyomix`](https://github.com/mihonapp/tachiyomix#-app-dependency-requirements),
copy chính xác bảng "App Dependency Requirements" ứng với `libVersion` bạn đang nhắm tới. Dùng API
mới hơn những gì app host thật sự bundle có thể biên dịch qua nhưng crash lúc chạy
(`NoSuchMethodError`) vì class thật trong app không có method đó.

Các thành phần build-tooling khác (AGP, Kotlin Gradle Plugin, Gradle wrapper, GitHub Actions,
`desugar_jdk_libs`) thì ngược lại — nên cập nhật độc lập theo nhịp phát hành riêng của chúng, không
phụ thuộc vào `tachiyomix`. Kiểm tra trang release chính thức của từng công cụ khi cần cập nhật;
CHANGELOG.md ghi lại các lần bump gần nhất kèm lý do.
