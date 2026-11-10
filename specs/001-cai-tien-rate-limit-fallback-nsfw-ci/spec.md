# Feature Specification: Cải tiến extension Cứu Truyện (ổn định, tiết kiệm, dễ bảo trì)

**Feature Branch**: `001-cai-tien-rate-limit-fallback-nsfw-ci`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "Cải tiến extension Cứu Truyện: (1) hạ chất lượng nén lại ảnh JPEG q95 ở chế độ Nguồn vì có thể làm file lớn hơn ảnh gốc, (2) thêm rate limit cho request API và ảnh để tránh bị chặn, (3) tự fallback sang miền cuutruyen.net <-> hetcuutruyen.net khi một miền lỗi, (4) thêm Kotlin unit/self-check và CI chạy khi có pull request (build debug + test), (5) thêm tùy chọn ẩn truyện NSFW dựa trên trường is_nsfw của API, (6) cập nhật README mô tả chế độ chất lượng ảnh và các tính năng mới."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Đọc ổn định khi một miền Cứu Truyện bị lỗi (Priority: P1)

Người đọc mở truyện bình thường. Khi miền đang chọn không truy cập được, ứng dụng tự chuyển sang miền còn lại để vẫn tải được danh sách, chi tiết và ảnh mà người đọc không phải vào cài đặt đổi tay.

**Why this priority**: Miền chết là nguyên nhân khiến toàn bộ nguồn không dùng được; sửa được điểm này có giá trị lớn nhất.

**Independent Test**: Chặn miền đang chọn rồi mở danh sách truyện và một chương; nội dung vẫn hiển thị.

**Acceptance Scenarios**:

1. **Given** miền đang chọn không phản hồi, **When** người đọc mở danh sách truyện, **Then** nội dung vẫn tải được từ miền còn lại.
2. **Given** cả hai miền đều lỗi, **When** người đọc mở danh sách, **Then** thấy thông báo lỗi rõ ràng, không bị treo vô hạn.
3. **Given** miền đang chọn hoạt động bình thường, **When** người đọc dùng ứng dụng, **Then** không có request thừa sang miền còn lại.

---

### User Story 2 - Không bị chặn khi đọc nhanh hoặc cập nhật thư viện (Priority: P1)

Người đọc lật trang liên tục hoặc làm mới cả thư viện. Extension giới hạn tần suất gửi request tới máy chủ để tránh bị chặn tạm thời, đồng thời không làm trải nghiệm đọc bình thường chậm đi rõ rệt.

**Why this priority**: Bị chặn khiến mọi thao tác thất bại, ảnh hưởng cả đọc lẫn cập nhật thư viện.

**Independent Test**: Làm mới thư viện lớn và tải liền một chương dài; số request gửi đi mỗi đơn vị thời gian không vượt ngưỡng đặt ra.

**Acceptance Scenarios**:

1. **Given** thư viện có nhiều truyện, **When** làm mới hàng loạt, **Then** tần suất request không vượt giới hạn và tất cả đều hoàn tất.
2. **Given** người đọc mở một chương thông thường, **When** các trang tải, **Then** trang đầu hiển thị không bị trì hoãn đáng kể.

---

### User Story 3 - Ảnh lưu trên máy không bị phình dung lượng (Priority: P2)

Ảnh sau khi ghép lại (mọi chế độ chất lượng) có dung lượng lưu trên máy không lớn hơn đáng kể so với ảnh nhận về, nhưng vẫn đủ sắc nét như trước.

**Why this priority**: Hiện ảnh bị nén lại ở chất lượng rất cao (đo thực tế: 527 KB so với 401 KB ảnh gốc) và ảnh đã tiết kiệm cũng bị nén lại thành định dạng nặng hơn, tốn bộ nhớ và dung lượng lưu offline.

**Independent Test**: So sánh dung lượng ảnh trước và sau xử lý trên một chương mẫu.

**Acceptance Scenarios**:

1. **Given** chương mẫu ở chế độ Nguồn, **When** tải về, **Then** dung lượng lưu không vượt quá 110% ảnh gốc.
2. **Given** chương mẫu ở chế độ Tiết kiệm, **When** tải về, **Then** dung lượng lưu không vượt quá 150% dung lượng ảnh nhận qua mạng.
3. **Given** ảnh sau xử lý, **When** người đọc xem, **Then** không thấy khác biệt chất lượng rõ rệt so với trước.

---

### User Story 4 - Người đóng góp kiểm tra thay đổi tự động (Priority: P3)

Khi có pull request, hệ thống tự build và chạy các bài kiểm tra; người bảo trì thấy kết quả đạt/không đạt trước khi gộp.

**Why this priority**: Giảm rủi ro hỏng bản phát hành, nhưng không ảnh hưởng trực tiếp tới người đọc.

**Independent Test**: Mở pull request có lỗi cố ý; kiểm tra tự động báo không đạt.

**Acceptance Scenarios**:

1. **Given** pull request mới, **When** được tạo hoặc cập nhật, **Then** build và kiểm tra tự động chạy và báo kết quả.
2. **Given** kiểm tra không đạt, **When** xem pull request, **Then** thấy rõ bước nào lỗi.
3. **Given** pull request từ nguồn bên ngoài, **When** kiểm tra chạy, **Then** không cần và không dùng khóa ký phát hành.

---

### User Story 5 - Tài liệu mô tả đúng tính năng hiện có (Priority: P3)

Người dùng và người đóng góp đọc README để biết các mức chất lượng ảnh, cách tiết kiệm data, fallback miền và cách chạy kiểm tra.

**Why this priority**: Tính năng không được mô tả thì người dùng không biết để dùng.

**Independent Test**: Người chưa biết dự án đọc README và tìm đúng nơi bật chế độ tiết kiệm data.

**Acceptance Scenarios**:

1. **Given** README, **When** người đọc tìm "tiết kiệm data", **Then** thấy mô tả ba mức chất lượng cùng mức giảm dung lượng ước tính.
2. **Given** README, **When** người đóng góp muốn chạy kiểm tra, **Then** thấy lệnh cụ thể.

---

### Edge Cases

- Miền dự phòng hiện bị Cloudflare chặn với request không qua bộ vượt của ứng dụng; fallback dựa vào bộ vượt này.
- Miền thứ nhất chỉ chậm (không chết hẳn): chờ tối đa một ngưỡng thời gian hợp lý rồi mới chuyển miền.
- Máy chủ trả mã lỗi yêu cầu giảm tần suất: extension phải giãn request thay vì lặp lại ngay.
- Người dùng bật tùy chọn proxy do extension cung cấp: tắt fallback miền (request đi qua proxy như hiện tại, không tự đổi host).
- Miền dự phòng bị Cloudflare chặn: báo lỗi rõ ràng, không lặp vô hạn.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Khi không bật proxy, hệ thống MUST tự chuyển sang miền còn lại khi miền đang chọn không truy cập được, cho danh sách, chi tiết và chương; ảnh trang chuyển giữa các máy chủ ảnh tương đương.
- **FR-002**: Hệ thống MUST hiển thị thông báo lỗi rõ ràng khi cả hai miền đều không truy cập được.
- **FR-003**: Hệ thống MUST NOT gửi request sang miền dự phòng khi miền đang chọn phản hồi bình thường.
- **FR-004**: Hệ thống MUST giới hạn tần suất request tới máy chủ truyện và máy chủ ảnh ở mức không gây bị chặn.
- **FR-005**: Hệ thống MUST giãn request khi máy chủ yêu cầu giảm tần suất.
- **FR-006**: Giới hạn tần suất MUST NOT làm chậm đáng kể việc hiển thị trang đầu tiên của một chương.
- **FR-007**: Ảnh sau khi ghép lại MUST có dung lượng lưu không vượt quá 110% ảnh gốc (chế độ Nguồn) hoặc 150% ảnh nhận qua mạng (chế độ tiết kiệm), không giảm chất lượng nhìn thấy rõ.
- **FR-008**: Dự án MUST có bộ kiểm tra tự động cho các logic thuần (ghép dải ảnh, tạo địa chỉ ảnh, chọn miền dự phòng) chạy được bằng một lệnh.
- **FR-009**: Build bản debug MUST chạy được không cần khóa ký phát hành; hệ thống CI MUST build bản debug và chạy bộ kiểm tra cho mỗi pull request, không cần khóa ký phát hành.
- **FR-010**: README MUST mô tả ba mức chất lượng ảnh, mức giảm dung lượng ước tính, cơ chế fallback miền và cách chạy kiểm tra.
- **FR-011**: Mặc định của mọi tùy chọn (bao gồm tùy chọn chất lượng ảnh) MUST giữ hành vi hiện tại của người dùng cũ.

### Key Entities

- **Tùy chọn người dùng**: miền, proxy, chất lượng ảnh (đã có). Không thêm tùy chọn mới.
- **Miền**: hai địa chỉ truy cập tương đương, một đang chọn và một dự phòng.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Khi miền đang chọn không truy cập được, 95% lượt mở danh sách và chương vẫn thành công qua miền còn lại trong dưới 15 giây.
- **SC-002**: Làm mới thư viện 100 truyện hoàn tất mà không có lượt nào bị máy chủ chặn.
- **SC-003**: Thời gian hiển thị trang đầu tiên của một chương không tăng quá 1 giây so với trước khi có giới hạn tần suất.
- **SC-004**: Dung lượng lưu một chương không vượt quá 110% ảnh gốc (Nguồn) hoặc 150% ảnh nhận qua mạng (tiết kiệm).
- **SC-005**: 100% pull request được tự động build và kiểm tra, kết quả có trong vòng 15 phút.
- **SC-006**: Người đọc README tìm được cách bật tiết kiệm data trong dưới 1 phút.

## Assumptions

- Hai miền `cuutruyen.net` và `hetcuutruyen.net` cung cấp cùng nội dung và cùng cấu trúc dữ liệu.
- Ngưỡng giới hạn tần suất cụ thể sẽ chốt ở bước lập kế hoạch dựa trên đo thử thực tế.
- Người dùng chế độ Nguồn chấp nhận giảm nhẹ chất lượng nén lại nếu không thấy khác biệt bằng mắt.
- Không thay đổi định dạng dữ liệu đã lưu của người dùng hiện tại; tùy chọn mới chỉ bổ sung.
- Hạng mục ẩn NSFW đã bị loại khỏi phạm vi theo quyết định của chủ dự án (danh sách API không có trường is_nsfw, chỉ có ở trang chi tiết).
- Phạm vi gồm 5 hạng mục còn lại; không thêm nguồn truyện khác hay tính năng đọc mới.
