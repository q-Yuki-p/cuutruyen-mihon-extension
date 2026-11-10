# Implementation Plan: Cải tiến extension Cứu Truyện

**Branch**: `001-cai-tien-rate-limit-fallback-nsfw-ci` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

## Summary

5 hạng mục (NSFW đã bị loại): (1) đầu ra ảnh dùng WebP q85 thay JPEG q95, (2) rate limit bằng `rateLimit` có sẵn của tachiyomix, (3) interceptor fallback miền API và máy chủ ảnh, (4) test Kotlin chạy bằng `kotlinc` + workflow CI cho PR, (5) cập nhật README. Không thêm tùy chọn người dùng, không thêm dependency.

## Technical Context

**Language/Version**: Kotlin 2.4.21, bytecode JVM 17, JDK 21 (CI)
**Primary Dependencies**: tachiyomix 1.6.0 (`rateLimit`, cloudflareClient), OkHttp 5.5.0 — đều `compileOnly`, không thêm mới
**Storage**: SharedPreferences hiện có, không đổi
**Testing**: self-check `main()` + `check()` chạy bằng `kotlinc` (đã dùng cho `CheckImageQuality.kt`); Python test sẵn có của generator
**Target Platform**: Android (Mihon/Suwayomi), minSdk 21
**Project Type**: Android extension (1 module `:app`)
**Performance Goals**: trang đầu chương không trễ quá 1s (SC-003)
**Constraints**: lưu ảnh ≤110% ảnh gốc (Nguồn) / ≤150% ảnh nhận qua mạng (tiết kiệm)
**Scale/Scope**: ~5 file Kotlin, 1 workflow, 1 README

## Constitution Check

`.specify/memory/constitution.md` còn là template chưa điền → không có cổng nào để vi phạm. Nguyên tắc dự án tự áp dụng: không thêm dependency, mặc định giữ hành vi cũ (FR-011). Gate: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/001-cai-tien-rate-limit-fallback-nsfw-ci/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md   # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/
├── CuuTruyen.kt                  # sửa: gắn ImageInterceptor -> MirrorFallbackInterceptor -> rateLimit vào client
├── CuuTruyenImageInterceptor.kt  # sửa: bắt cả IOException khi gọi wsrv.nl + xuất WebP q85
├── CuuTruyenFallback.kt          # mới: MirrorFallbackInterceptor + bảng cặp host
└── CuuTruyenImageQuality.kt      # sửa nhỏ: hằng số output
scripts/
├── CheckImageQuality.kt          # có sẵn
├── CheckFallback.kt              # mới
└── run_kotlin_checks.sh          # mới: 1 lệnh chạy mọi check
.github/workflows/
├── build.yml                     # giữ nguyên (publish)
└── pr.yml                        # mới: PR → test + assembleDebug
README.md                         # sửa
```

**Structure Decision**: giữ 1 module, thêm đúng 1 file nguồn mới. Check chạy bằng `kotlinc` vì các hàm được test là Kotlin thuần, không phụ thuộc Android.

## Design Decisions

| # | Quyết định | Chi tiết |
|---|---|---|
| D1 | Output WebP q85 | `Bitmap.CompressFormat.WEBP_LOSSY` (API 30+), `WEBP` (API <30). Đo: Nguồn 58%, tiết kiệm 117% so với ảnh nhận. |
| D2 | Rate limit | `client.newBuilder()...rateLimit(5, 1, SECONDS)` đặt trong cùng của chuỗi interceptor — bucket cho phép burst nên trang đầu không bị trễ, mọi lượt gọi đầu và thử lại đều đi qua rate limit. |
| D3 | Fallback | Thứ tự thêm vào client: `CuuTruyenImageInterceptor` (ngoài cùng) → `MirrorFallbackInterceptor` (giữa) → `rateLimit` (trong cùng). `CuuTruyenImageInterceptor` bắt cả `IOException` lẫn HTTP lỗi khi gọi `wsrv.nl` rồi mới fallback về ảnh gốc (`ct_src`) để lượt `chain.proceed` thứ hai đi qua `MirrorFallbackInterceptor` và `rateLimit`. Khi gặp `IOException` hoặc HTTP 403/5xx (không retry 429) từ host thuộc bảng cặp → thử lại tối đa 1 lần với host đối ứng. Bảng đối xứng: `cuutruyen.net`↔`hetcuutruyen.net`, `storage-bravo.cuutruyen.net`↔`storage-charlie.cuutruyen.net`. Tắt khi bật proxy. |
| D4 | Cloudflare | `HttpSource.client` mặc định là cloudflareClient của host → mirror bị Cloudflare chặn vẫn được thử qua bộ vượt; phải kiểm thử thủ công trên máy thật. |
| D5 | CI PR | `pull_request`, không sinh keystore tạm và không dùng secret, `permissions: contents: read`, chạy check Kotlin/Python và build `assembleDebug`. |
| D6 | Build debug cần keystore | `build.gradle.kts` hiện ném lỗi khi thiếu keystore ngay cả với debug → chỉ bắt buộc khi task release. |

## Complexity Tracking

Không có vi phạm cần biện minh.
