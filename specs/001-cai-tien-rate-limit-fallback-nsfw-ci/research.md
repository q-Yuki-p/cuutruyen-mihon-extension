# Research

## R1. Định dạng đầu ra ảnh
- **Decision**: WebP lossy q85 cho mọi chế độ.
- **Rationale**: đo trên 1 trang thật (ảnh nhận 400.898 B): JPEG q95=131%, JPEG q85=89%, WebP q85=58%. Chế độ tiết kiệm (ảnh nhận 89.294 B): JPEG q95=327%, JPEG q85=214%, WebP q85=117% → chỉ WebP đạt ngưỡng 150%.
- **Alternatives**: JPEG q85 (vẫn 214% ở chế độ tiết kiệm, loại); giữ nguyên byte khi không ghép (không áp dụng: mọi trang đều bị xáo).
- **Risk**: nén lại ảnh đã lossy gây mất nhẹ chất lượng; chấp nhận ở q85, cần xem mắt 2–3 trang.

## R2. Rate limit
- **Decision**: dùng `rateLimit(permits, period, unit)` có sẵn trong tachiyomix 1.6.0 (đã xác nhận bằng `javap`).
- **Rationale**: không viết lại; đúng chuẩn các extension khác.
- **Đo thực tế**: 8 request liên tiếp tới API đều 200 → server chưa chặn ở tải nhẹ; chưa có số ngưỡng công khai. Chọn bảo thủ 5 req/s toàn client, chỉnh sau nếu người dùng báo chậm.
- **Alternatives**: `rateLimitHost` riêng từng host (phức tạp hơn, không cần).

## R3. Fallback miền
- **Đo thực tế**: `cuutruyen.net` API 200; `hetcuutruyen.net` API trả 403 Cloudflare "Just a moment" với curl (trang chủ 200). `storage-bravo.cuutruyen.net` và `storage-charlie.cuutruyen.net` cùng trả 200 cùng dung lượng ảnh; `storage-ct.lrclib.net` không truy cập được (nên `rewriteStorageUrl` hiện có là cần thiết).
- **Decision**: fallback hai cặp host; mirror API dựa vào bộ vượt Cloudflare của host app (chủ dự án đã chọn).
- **Risk**: nếu bộ vượt không qua được thì fallback API vô hiệu; sẽ báo lỗi rõ thay vì lặp. Cần thử trên Mihon thật.

## R4. Test Kotlin
- **Decision**: giữ mô hình `kotlinc` + `check()` đã có; thêm `run_kotlin_checks.sh`. Không thêm JUnit/Gradle test để khỏi kéo dependency và cấu hình AGP unit test.

## R5. NSFW (đã loại)
- Danh sách `recently_updated/top/search` không có `is_nsfw`; chỉ trang chi tiết có. Lọc chính xác cần 24–30 request/trang → trái mục tiêu rate limit. Chủ dự án chọn bỏ.
