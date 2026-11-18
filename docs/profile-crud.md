# CRUD hồ sơ cá nhân

## Phạm vi đã triển khai

- Create: tạo một hồ sơ cho UID hiện tại; tạo trùng báo lỗi, không ghi đè.
- Read: theo dõi Room qua Flow, hiển thị tên, ảnh, email, trình độ và mục tiêu.
- Update: sửa tên, URL ảnh HTTPS (tùy chọn), trình độ và phút học mỗi ngày.
  UID, email và thời điểm tạo không được sửa từ form.
- Delete: xác nhận trước khi xóa hồ sơ, xóa cả bản trong Room và bản trên Firestore
  (`users/{uid}/profile/main`). Không xóa tài khoản Firebase. Có thể tạo lại sau khi xóa.
- Validation ở cả ViewModel và Repository: tên 2–50 ký tự chữ/khoảng trắng,
  hỗ trợ dấu tiếng Việt; mục tiêu nguyên 5–120; trình độ A1–C2; URL ảnh HTTPS.
- Trạng thái tải/lỗi, thử tải lại, chống bấm lưu lặp, giữ form khi lưu thất bại.
- Đổi UID/đăng xuất hủy quan sát và tác vụ của phiên cũ, xóa form và hồ sơ cũ
  khỏi UI; Repository kiểm tra UID trước thao tác. Đây là kiểm soát trong app,
  không thay thế Firestore Rules khi bổ sung lưu trữ trực tuyến.

## Đường đi dữ liệu

```text
ProfileScreen → ProfileViewModel → ProfileRepository
                                      ↑
                              RoomProfileRepository
                                      ↓
                               UserProfileDao → Room
```

Hilt cung cấp database, DAO, Clock, Repository và UserSession. Domain không
phụ thuộc Room/Firebase. Schema v1 và v2 đã được export tại `app/schemas`; v2 thêm các bảng học tập.
Không bật destructive migration; tăng version và thêm migration khi đổi schema.

## Chạy ngay bản demo

1. Mở thư mục chứa `settings.gradle.kts` trong Android Studio.
2. Cài SDK 37, Build Tools 36.0.0, đồng bộ Gradle.
3. Chọn variant `demoDebug`, chạy trên Android API 26+.
4. Chọn **Tạo hồ sơ**, nhập tên, trình độ, mục tiêu rồi lưu.
5. Đóng/mở lại để kiểm tra lưu bền; sửa hồ sơ hoặc xác nhận xóa.

Demo sử dụng `local-demo-user`, email `demo@engmate.local`, applicationId
`com.engmate.app.demo` và database `engmate-demo.db`. Đây không phải tài khoản
Firebase. Demo không yêu cầu mạng trừ khi tải ảnh từ URL.

## Tích hợp với Firebase Auth

Variant `live` sử dụng `FirebaseUserSession`, lắng nghe Firebase Auth để lấy
UID và email. Không có phiên đăng nhập thì UI yêu cầu đăng nhập và không mở
form CRUD; thiếu cấu hình Firebase cũng không fallback sang tài khoản demo.

1. Thêm Android app `com.engmate.app` vào Firebase project.
2. Đặt cấu hình tại `app/google-services.json`. Google Services plugin chỉ
   được áp dụng khi file tồn tại; xử lý Google Services cho demo được tắt.
3. Chọn `liveDebug`, đăng ký và xác minh email trước khi đăng nhập.
4. Tạo hồ sơ trên thiết bị khi đăng nhập lần đầu. Hồ sơ lưu trong Room và đồng bộ lên Firestore (xem [cloud-sync.md](cloud-sync.md));
   xem [hướng dẫn tài khoản và tiến độ](auth-progress.md).

Firebase BoM được ghim 34.0.0 để tương thích Kotlin 2.2.10 của AGP đang dùng.
Build live thành công chỉ xác nhận mã tích hợp biên dịch, không xác nhận cấu hình
Firebase hay luồng đăng nhập trên thiết bị thật. Luồng live yêu cầu email đã
xác minh trước khi vào ứng dụng.

## Khác biệt với ERD ban đầu

- Không lưu `password`.
- Chưa triển khai `username`: SRS đăng nhập bằng email, chưa có chức năng tên
  tài khoản duy nhất. Không tạo giá trị username giả để lấp cột.
- Không lưu/chỉnh `role` trong CRUD hồ sơ. Phân quyền admin cần nguồn tin cậy
  từ backend/Firebase khi triển khai, không dựa vào giá trị người dùng sửa.
- Email là snapshot tại thời điểm tạo hồ sơ; UI hiển thị email của phiên hiện
  tại. Thay đổi email Firebase là luồng riêng, cần đồng bộ snapshot khi bổ sung.
- Từ database v2, các bảng học tập tham chiếu hồ sơ bằng FK RESTRICT. Xóa
  hồ sơ có dữ liệu học liên quan sẽ bị chặn và hiển thị thông báo; không tự
  xóa lịch sử, tài khoản hoặc toàn bộ dữ liệu học. Xem [database.md](database.md).

## Kiểm thử

```bash
./gradlew :app:testDemoDebugUnitTest :app:lintDemoDebug
./gradlew :app:assembleDemoDebug :app:assembleLiveDebug
```

- Validation: tiếng Việt, biên 5/120 phút, URL, dữ liệu không hợp lệ.
- Room với Robolectric: CRUD thật, đóng/mở database, UID isolation, tạo trùng,
  sửa bản ghi không tồn tại, bảo toàn email/created_at, từ chối khi đăng xuất.
- ViewModel: input sai không ghi, lỗi lưu giữ form và cho retry, chống lưu lặp,
  xác nhận/hủy xóa, xóa trạng thái khi đổi phiên.
- Compose với Robolectric: tạo → xem → sửa → hủy sửa → hủy xóa → xác nhận xóa.

Chưa kiểm tra trên máy thật/máy ảo Android; kiểm thử Compose ở đây chạy JVM.
Chưa hỗ trợ tải file ảnh lên Storage, đồng bộ Firestore hoặc khôi phục form
sau khi tiến trình bị hệ điều hành kết thúc. ViewModel giữ form khi xoay màn
hình; dữ liệu đã lưu tồn tại trong Room khi mở lại app.
