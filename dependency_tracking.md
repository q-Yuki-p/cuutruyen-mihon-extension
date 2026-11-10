# Bảng theo dõi phiên bản Dependencies & Build Tooling

*Cập nhật lần cuối: 09/11/2026 (đối chiếu trực tiếp Maven Central, Google Maven, GitHub Releases)*

Tài liệu tổng hợp và đối soát phiên bản các công cụ build, thư viện và SDK trong dự án Android theo hai nhóm quy tắc quản lý phiên bản.

---

## 1. Build tooling & Android SDK (Nhóm luôn cập nhật mới nhất)

Nhóm này ưu tiên sử dụng các bản phát hành ổn định (GA/Stable) mới nhất nhằm tận dụng tối đa cải tiến hiệu năng compiler, sửa lỗi bảo mật và tuân thủ chính sách mới của Google Play.

| Tên thành phần | Phiên bản đang dùng | Phiên bản mới nhất | Trạng thái & Khuyến nghị | Nguồn tra cứu chính thức |
| :--- | :--- | :--- | :--- | :--- |
| **Android Gradle Plugin** | `9.5.1` | `9.5.1` | **Đã cập nhật** (9.6.0 mới chỉ ở alpha, không dùng) | [AGP Release Notes](https://developer.android.com/build/releases) |
| **Kotlin Gradle Plugin** | `2.4.21` | `2.4.21` | **Đã cập nhật** (2.5.0 mới chỉ ở Beta, không dùng) | [Kotlin Releases](https://kotlinlang.org/docs/releases.html) |
| **Gradle wrapper** | `9.9.0` | `9.9.0` | **Đã cập nhật** | [Gradle Releases](https://gradle.org/releases/) |
| **desugar_jdk_libs** | `2.1.5` | `2.1.5` | **Đã cập nhật** | [Maven Central Repository](https://mvnrepository.com/artifact/com.android.tools/desugar_jdk_libs) |
| **actions/checkout** | `v7` | `v7` (v7.0.1) | Mới nhất | [GitHub Actions Checkout](https://github.com/actions/checkout) |
| **actions/setup-java** | `v6` | `v6` (v6.2.0) | **Đã cập nhật** (bản cũ ghi v5 là sai) | [GitHub Actions Setup Java](https://github.com/actions/setup-java) |
| **android-actions/setup-android** | `v4` | `v4` (v4.0.4) | Mới nhất | [GitHub Setup Android](https://github.com/android-actions/setup-android) |
| **compileSdk / minSdk / targetSdk** | `36 / 26 / 36` | `36 / 26 / 36` | **Đã cập nhật** (Android 16, minSdk 26 chuẩn Mihon/Keiyoushi, tắt desugar interface default methods để tương thích Suwayomi-Server) | [Android SDK Setup](https://developer.android.com/about/versions/16/setup-sdk) |
| **JDK / Temurin** | `21` (CI) | `21` | **Đã cập nhật CI**. Bytecode target vẫn JVM 17 để chạy được trên host app | [Eclipse Temurin](https://github.com/actions/setup-java) |

---

## 2. tachiyomix & dependencies phụ thuộc (Nhóm phụ thuộc)

Nhóm này chỉ cập nhật khi `tachiyomix` ra phiên bản mới hơn phiên bản 1.6 và yêu cầu nâng cấp các thư viện đi kèm. Tránh tự ý nâng cấp đơn lẻ các dependency dưới đây để đảm bảo tính tương thích của extension stub interface.

| Tên thành phần | Phiên bản đang dùng | Phiên bản mới (theo tachiyomix) | Trạng thái & Khuyến nghị | Nguồn tra cứu chính thức |
| :--- | :--- | :--- | :--- | :--- |
| **com.github.mihonapp:tachiyomix** | `1.6` | `1.6` | Chưa có bản mới hơn; tiếp tục giữ nguyên bản 1.6 | [mihonapp/tachiyomix Releases](https://github.com/mihonapp/tachiyomix) |
| **OkHttp** | `5.5.0` | `5.5.0` | **Đã cập nhật** theo README tachiyomix 1.6.0 (5.5.1 có sẵn nhưng host chưa yêu cầu) | [Square OkHttp](https://square.github.io/okhttp/) |
| **kotlinx-serialization-json** | `1.11.0` | `1.11.0` | **Đã cập nhật** theo README tachiyomix 1.6.0 | [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) |
| **kotlinx-coroutines-core** | `1.11.0` | `1.11.0` | **Đã cập nhật** theo README tachiyomix 1.6.0 | [Kotlinx Coroutines](https://github.com/Kotlin/kotlinx.coroutines) |
| **jsoup** | `1.23.1` | `1.23.1` | **Đã cập nhật** theo README tachiyomix 1.6.0 (1.24.1 có sẵn nhưng host chưa yêu cầu) | [jsoup Java HTML Parser](https://jsoup.org/download) |
| **com.github.mihonapp:injekt** | `91edab2317` | `91edab2317` | Giữ nguyên commit hash tương thích | [mihonapp/injekt Repository](https://github.com/mihonapp/injekt) |

---

## 3. Ghi chú kỹ thuật chi tiết

### Android Gradle Plugin 9.5.x
- **Tương thích Gradle**: Yêu cầu Gradle tối thiểu `9.6.0` (tương thích với Gradle wrapper `9.9.0`).
- **Tính năng mới**: Bổ sung `testOptions.backupTests` phục vụ kiểm thử sao lưu và phục hồi dữ liệu tự động.
- **Sửa lỗi cảnh báo**: Xóa bỏ hoàn toàn các cảnh báo deprecation `Configuration.setVisible()` khi chạy trên Gradle 9.8.
- **L8 Desugaring**: Khắc phục lỗi mapping obfuscation cho `desugar_jdk_libs`.

### Gradle wrapper 9.9.0
- **Cải tiến**: Tăng tốc độ thực thi build trên môi trường Windows ảo hóa lên tới 45% thông qua cơ chế phát hiện đồng hồ hệ thống.
- **Sửa lỗi**: Khắc phục lỗi phân giải dependency và nhận diện Java Toolchain trên các hệ điều hành Ubuntu/Debian của bản 9.8.0.

### Kotlin Gradle Plugin 2.4.21
- **Hiệu năng**: Nâng cao độ ổn định và tốc độ biên dịch của K2 compiler.
- **Khả năng tương thích**: Tương thích tốt với JDK 17, JDK 21 và Gradle 9.8.x.
