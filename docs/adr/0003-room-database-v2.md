# ADR-0003: Triển khai toàn bộ ERD bằng Room v2

- Ngày: 2026-11-12.
- Trạng thái: Áp dụng trong code database.
- Bổ sung ADR-0001 và ADR-0002; bản vẽ ERD gốc chưa được sửa.

## Bối cảnh

Database v1 mới có user_profile. Nhóm cần tầng dữ liệu chung cho từ vựng,
flashcard, sổ tay, ngữ pháp, quiz và thống kê dựa trên ERD đã gửi.

## Quyết định

1. Thêm 14 bảng còn lại theo module nghiệp vụ, dùng FK/index/unique thực tế
   trong SQLite. Giữ schema user_profile v1; auto-migration 1→2 chỉ bổ sung
   bảng nên bảo toàn hồ sơ hiện có.
2. Thống nhất user_uid, dùng word_id làm FK, giữ mô hình một từ thuộc tối đa
   một chủ đề. Giữ từ unique toàn kho theo ERD; nhiều–nhiều là thay đổi sau.
3. Tính total_words bằng COUNT; thống kê ngày chỉ được cập nhật thông qua
   transaction ghi/hiệu chỉnh/xóa activity. Lưu thêm ngày và các bộ đếm trên
   activity để có thể tổng hợp lại; ID ổn định chống lặp XP khi retry.
4. Không cascade từ user_profile. RESTRICT bảo vệ lịch sử, phù hợp nút xóa
   hiện tại chỉ xóa hồ sơ. Khi có dữ liệu liên quan, Repository trả lỗi nghiệp
   vụ và UI giải thích; xóa toàn tài khoản cần quy trình riêng.
5. Cascade cho tập hợp con có vòng đời gắn với cha: thẻ trong bộ, đáp án trong
   attempt, option của question chưa được lịch sử tham chiếu. Các nội dung
   đã được lịch sử tham chiếu dùng RESTRICT.
6. Lưu quiz attempt và answers nguyên tử; bổ sung FK ghép để option phải
   thuộc question, snapshot nội dung lịch sử và đáp án chuẩn cho dạng TEXT.
7. Enum dùng TEXT, mảng dùng JSON converter, thời gian là epoch milliseconds.
   DAO cung cấp cơ chế lưu trữ, không đảm nhiệm chấm điểm, SM-2, auth hay sync.

## Hệ quả

Lợi ích: các module có chung database có ràng buộc, dữ liệu cũ được giữ,
không trôi số đếm và giảm rủi ro dữ liệu mồ côi/XP trùng.

Đánh đổi: xóa nội dung có lịch sử sẽ bị từ chối; UI Admin cần xử lý hoặc
thêm cơ chế lưu trữ mềm sau này. Ghi activity phải tính lại tổng một ngày,
phù hợp quy mô đồ án; khi dữ liệu tăng cần đo hiệu năng trước khi đổi sang
bộ đếm tăng dần. Các trường thêm/đổi phải được cập nhật lại trong ERD khi
nhóm yêu cầu chỉnh sơ đồ.

Chi tiết các bảng, API, ranh giới và kiểm thử: [database.md](../database.md).
