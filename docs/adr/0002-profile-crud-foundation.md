# ADR-0002: Khởi tạo Android và tách demo khỏi phiên Firebase

- Ngày: 2026-11-11.
- Trạng thái: Áp dụng cho triển khai CRUD hồ sơ.
- Bổ sung: [ADR-0001](0001-android-architecture.md).

## Bối cảnh

Repository mới có thư mục. CRUD hồ sơ cần Room và giao diện chạy được, trong
khi chưa có cấu hình Firebase hoặc màn hình đăng nhập.

## Quyết định

Khởi tạo project Android một module, Compose + Hilt + Room theo ADR-0001.
Dùng UserSession làm ranh giới cung cấp UID, không để UI nhập UID tùy ý.
Tách source set demo và live: demo có tài khoản cục bộ minh họa; live chỉ lấy
phiên Firebase. Hai biến thể dùng applicationId và database riêng để dữ liệu
minh họa không lẫn dữ liệu thật. Adapter demo không nằm trong source set live.

CRUD chỉ quản lý hồ sơ cục bộ; nút xóa ghi rõ phạm vi và cần xác nhận. Không
triển khai xóa tài khoản Firebase khi chưa có luồng tái xác thực và chính sách
xóa dữ liệu học liên quan. Bỏ password; username/admin role chưa thuộc phần
hồ sơ được yêu cầu nên không tạo trường giả hay quyền admin cục bộ.

Ảnh đại diện hiện nhập URL HTTPS, có ảnh thay thế khi tải lỗi. Upload ảnh sẽ
cần quyết định Storage và quyền truy cập riêng.

## Đánh đổi

Có thể demo CRUD ngay và kiểm thử bằng database thật trên JVM. Đổi lại nhóm
phải chọn đúng variant; live chưa đăng nhập được cho đến khi module auth được
triển khai. Kiểm thử JVM không thay thế kiểm thử vòng đời/quyền trên thiết bị.

Bộ công cụ được ghim và build kiểm chứng: Gradle 9.5.0, AGP 9.3.3, Kotlin
Compose plugin 2.2.10, KSP 2.3.10, Hilt 2.60.1, Room 2.8.4. Firebase BoM
34.0.0 được dùng vì 35.1.0 chứa Auth metadata Kotlin 2.4 không tương thích
compiler 2.2 hiện tại. Cập nhật dependency phải kiểm tra lại cả hai variant.
