# Dữ liệu khởi đầu: từ vựng và ngữ pháp

## Từ vựng (`app/src/main/assets/vocabulary_seed.json`)
- 15 chủ đề (A1–B1), khoảng 446 từ. Mỗi từ có phiên âm IPA, từ loại, nghĩa tiếng Việt, định nghĩa tiếng Anh và một ví dụ.
- `VocabularySeeder` nạp vào Room mỗi lần mở app (idempotent): chủ đề khớp theo tiêu đề, từ khớp theo chữ.
  Từ đã tra bằng API được giữ nguyên, chỉ bổ sung phần còn thiếu (nghĩa Việt, chủ đề, ví dụ).
- Tab **Học → Từ vựng** duyệt theo chủ đề; chạm vào từ để mở chi tiết.
- Muốn thêm từ: sửa JSON (hoặc file nguồn khi sinh dữ liệu) rồi mở lại app, từ mới được thêm vào.

## Tra từ bằng API và dịch tiếng Việt
- Từ không có trong bộ khởi đầu vẫn tra bằng dictionaryapi.dev và được lưu cache.
- Nghĩa tiếng Việt của từ lấy từ API được dịch bằng **Google ML Kit Translate** chạy trên máy. Lần đầu cần mạng để tải
  mô hình (~30 MB), sau đó dùng offline. Việc dịch chạy nền nên không làm chậm lúc tra; nghĩa Việt xuất hiện sau ít giây.
- Nghĩa này là dịch tự động của một từ đơn, độ chính xác có hạn so với các từ trong bộ khởi đầu.

## Ngữ pháp thì và động từ (`app/src/main/assets/grammar_seed.json`)
14 bài (id 101–114), mỗi bài có giải thích, ví dụ và 6 câu quiz (84 câu): quá khứ đơn/tiếp diễn/hoàn thành, hiện tại hoàn thành
(và tiếp diễn), tương lai đơn/be going to/tương lai tiếp diễn, động từ to be, động từ khuyết thiếu, động từ bất quy tắc,
V-ing hay to V, cụm động từ, câu bị động.

- `ContentSeeder` (live) thêm vào Room trên mọi thiết bị, kể cả khi chưa đăng nhập.
- Khi đăng nhập, bài nào Firestore chưa có thì được tải lên trước khi đồng bộ, nếu không đồng bộ sẽ xóa bài đó khỏi máy.
  Bài đã có trên Firestore không bị ghi đè nên chỉnh sửa trên console được giữ.
- `SeedDataTest` kiểm tra hai file JSON qua đúng các validator của app.
