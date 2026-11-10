---
description: "Danh sách công việc triển khai cải tiến extension Cứu Truyện"
---

# Tasks: Cải tiến extension Cứu Truyện (ổn định, tiết kiệm, dễ bảo trì)

**Input**: Tài liệu thiết kế từ `/specs/001-cai-tien-rate-limit-fallback-nsfw-ci/` (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`)
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `quickstart.md`
**Tests**: Sử dụng self-check Kotlin thuần (`main()` + `check()`) chạy bằng `kotlinc` qua một lệnh `scripts/run_kotlin_checks.sh` (không dùng JUnit, không thêm dependency).
**Scope Note**: Chỉ gồm 5 hạng mục (US1 fallback miền, US2 rate limit, US3 ảnh WebP q85, US4 CI PR + test Kotlin, US5 README). Hạng mục ẩn truyện NSFW đã bị loại khỏi phạm vi (trường `is_nsfw` trong `CuuTruyenDto.kt` có sẵn từ repo gốc nên giữ nguyên, không tạo task xóa). Các thay đổi nền trong working tree (nâng dependency tachiyomix 1.6.0, `CuuTruyenImageQuality.kt`, co tọa độ dải ảnh và tùy chọn chất lượng ảnh) đã có sẵn, không tạo task làm lại.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Có thể chạy song song (khác file, không phụ thuộc vào task chưa hoàn thành)
- **[Story]**: Nhãn User Story (`[US1]`..`[US5]`), chỉ dùng cho các phase User Story
- Cuối mỗi mô tả task chỉ định rõ ` — implementer: argon-low|argon-med|argon-high|sonnet-low|sonnet-med`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Xác nhận nền tảng mã nguồn hiện có trong working tree trước khi chỉnh sửa

- [X] T001 Kiểm tra nền tảng working tree hiện có bằng cách chạy thử self-check `scripts/CheckImageQuality.kt` với `app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyenImageQuality.kt` (qua `kotlinc`) và kiểm tra trạng thái `./gradlew :app:assembleDebug` cùng giữ nguyên mặc định tùy chọn cũ (FR-008, FR-011) — implementer: sonnet-low

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Sửa cấu hình Gradle chặn build debug khi không có keystore phát hành (chặn trực tiếp việc kiểm증 APK cục bộ và US4 CI PR)

**CRITICAL**: Phải hoàn tất trước khi kiểm chứng build APK của các User Story

- [X] T002 Sửa khối `signingConfigs` và `buildTypes.release` trong `app/build.gradle.kts` (plan D6, FR-009, SC-005) để chỉ bắt buộc khóa ký phát hành (`hasReleaseKeystore`) khi thực thi các task release (kiểm tra `gradle.startParameter.taskNames`), không ném `GradleException` ở pha cấu hình khi chạy `./gradlew :app:assembleDebug`, sau đó chạy `./gradlew :app:assembleDebug` xác nhận tạo được `app/build/outputs/apk/debug/app-debug.apk` không cần `keystore.properties` — implementer: argon-low

**Checkpoint**: `./gradlew :app:assembleDebug` chạy thành công không cần khóa ký phát hành; sẵn sàng triển khai các User Story.

---

## Phase 3: User Story 1 - Đọc ổn định khi một miền Cứu Truyện bị lỗi (Priority: P1) 🎯 MVP

**Goal**: Tự động chuyển sang miền đối ứng (`cuutruyen.net` ↔ `hetcuutruyen.net` cho API và `storage-bravo.cuutruyen.net` ↔ `storage-charlie.cuutruyen.net` cho máy chủ ảnh) tối đa 1 lần khi miền đang chọn gặp lỗi mạng hoặc HTTP 403/5xx, tương thích với fallback `wsrv.nl` (`ct_src`), tắt khi bật proxy, không gửi request thừa khi miền chính bình thường, và báo lỗi rõ ràng khi cả hai miền đều lỗi.

**Independent Test**: Chặn miền đang chọn rồi mở danh sách truyện và một chương; nội dung vẫn hiển thị.

### Tests for User Story 1 (Red)

- [X] T003 [P] [US1] Tạo self-check `scripts/CheckFallback.kt` (chạy bằng `kotlinc`, đóng vai trò check đỏ trước khi viết T004/T005) kiểm tra: (1) bảng ánh xạ đối xứng hai chiều `cuutruyen.net` ↔ `hetcuutruyen.net` và `storage-bravo.cuutruyen.net` ↔ `storage-charlie.cuutruyen.net`, (2) hàm đổi host giữ nguyên scheme/path/query, (3) quy tắc mỗi lượt thực thi request chỉ thử lại tối đa 1 lần (cờ theo lượt thực thi, không đệ quy/vòng lặp), (4) tắt fallback khi `pref_proxy` khác rỗng, (5) chỉ kích hoạt retry mirror khi `IOException` hoặc HTTP 403/5xx (không retry khi HTTP 200 hoặc 429), và (6) điều kiện fallback từ `wsrv.nl` về ảnh gốc `ct_src` kích hoạt cho cả `IOException` lẫn HTTP lỗi (`!isSuccessful`) đồng thời giữ nguyên các tham số `ct_w`, `ct_h`, `ct_drm` (FR-001, FR-003, FR-005, FR-008) — implementer: argon-med

### Implementation for User Story 1 (Green)

- [X] T004 [US1] Tạo `app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyenFallback.kt` cài đặt bảng cặp host đối xứng, các hàm thuần tính URL dự phòng / kiểm tra điều kiện retry mirror và fallback `wsrv.nl` (chạy pass `scripts/CheckFallback.kt` qua `kotlinc`), cùng lớp `MirrorFallbackInterceptor` thực hiện thử lại tối đa 1 lần theo cờ lượt thực thi sang host đối ứng khi gặp `IOException` hoặc HTTP 403/5xx (đóng response lỗi trước khi retry, tắt khi bật proxy, không retry khi HTTP 429, ném `IOException` thông báo rõ hai miền đã thử khi cả hai đều lỗi; FR-001, FR-002, FR-003, FR-005, FR-011, SC-001) — implementer: argon-high
- [X] T005 [US1] Cập nhật `app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyen.kt` và `app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyenImageInterceptor.kt` theo đúng thứ tự chuỗi interceptor `CuuTruyenImageInterceptor` (ngoài) → `MirrorFallbackInterceptor` (trong, giữ `super.client` mặc định là `cloudflareClient` của host app) và sửa `CuuTruyenImageInterceptor.kt` bọc lệnh gọi `wsrv.nl` bằng `try/catch (e: IOException)` để bắt cả `IOException` lẫn `!response.isSuccessful` khi fallback về ảnh gốc (`ct_src`), đảm bảo lượt `chain.proceed` thứ hai đi qua `MirrorFallbackInterceptor` (và pass check đỏ ở T003; FR-001, FR-002, SC-001) — implementer: argon-high

**Checkpoint**: User Story 1 hoàn tất; `scripts/CheckFallback.kt` in `OK` và `./gradlew :app:assembleDebug` pass.

---

## Phase 4: User Story 2 - Không bị chặn khi đọc nhanh hoặc cập nhật thư viện (Priority: P1)

**Goal**: Giới hạn tần suất gửi request tới máy chủ API và máy chủ ảnh bằng `rateLimit` có sẵn của `tachiyomix 1.6.0` ở mức 5 req/s để tránh bị chặn khi làm mới thư viện lớn nhưng vẫn cho phép burst trang đầu chương không trễ quá 1 giây.

**Independent Test**: Làm mới thư viện lớn và tải liền một chương dài; số request gửi đi mỗi đơn vị thời gian không vượt ngưỡng đặt ra.

### Implementation for User Story 2

- [X] T006 [US2] Cập nhật `override val client: OkHttpClient` trong `app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyen.kt` thêm `.rateLimit(5, 1, TimeUnit.SECONDS)` (từ `eu.kanade.tachiyomi.network.interceptor.rateLimit`) ở vị trí trong cùng theo đúng thứ tự `CuuTruyenImageInterceptor` (ngoài) → `MirrorFallbackInterceptor` (giữa) → `rateLimit` (trong cùng) để mọi request API, ảnh và mọi lượt thử lại (`wsrv.nl` → ảnh gốc, mirror host) đều bị giới hạn tần suất (FR-004, FR-005, FR-006, SC-002, SC-003) — implementer: argon-med

**Checkpoint**: User Story 1 và User Story 2 cùng hoạt động trên `CuuTruyen.kt`; `./gradlew :app:assembleDebug` pass.

---

## Phase 5: User Story 3 - Ảnh lưu trên máy không bị phình dung lượng (Priority: P2)

**Goal**: Xuất ảnh sau khi ghép dải bằng định dạng WebP lossy chất lượng 85 (`image/webp`) thay cho JPEG q95 để dung lượng lưu đạt ~58% ở chế độ Nguồn (400.898 B, ngưỡng ≤110%) và ~117% ở chế độ Tiết kiệm (89.294 B, ngưỡng ≤150%), tương thích từ `minSdk = 21` trở lên.

**Independent Test**: So sánh dung lượng ảnh trước và sau xử lý trên một chương mẫu.

### Tests for User Story 3 (Red)

- [X] T007 [P] [US3] Cập nhật `scripts/CheckImageQuality.kt` bổ sung các lệnh `check()` kiểm tra hằng số đầu ra ảnh `OUTPUT_IMAGE_QUALITY == 85` và `OUTPUT_IMAGE_MIME == "image/webp"` cùng kiểm tra ngưỡng tỉ lệ nén mẫu (58% ≤ 110% cho Nguồn 400.898 B; 117% ≤ 150% cho Tiết kiệm 89.294 B; FR-007, FR-008, SC-004) — implementer: argon-med

### Implementation for User Story 3 (Green)

- [X] T008 [US3] Khai báo hằng số `OUTPUT_IMAGE_QUALITY = 85` và `OUTPUT_IMAGE_MIME = "image/webp"` trong `app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyenImageQuality.kt` và chạy `kotlinc` với `scripts/CheckImageQuality.kt` để xác nhận in `OK` (FR-007, FR-011, SC-004) — implementer: argon-low
- [X] T009 [US3] Cập nhật `app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyenImageInterceptor.kt` thay `Bitmap.CompressFormat.JPEG` (q95, `image/jpeg`) bằng `Bitmap.CompressFormat.WEBP_LOSSY` khi `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` (API 30+) và `@Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP` khi API < 30 (`minSdk = 21`) với chất lượng `OUTPUT_IMAGE_QUALITY` (85) và media type `OUTPUT_IMAGE_MIME` (`image/webp`) (FR-007, FR-011, SC-004) — implementer: argon-med

**Checkpoint**: User Story 3 hoàn tất; `scripts/CheckImageQuality.kt` in `OK` và `./gradlew :app:assembleDebug` pass.

---

## Phase 6: User Story 4 - Người đóng góp kiểm tra thay đổi tự động (Priority: P3)

**Goal**: Gom toàn bộ self-check Kotlin vào một lệnh duy nhất `scripts/run_kotlin_checks.sh` và thiết lập GitHub Actions workflow `.github/workflows/pr.yml` tự động chạy kiểm tra + build debug APK cho mọi pull request mà không cần khóa ký phát hành.

**Independent Test**: Mở pull request có lỗi cố ý; kiểm tra tự động báo không đạt.

### Implementation for User Story 4

- [X] T010 [P] [US4] Tạo `scripts/run_kotlin_checks.sh` (bật `chmod +x` và `set -euo pipefail`) tự động biên dịch bằng `kotlinc` và chạy cả hai bài kiểm tra `scripts/CheckImageQuality.kt` (kèm `CuuTruyenImageQuality.kt`) và `scripts/CheckFallback.kt` (kèm `CuuTruyenFallback.kt`) bằng một lệnh duy nhất (FR-008, SC-005) — implementer: sonnet-low
- [X] T011 [P] [US4] Tạo `.github/workflows/pr.yml` chạy trên sự kiện `pull_request` với `permissions: contents: read`, cài JDK 21 (Temurin) và Android SDK, thực thi `./scripts/run_kotlin_checks.sh`, `python3 scripts/test_generate_index.py` và `./gradlew :app:assembleDebug --stacktrace` hoàn toàn không sử dụng `secrets.KEYSTORE_*` (FR-008, FR-009, SC-005) — implementer: argon-low

**Checkpoint**: `./scripts/run_kotlin_checks.sh` chạy một lệnh ra `OK` cho mọi bài test và `.github/workflows/pr.yml` hợp lệ.

---

## Phase 7: User Story 5 - Tài liệu mô tả đúng tính năng hiện có (Priority: P3)

**Goal**: Cập nhật `README.md` giúp người đọc và người đóng góp nắm rõ 3 chế độ chất lượng ảnh kèm mức tiết kiệm dung lượng, cơ chế fallback miền, giới hạn tần suất và lệnh chạy kiểm tra.

**Independent Test**: Người chưa biết dự án đọc README và tìm đúng nơi bật chế độ tiết kiệm data.

### Implementation for User Story 5

- [X] T012 [P] [US5] Cập nhật `README.md` bổ sung mục tính năng và hướng dẫn phát triển: (1) bảng 3 mức chất lượng ảnh (`Nguồn` gốc ~400 KB, `Cân bằng` ~134 KB giảm ~66% data, `Tiết kiệm` ~89 KB giảm ~78% data qua `wsrv.nl`, đầu ra ghép ảnh nén WebP q85 giảm còn ~58% so với ảnh gốc), (2) cơ chế tự động fallback miền đối xứng `cuutruyen.net` ↔ `hetcuutruyen.net` và `storage-bravo.cuutruyen.net` ↔ `storage-charlie.cuutruyen.net` (tắt khi bật proxy, dựa vào bộ vượt Cloudflare của ứng dụng), (3) rate limit 5 req/s, và (4) các lệnh chạy kiểm tra (`./scripts/run_kotlin_checks.sh`, `python3 scripts/test_generate_index.py`, `./gradlew :app:assembleDebug`) (FR-010, SC-006) — implementer: argon-low

**Checkpoint**: `README.md` đầy đủ thông tin, tìm thấy ngay từ khóa "tiết kiệm data" và lệnh chạy kiểm tra.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Kiểm chứng tổng thể toàn nhánh theo `quickstart.md`

- [X] T013 Chạy kiểm chứng toàn diện theo `quickstart.md` gồm `./scripts/run_kotlin_checks.sh`, `python3 scripts/test_generate_index.py` và `./gradlew :app:assembleDebug` để xác nhận mọi bài kiểm tra tự động và gói APK debug đều đạt (FR-008, FR-009, SC-005) — implementer: sonnet-low

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup - T001)**: Chạy ngay lập tức.
- **Phase 2 (Foundational - T002)**: Phụ thuộc T001; chặn việc build `./gradlew :app:assembleDebug` không cần keystore của tất cả các phase sau.
- **Phase 3 (US1 - T003..T005)**: Bắt đầu sau T002. Trong nội bộ US1: `T003 (Red) -> T004 (Green) -> T005`.
- **Phase 4 (US2 - T006)**: Chạm cùng file `CuuTruyen.kt` với T005 của US1, nên thực thi tuần tự `T005 -> T006` (không chạy song song với T005).
- **Phase 5 (US3 - T007..T009)**:
  - `T007 (Red)` và `T008 (Green)` chỉ chạm `scripts/CheckImageQuality.kt` và `CuuTruyenImageQuality.kt`, hoàn toàn độc lập với US1/US2.
  - `T009` chạm cùng file `CuuTruyenImageInterceptor.kt` với `T005` (US1), nên phải chạy sau khi cả `T008` và `T005` đã xong.
- **Phase 6 (US4 - T010..T011)**: Viết file `scripts/run_kotlin_checks.sh` (T010) và `.github/workflows/pr.yml` (T011) độc lập về mặt file, nhưng để chạy pass `./scripts/run_kotlin_checks.sh` cần hoàn tất T004 (US1) và T008 (US3).
- **Phase 7 (US5 - T012)**: Chỉ sửa `README.md`, hoàn toàn độc lập, có thể chạy song song bất kỳ lúc nào sau Phase 2.
- **Phase 8 (Polish - T013)**: Phụ thuộc tất cả các task T001..T012.

### File Conflict & True Dependency Graph

```text
T001 (Setup)
 └──► T002 (Foundational: app/build.gradle.kts)
       ├──► T003 [P] (scripts/CheckFallback.kt)
       │     └──► T004 (CuuTruyenFallback.kt)
       │           └──► T005 (CuuTruyen.kt + CuuTruyenImageInterceptor.kt)
       │                 ├──► T006 (CuuTruyen.kt)
       │                 └──► T009 (CuuTruyenImageInterceptor.kt) ◄── T008 (CuuTruyenImageQuality.kt) ◄── T007 [P] (scripts/CheckImageQuality.kt)
       ├──► T010 [P] (scripts/run_kotlin_checks.sh) [chạy kiểm chứng sau T004 + T008]
       ├──► T011 [P] (.github/workflows/pr.yml)
       └──► T012 [P] (README.md)
             └──► T013 (Polish: chạy toàn bộ quickstart checks)
```

---

## Parallel Examples

### Nhóm 1: Khởi tạo bài kiểm tra độc lập (Red) và tài liệu / CI sau khi xong T002

```bash
# Chạy song song vì chạm các file hoàn toàn khác nhau:
Task T003: "Tạo self-check scripts/CheckFallback.kt"
Task T007: "Cập nhật self-check scripts/CheckImageQuality.kt"
Task T011: "Tạo workflow .github/workflows/pr.yml"
Task T012: "Cập nhật README.md"
```

### Nhóm 2: Triển khai song song phần thuần của US1 và US3

```bash
# T004 (CuuTruyenFallback.kt) và T008 (CuuTruyenImageQuality.kt) khác file, chạy song song được:
Task T004: "Tạo CuuTruyenFallback.kt"
Task T008: "Khai báo hằng số OUTPUT_IMAGE_QUALITY và OUTPUT_IMAGE_MIME trong CuuTruyenImageQuality.kt"
Task T010: "Tạo scripts/run_kotlin_checks.sh"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2 - P1)

1. Hoàn thành T001 (Setup) và T002 (Foundational) để mở khóa `./gradlew :app:assembleDebug` không cần keystore.
2. Hoàn thành US1 (T003 → T004 → T005): có ngay cơ chế tự động fallback miền và máy chủ ảnh.
3. Hoàn thành US2 (T006): gắn `rateLimit(5, 1, TimeUnit.SECONDS)` vào `OkHttpClient`.
4. Kiểm chứng độc lập US1 + US2 bằng `kotlinc` và `./gradlew :app:assembleDebug`.

### Incremental Delivery

1. **Nền tảng**: T001 → T002 (mở khóa build debug).
2. **Độ ổn định mạng (P1)**: T003 → T004 → T005 (US1 Fallback) → T006 (US2 Rate Limit).
3. **Tối ưu dung lượng lưu (P2)**: T007 → T008 → T009 (US3 WebP q85).
4. **Tự động hóa & Tài liệu (P3)**: T010 + T011 (US4 CI & Test runner) + T012 (US5 README) → T013 (Polish).

---

## Requirement & Success Criteria Traceability

| Mã yêu cầu / tiêu chí | Mô tả ngắn | Task phủ |
|---|---|---|
| **FR-001** | Tự chuyển sang miền còn lại cho danh sách, chi tiết, chương và máy chủ ảnh | T003, T004, T005 |
| **FR-002** | Hiển thị thông báo lỗi rõ ràng khi cả hai miền đều không truy cập được | T004, T005 |
| **FR-003** | Không gửi request sang miền dự phòng khi miền đang chọn phản hồi bình thường | T003, T004 |
| **FR-004** | Giới hạn tần suất request tới máy chủ truyện và máy chủ ảnh | T006 |
| **FR-005** | Giãn request khi máy chủ yêu cầu giảm tần suất / không fallback lặp lại khi 429 | T003, T004, T006 |
| **FR-006** | Giới hạn tần suất không làm chậm đáng kể trang đầu tiên của một chương | T006 |
| **FR-007** | Ảnh sau ghép có dung lượng lưu ≤110% (Nguồn) hoặc ≤150% (tiết kiệm), dùng WebP q85 | T007, T008, T009 |
| **FR-008** | Bộ kiểm tra tự động cho các logic thuần chạy được bằng một lệnh | T001, T003, T007, T010, T013 |
| **FR-009** | Build debug không cần khóa ký phát hành; CI PR build debug + chạy test không cần khóa ký | T002, T011, T013 |
| **FR-010** | README mô tả 3 mức chất lượng ảnh, mức giảm dung lượng, fallback miền và cách chạy kiểm tra | T012 |
| **FR-011** | Mặc định của mọi tùy chọn giữ hành vi hiện tại của người dùng cũ | T001, T004, T008, T009 |
| **SC-001** | 95% lượt mở danh sách và chương thành công qua miền còn lại trong < 15 giây | T004, T005 |
| **SC-002** | Làm mới thư viện 100 truyện hoàn tất không bị máy chủ chặn | T006 |
| **SC-003** | Thời gian hiển thị trang đầu tiên của chương không tăng quá 1 giây | T006 |
| **SC-004** | Dung lượng lưu chương ≤110% ảnh gốc (Nguồn) hoặc ≤150% ảnh nhận qua mạng (tiết kiệm) | T007, T008, T009 |
| **SC-005** | 100% pull request được tự động build và kiểm tra trong vòng 15 phút | T002, T010, T011, T013 |
| **SC-006** | Người đọc README tìm được cách bật tiết kiệm data trong dưới 1 phút | T012 |
