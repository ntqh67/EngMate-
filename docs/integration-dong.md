# Tích hợp với module ngữ pháp, quiz, nhắc học

Tài liệu ngắn cho Huân và Khang: mở màn của Đông ở đâu, cài đặt hợp đồng nào,
và quy ước DataStore. Các màn dưới đây **đã chạy thật** (làm quiz, kết quả và
xem lại, kiểm tra trình độ, nhắc học). Cách demo: [demo-dong.md](demo-dong.md).

## Lối vào màn hình

**Trạng thái:** sau khi rebase lên main (điều hướng theo tab của PR #4), các màn dưới đây
biên dịch và có test nhưng **chưa được nối** vào `navigation/EngMateApp.kt`. Bảng mô tả
nơi cần gọi; `Detail.X` là tên lối vào dự kiến khi nối lại.

| Lối vào | Màn hình | Người gọi | Khi xong |
| --- | --- | --- | --- |
| `Detail.Quiz(QuizSource.Lesson(id))` | `QuizScreen` | Nút "Làm bài tập" trong bài ngữ pháp | Mở `Detail.QuizResult(attemptId)` |
| `Detail.Quiz(QuizSource.Topic(id))` | `QuizScreen` | **Khang**: nút "Làm quiz" ở màn chủ đề từ vựng | Mở `Detail.QuizResult(attemptId)` |
| `Detail.Quiz(QuizSource.Placement)` | `QuizScreen` | Dự phòng; thường dùng `Detail.Placement` | Mở `Detail.QuizResult(attemptId)` |
| `Detail.QuizResult(attemptId)` | `QuizResultScreen` | Tự mở sau khi làm quiz | "Làm lại" mở `Detail.Quiz(source)`, "Xong" đóng |
| `Detail.Placement` | `PlacementScreen` | **Huân**: Onboarding; mục "Kiểm tra trình độ" trong Hồ sơ | Đóng; `level` là A1–B2 hoặc `null` nếu chọn "Để sau" |
| `Detail.ReminderSettings` | `ReminderSettingsScreen` | Mục "Nhắc học hằng ngày" trong Hồ sơ | Đóng |

Chữ ký công khai:

```kotlin
QuizScreen(source: QuizSource, onFinished: (attemptId: Long) -> Unit, onBack: () -> Unit, onOpenProfile: () -> Unit = {})
QuizResultScreen(attemptId: Long, onRetry: (QuizSource) -> Unit, onBack: () -> Unit)
PlacementScreen(onFinished: (level: String?) -> Unit, onBack: () -> Unit, onOpenProfile: () -> Unit = {})
ReminderSettingsScreen(onBack: () -> Unit)
```

- `onOpenProfile` (mới, có giá trị mặc định): hiện nút "Tạo hồ sơ" khi chưa có hồ sơ nên chưa lưu
  được kết quả. `EngMateApp` nối nó để đóng màn và chuyển sang tab Hồ sơ.
- Onboarding không nằm trong `EngMateApp` thì gọi thẳng
  `PlacementScreen(onFinished = { level -> ... }, onBack = { ... }, onOpenProfile = { ... })`.
  Màn đã tự lưu kết quả vào lịch sử và cập nhật `english_level` qua `ProfileRepository`;
  `level` trả về chỉ để Onboarding biết đi tiếp.
- Số câu: bài ngữ pháp lấy tối đa 10 câu ngẫu nhiên; chủ đề cần ít nhất 10 câu;
  kiểm tra trình độ cần đủ 15 câu (thứ tự cố định). Thiếu câu thì màn báo rõ, không crash.

## Dữ liệu câu hỏi

- `QuestionRepository` có thêm `questionsForTopic(topicId)`: truy vấn Room theo `topic_id`.
  Bản live đồng bộ trường `topicId` từ Firestore xuống Room qua `FirestoreContentSync`, nên
  câu hỏi chủ đề trên Firestore dùng được ngay. `SyncedQuestionRepository` ủy quyền `by local`
  nên không cần sửa code sync.
- Màn quiz chỉ đọc qua repository, không gọi Firestore trực tiếp.
- Kết quả làm bài **chỉ lưu Room**; không có collection Firestore mới, `firestore.rules` không đổi
  (đồng bộ lịch sử là E03, ngoài phạm vi).
- `AttemptSummary` có thêm `lessonId`, `topicId` (mặc định `null`) và `retrySource()`.

## Hợp đồng tích hợp (`core/contracts`)

Khai báo binding trong `di/IntegrationContractsModule.kt`, mỗi hợp đồng một dòng.
Khi có cài đặt thật, **chỉ thay đúng dòng đó**, không sửa nơi gọi, ví dụ:
`@Provides @Singleton fun progressRecorder(impl: RoomProgressRecorder): ProgressRecorder = impl`.

| Hợp đồng | Người cài đặt | Bản mặc định | Quiz dùng ở đâu |
| --- | --- | --- | --- |
| `ProgressRecorder.onQuizCompleted(uid, xpEarned, correctCount, totalQuestions, isPlacement, completedAt)` | **Huân** (`progress`): cộng XP, ghi hoạt động ngày, cập nhật streak | `NoOpProgressRecorder` | `RoomQuizAttemptRepository.saveAttempt`, gọi đúng 1 lần sau khi lượt làm đã lưu |
| `TopicLookup.observeTopics()` / `titleOf(topicId)` trả `TopicRef(id, title)` | **Khang** (`vocabulary`), đọc từ bảng `topics` | `EmptyTopicLookup` (rỗng, `null`) | Tên chủ đề trên màn quiz và lịch sử; lọc và chọn chủ đề trong Ngân hàng câu hỏi |

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
| `reminder_enabled`, `reminder_hour`, `reminder_minute` | Đông (nhắc học, `ReminderRepository`) |
| `theme_*`, `app_language` | Cài đặt giao diện |
| `recent_searches` | Khang (tra từ) |

Cài đặt theo tài khoản phải có `uid` trong key hoặc bị xoá khi đăng xuất (ADR-0001, mục 4).

## Nhắc học hằng ngày

- Bật/tắt và giờ nhắc lưu trong DataStore; mặc định tắt, 20:00.
- Lịch nhắc dùng WorkManager: một work duy nhất tên `daily_study_reminder`, giờ truyền qua
  `inputData`. Bật hoặc đổi giờ thì hẹn lại bằng `REPLACE`; tắt thì `cancelUniqueWork`.
  Worker hiện thông báo rồi tự hẹn ngày hôm sau. WorkManager giữ lịch sau khi khởi động lại máy.
- Thông báo dùng kênh `study_reminder`; Android 13+ xin quyền `POST_NOTIFICATIONS` khi bật công tắc.
- Không dùng `AlarmManager`/`SCHEDULE_EXACT_ALARM`: thông báo có thể trễ vài phút khi máy tiết kiệm pin.

**Khác biệt có chủ đích so với ADR-0001:** cài đặt nhắc học áp dụng cho **cả thiết bị**, không
theo `uid`. SRS FR12 chỉ yêu cầu bật/tắt và chọn giờ cho thiết bị; nhắc học là thói quen của người
dùng máy, không chứa dữ liệu học cá nhân, và thông báo không hiện thông tin tài khoản. Nếu sau này
cần tắt nhắc khi đăng xuất, **Huân** gọi `ReminderScheduler.cancel()` (đã công khai, hiện chưa gọi ở
đâu) và `ReminderRepository.setEnabled(false)` trong luồng đăng xuất.

## Schema Room

`EngMateDatabase` giữ **version 2**; không thêm entity hay migration (chỉ thêm `@Query`).
Khang lên version 3 khi thêm bảng từ vựng/`topics`. Sau khi phần đó được merge,
Đông viết migration thêm khoá ngoại `topic_id → topics.id` cho `questions` và
`quiz_attempts` (TODO trong `quiz/data/local/QuizEntities.kt`).
