# Tài khoản, hồ sơ và tiến độ

## Bản demo

Chọn `demoDebug`. Demo mở một phiên cục bộ; chọn Cài đặt → Đăng xuất,
sau đó chọn Vào bản demo để quay lại. Demo không tạo tài khoản thật, không
gửi email và không đồng bộ dữ liệu giữa thiết bị.

## Bản live

1. Tạo ứng dụng Android `com.engmate.app` trong Firebase và đặt file cấu hình
   `google-services.json` tại `app/` (file này không commit vào Git).
2. Trong Firebase Authentication, bật Email/Password và cấu hình email xác minh,
   đặt lại mật khẩu phù hợp dự án. Chọn `liveDebug`, đồng bộ Gradle và chạy app.
3. Đăng ký bằng họ tên, email và mật khẩu từ 6 ký tự. Mở email xác minh, sau đó
   đăng nhập. Nếu email chưa xác minh, ứng dụng thử gửi lại email xác minh.
4. Sau khi đăng nhập, tạo hồ sơ trên thiết bị. Hồ sơ tách khỏi tài khoản Firebase;
   nếu tạo hồ sơ thất bại, người dùng có thể thử lại, không cần đăng ký lại.
5. Vào Cài đặt → Đăng xuất. Chọn Quên mật khẩu trên màn hình đăng nhập để gửi
   email đặt lại mật khẩu.

Email và tên tài khoản dùng Firebase; hồ sơ và lịch sử học lưu trong Room cục bộ,
không tự đồng bộ giữa thiết bị. Chưa có đăng nhập Google. Cần cấu hình Firebase
và kiểm tra thiết bị thật trước khi khẳng định luồng email hoạt động đầu cuối.

## Cách tính tiến độ

- Lượt từ đã học và XP: tổng từ `daily_study_stat` của người dùng hiện tại.
- Từ cần ôn: số bản ghi `word_progress` đến hạn.
- Chuỗi ngày: số ngày liên tiếp có hoạt động, tính từ hôm nay hoặc hôm qua.
- Biểu đồ: thời gian học theo phút của 7 ngày gần nhất.
- Độ chính xác: tổng câu đúng chia tổng câu hỏi của các lượt quiz; chưa có quiz
  hiển thị “Chưa có dữ liệu”.

Số liệu chỉ thay đổi khi các chức năng học ghi hoạt động/kết quả vào Room; không
sử dụng số liệu giả. Các màn hình học và quiz vẫn đang được phát triển.
