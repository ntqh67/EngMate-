# Tích hợp với module ngữ pháp, quiz, nhắc học

Tài liệu ngắn cho Huân và Khang: mở màn của Đông ở đâu, cài đặt hợp đồng nào,
và quy ước DataStore. Các màn dưới đây đã có chữ ký cố định; nội dung đang
là màn tạm "Đang phát triển" và sẽ được hoàn thiện ở PR sau mà không đổi chữ ký.

## Lối vào màn hình

Điều hướng tự viết trong `navigation/EngMateApp.kt`: gọi `open(Detail.X)` để mở,
`close()` để đóng. Khoá lưu trạng thái là chuỗi, ví dụ `quiz:lesson:3`.

| Lối vào | Màn hình | Người gọi | Khi xong |
| --- | --- | --- | --- |
| `Detail.Quiz(QuizSource.Lesson(id))` | `QuizScreen` | Nút "Làm bài tập" trong bài ngữ pháp (đã nối) | Mở `Detail.QuizResult(attemptId)` |
| `Detail.Quiz(QuizSource.Topic(id))` | `QuizScreen` | **Khang**: nút "Làm quiz" ở màn chủ đề từ vựng | Mở `Detail.QuizResult(attemptId)` |
| `Detail.Quiz(QuizSource.Placement)` | `QuizScreen` | Dự phòng; thường dùng `Detail.Placement` | Mở `Detail.QuizResult(attemptId)` |
| `Detail.QuizResult(attemptId)` | `QuizResultScreen` | Tự mở sau khi làm quiz | "Làm lại" mở `Detail.Quiz(source)` |
| `Detail.Placement` | `PlacementScreen(onFinished: (level: String?) -> Unit, onBack)` | **Huân**: Onboarding; mục "Kiểm tra trình độ" trong Hồ sơ (đã nối) | Đóng; `level` là A1–B2 hoặc `null` nếu bỏ qua |
| `Detail.ReminderSettings` | `ReminderSettingsScreen` | Mục "Nhắc học hằng ngày" trong Hồ sơ (đã nối) | Đóng |

Onboarding không nằm trong `EngMateApp` thì gọi thẳng composable
`PlacementScreen(onFinished = { level -> ... }, onBack = { ... })`.
Màn chỉ trả về trình độ; bản hoàn chỉnh sẽ tự lưu `english_level` vào hồ sơ
qua `ProfileRepository` như bài kiểm tra trình độ hiện tại.

## Hợp đồng tích hợp (`core/contracts`)

Khai báo binding trong `di/IntegrationContractsModule.kt`, mỗi hợp đồng một dòng.
Khi có cài đặt thật, **chỉ thay đúng dòng đó**, không sửa nơi gọi, ví dụ:
`@Provides @Singleton fun progressRecorder(impl: RoomProgressRecorder): ProgressRecorder = impl`.

| Hợp đồng | Người cài đặt | Bản mặc định | Quiz dùng ở đâu |
| --- | --- | --- | --- |
| `ProgressRecorder.onQuizCompleted(uid, xpEarned, correctCount, totalQuestions, isPlacement, completedAt)` | **Huân** (`progress`): cộng XP, ghi hoạt động ngày, cập nhật streak | `NoOpProgressRecorder` | `RoomQuizAttemptRepository.saveAttempt`, gọi đúng 1 lần sau khi lượt làm đã lưu |
| `TopicLookup.observeTopics()` / `titleOf(topicId)` trả `TopicRef(id, title)` | **Khang** (`vocabulary`), đọc từ bảng `topics` | `EmptyTopicLookup` (rỗng, `null`) | Tên chủ đề trong lịch sử làm bài; lọc và chọn chủ đề trong Ngân hàng câu hỏi |

Lưu ý khi cài đặt:
- `ProgressRecorder`: lỗi ném ra bị bỏ qua để không mất kết quả đã lưu, nên hãy tự
  ghi log hoặc retry nếu cần. Hàm được gọi một lần cho mỗi lượt làm; nếu sau này có
  retry thì nên chống cộng trùng theo `uid` + `completedAt`.
- `TopicLookup.observeTopics()` phải phát ít nhất một lần (danh sách rỗng cũng được).
  Khi rỗng hoặc lỗi, quiz quay về cách cũ: hiện "Chủ đề #id" và nhập mã số.

## DataStore

Một `DataStore<Preferences>` dùng chung, file `engmate_settings`, inject từ
`di/DataStoreModule.kt`. Không tạo thêm DataStore cho cùng file.
Mỗi tính năng tự khai báo key trong package của mình, theo tiền tố ở
`core/datastore/PreferenceKeys.kt`:

| Tiền tố / key | Chủ sở hữu |
| --- | --- |
| `reminder_*` | Đông (nhắc học) |
| `theme_*`, `app_language` | Cài đặt giao diện |
| `recent_searches` | Khang (tra từ) |

Cài đặt theo tài khoản phải có `uid` trong key hoặc bị xoá khi đăng xuất (ADR-0001, mục 4).
Quyền `POST_NOTIFICATIONS` đã khai báo trong manifest; xin quyền lúc chạy sẽ làm cùng PR nhắc học.

## Schema Room

`EngMateDatabase` giữ **version 2**; PR này không thêm entity hay migration.
Khang lên version 3 khi thêm bảng từ vựng/`topics`. Sau khi phần đó được merge,
Đông viết migration thêm khoá ngoại `topic_id → topics.id` cho `questions` và
`quiz_attempts` (TODO trong `quiz/data/local/QuizEntities.kt`).
