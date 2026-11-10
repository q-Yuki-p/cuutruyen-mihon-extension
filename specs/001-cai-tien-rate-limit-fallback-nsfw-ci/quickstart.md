# Quickstart (kiểm chứng)

1. **Self-check**: `scripts/run_kotlin_checks.sh` → in `OK` cho từng file check (ghép dải, URL proxy, bảng fallback).
2. **Build debug không keystore**: `./gradlew :app:assembleDebug` → thành công, có `app/build/outputs/apk/debug/app-debug.apk`.
3. **Dung lượng ảnh**: cài APK, đọc 1 chương ở mỗi mức chất lượng, so dung lượng lưu với ảnh nhận (≤110% / ≤150%), xem mắt 2–3 trang.
4. **Fallback**: chặn `cuutruyen.net` (hosts/airplane+VPN) → danh sách vẫn tải; chặn cả hai → thông báo lỗi.
5. **Rate limit**: làm mới thư viện ≥50 truyện → không lỗi 429/403 hàng loạt.
6. **CI**: mở PR thử → workflow `pr.yml` chạy xanh, không dùng secret.
