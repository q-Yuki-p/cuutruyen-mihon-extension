# Extension Cứu Truyện for Mihon/Suwayomi

### Please give the repo a :star:

| Build |
|-------|
| [![CI](https://github.com/q-Yuki-p/cuutruyen-mihon-extension/actions/workflows/build.yml/badge.svg)](https://github.com/q-Yuki-p/cuutruyen-mihon-extension/actions/workflows/build.yml) |

## Usage
**If you are new to repository/extensions, please read the [Keiyoushi Getting Started guide](https://keiyoushi.github.io/docs/guides/getting-started#adding-the-extension-repo) first.**

* Copy & paste the following URL: https://raw.githubusercontent.com/q-Yuki-p/cuutruyen-mihon-extension/repo/index.pb

## Tùy chọn Chất lượng ảnh & Tiết kiệm data

Trong phần **Cài đặt extension** của Mihon/Suwayomi (**Browse → Extensions → Cứu Truyện → Cài đặt → Chất lượng ảnh trang**), bạn có thể chọn một trong 3 mức chất lượng (đo thực tế trên trang chuẩn `1024×1450`, ảnh gốc ~400 KB):

| Mức chất lượng | Dung lượng tải/trang | Mức tiết kiệm data | Cơ chế xử lý |
|---|---|---|---|
| `Nguồn (gốc, tốn data nhất)` *(mặc định)* | ~401 KB | 0% (bản gốc) | Tải ảnh gốc trực tiếp từ máy chủ ảnh Cứu Truyện. |
| `Cân bằng (~-66% data)` | ~134 KB | ~-66% data | Thu nhỏ tối đa rộng `900px`, nén WebP `q70` qua proxy `wsrv.nl`. |
| `Tiết kiệm (~-78% data)` | ~89 KB | ~-78% data | Thu nhỏ tối đa rộng `720px`, nén WebP `q60` qua proxy `wsrv.nl`, phù hợp khi dùng dữ liệu di động 3G/4G/5G hoặc máy ít dung lượng. |

- Ở hai chế độ tiết kiệm data, ảnh bìa truyện tự động dùng bản nhẹ chính thức (`cover_mobile_url`). Nếu proxy `wsrv.nl` gặp sự cố hoặc quá tải, extension tự động tải lại ảnh gốc (kèm giải mã DRM đầy đủ) để trang truyện không bị lỗi.
- Ảnh sau khi ghép dải DRM được xuất ra định dạng **WebP chất lượng 85** (thay vì JPEG q95 trước đây), giúp dung lượng lưu trên máy ở chế độ Nguồn giảm còn ~58% (~232 KB so với ~401 KB ảnh gốc) mà không làm phình file.

## Tự động chuyển miền dự phòng (Mirror Fallback) & Giới hạn tần suất (Rate Limit)

- **Mirror Fallback**: Tự động thử lại tối đa 1 lần sang miền đối ứng khi gặp lỗi kết nối hoặc HTTP `403`/`5xx`:
  - **API**: `cuutruyen.net` ↔ `hetcuutruyen.net` *(lưu ý: `hetcuutruyen.net` có lớp bảo vệ Cloudflare nên khả năng tự vượt phụ thuộc vào bộ vượt Cloudflare tích hợp của ứng dụng Mihon/Suwayomi; tự động tắt fallback miền khi người dùng bật `Shin Proxy`)*.
  - **Máy chủ ảnh (CDN)**: `storage-bravo.cuutruyen.net` ↔ `storage-charlie.cuutruyen.net`.
- **Rate Limit**: Giới hạn tần suất `5 req/s` (`rateLimit(5, 1, TimeUnit.SECONDS)`) trên `OkHttpClient` để tránh bị máy chủ chặn khi làm mới thư viện lớn hoặc lật trang nhanh.

## Kiểm tra & Phát triển cục bộ (Testing & Local Build)

- Chạy bộ self-check Kotlin (ghép dải ảnh, URL proxy, fallback miền):
  ```bash
  ./scripts/run_kotlin_checks.sh
  ```
- Chạy kiểm tra script sinh index repo:
  ```bash
  python3 scripts/test_generate_index.py
  ```
- Build gói APK debug (không cần tạo `keystore.properties`):
  ```bash
  ./gradlew :app:assembleDebug
  ```

## Requests

To request a new source or bug fix, [create an issue](https://github.com/q-Yuki-p/cuutruyen-mihon-extension/issues/new/choose).

Please note that creating an issue does not mean that the source will be added or fixed in a timely
fashion, because the work is volunteer-based. Some sources may also be impossible to do or prohibitively
difficult to maintain.

If you would like to see a request fulfilled and have the necessary skills to do so, consider contributing!
Issues are up-for-grabs for any developer if there is no assigned user already.

## Contributing

Contributions are welcome!

Check out the repo's [issue backlog](https://github.com/q-Yuki-p/cuutruyen-mihon-extension/issues) for source requests and bug reports. 

See [CONTRIBUTING.md](CONTRIBUTING.md) for the technical notes behind this extension's design decisions.

## License

    Copyright 2026 the Cứu Truyện Mihon extension contributors.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

## Disclaimer

This project does not have any affiliation with the content providers available.

This project is not affiliated with Mihon/Tachiyomi. Don't ask for help about these extensions at the
official support means of Mihon/Tachiyomi. All credits to the codebase goes to the original contributors.
