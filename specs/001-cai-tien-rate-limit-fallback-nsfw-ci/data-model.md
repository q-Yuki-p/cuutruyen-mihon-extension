# Data Model

Không thêm dữ liệu lưu trữ hay tùy chọn mới. Trường `is_nsfw` trong `CuuTruyenDto.kt` có sẵn từ repo gốc, giữ nguyên không xóa.

| Thực thể | Trường | Ghi chú |
|---|---|---|
| Cặp host dự phòng | `hostA` ↔ `hostB` (chuỗi host đối xứng) | Hằng số trong `CuuTruyenFallback.kt`: 2 cặp đối xứng hai chiều (`cuutruyen.net` ↔ `hetcuutruyen.net`, `storage-bravo.cuutruyen.net` ↔ `storage-charlie.cuutruyen.net`) |
| Tùy chọn có sẵn | `pref_domain`, `pref_proxy`, `pref_image_quality` | Không đổi; `pref_proxy` khác rỗng (proxy bật) ⇒ tắt fallback |

Quy tắc:
- Ánh xạ host hoàn toàn đối xứng hai chiều (bất kể người dùng chọn `cuutruyen.net` hay `hetcuutruyen.net` làm miền chính, hoặc URL ảnh xuất phát từ `storage-bravo.cuutruyen.net` hay `storage-charlie.cuutruyen.net`).
- Mỗi lượt thực thi request chỉ thử lại tối đa **1 lần** sang host đối ứng (theo dõi bằng cờ cục bộ gắn theo lượt thực thi trong `MirrorFallbackInterceptor`), không đệ quy và không thử lại vòng thứ hai về host ban đầu.
