# Đồng bộ dữ liệu người dùng với Firestore

Room là nguồn dữ liệu giao diện đọc; Firestore là bản sao trên cloud. Chỉ build `live`.

## Cấu trúc Firestore

```text
users/{uid}/profile/main                    hồ sơ
users/{uid}/notebook/{từ}                   từ trong sổ tay + ghi chú
users/{uid}/word_notes/{từ}                 nghĩa/ví dụ/ghi chú riêng
users/{uid}/word_progress/{từ}              trạng thái ôn tập (SM-2)
users/{uid}/flashcard_collections/c{ts}     bộ thẻ + các thẻ
users/{uid}/quiz_attempts/a{ts}             lần làm quiz + đáp án
users/{uid}/learning_activity/{activityId}  XP, thời gian học
grammar_lessons/{id}, questions/{id}        nội dung dùng chung (đọc/ghi khi đã đăng nhập)
```

Id dòng trong Room khác nhau giữa các máy, nên tài liệu đặt khóa theo chữ của từ
hoặc mốc thời gian. Thống kê ngày (`daily_study_stat`) được tính lại từ `learning_activity`.

## Luồng hoạt động

1. **Ghi:** ghi vào Room trước, rồi đẩy lên Firestore. Mất mạng thì Firestore xếp hàng gửi lại.
2. **Đăng nhập / mở app:** `UserDataSync` tải dữ liệu cloud về. Mục chưa có trên máy được thêm;
   với ghi chú và tiến độ, bản mới hơn thắng. Sau đó theo dõi Room và đẩy mọi thay đổi lên.
3. **Xóa:** xóa dòng cục bộ thì xóa tài liệu tương ứng trên cloud.

Mã nguồn: `app/src/live/java/com/engmate/app/sync/` (`SyncedProfileRepository`, `UserDataSync`,
`FirestoreContentSync`).

## Giới hạn hiện tại

- Chỉ tải về khi đăng nhập/mở app, chưa đồng bộ thời gian thực giữa hai máy đang mở.
- Cài đặt nhắc học (DataStore) chưa đồng bộ.
- Rule cho `grammar_lessons`/`questions` đang cho mọi người dùng đã đăng nhập ghi; cần giới hạn cho admin.

## Chế độ khách

Chưa đăng nhập vẫn xem Trang chủ, tìm từ và học ngữ pháp. Sổ tay, Hồ sơ/Tiến độ và quiz yêu cầu đăng nhập;
hồ sơ chỉ bắt tạo khi vào các chức năng cá nhân.
