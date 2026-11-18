# Room database v2 — EngMate

Database cục bộ đã có đủ 15 bảng từ ERD nhóm cung cấp. Phần này cung cấp
Entity, DAO, các transaction và migration; chưa triển khai giao diện hay
Repository nghiệp vụ cho các chức năng ngoài hồ sơ, chưa nhập bộ dữ liệu
20 chủ đề × 20 từ và chưa đồng bộ Firestore.

## Bảng và vị trí mã nguồn

Các đường dẫn dưới `app/src/main/java/com/engmate/app/feature/`:

| Bảng | Entity | DAO | Thư mục |
| --- | --- | --- | --- |
| user_profile | UserProfileEntity | UserProfileDao | profile/data/local |
| topics | TopicEntity | VocabularyDao | vocabulary/data/local |
| words | WordEntity | VocabularyDao | vocabulary/data/local |
| word_progress | WordProgressEntity | FlashcardDao | flashcard/data/local |
| flashcard_collections | FlashcardCollectionEntity | FlashcardDao | flashcard/data/local |
| flashcards | FlashcardEntity | FlashcardDao | flashcard/data/local |
| notebook_words | NotebookWordEntity | NotebookDao | notebook/data/local |
| word_notes | WordNoteEntity | NotebookDao | notebook/data/local |
| grammar_lessons | GrammarLessonEntity | GrammarDao | grammar/data/local |
| questions | QuestionEntity | QuizDao | quiz/data/local |
| question_options | QuestionOptionEntity | QuizDao | quiz/data/local |
| quiz_attempts | QuizAttemptEntity | QuizDao | quiz/data/local |
| attempt_answers | AttemptAnswerEntity | QuizDao | quiz/data/local |
| learning_activity | LearningActivityEntity | StudyDao | progress/data/local |
| daily_study_stat | DailyStudyStatEntity | StudyDao | progress/data/local |

`core/database/EngMateDatabase.kt` đăng ký các bảng và DAO. Hilt cung cấp các
DAO trong `di/ProfileModule.kt` (object `DatabaseModule`). Các thao tác ghi
là `suspend`; màn hình quan sát dữ liệu qua Flow. Không gọi DAO từ Composable:
Repository của từng chức năng phải gọi DAO, ViewModel gọi Repository.

## Quy ước và khác biệt với bản ERD

- UID Firebase là TEXT. Bảng cá nhân dùng `user_uid`, FK đến `user_profile.uid`.
  Giữ nguyên schema hồ sơ v1: không password, chưa username/role.
- ID số là Kotlin Long, SQLite INTEGER; các bảng nội dung tự sinh ID khi id = 0.
- Các FK từ tiến độ, ghi chú, sổ tay dùng `word_id`, không dùng chuỗi `word`.
- `phonetic` chỉ xuất hiện một lần trong `words`.
- Giữ cấu trúc một từ thuộc tối đa một chủ đề. `words.word` unique toàn kho
  với NOCASE cho từ tiếng Anh; chưa chuyển sang quan hệ nhiều–nhiều. NOCASE
  SQLite không cung cấp tìm kiếm tiếng Việt không dấu.
- `total_words` được COUNT qua truy vấn `TopicWithWordCount`, không lưu một
  cột đếm có thể lệch khi thêm/xóa từ.
- Timestamp dùng Long epoch milliseconds UTC; thời lượng dùng giây, mục tiêu
  dùng phút, khoảng ôn dùng ngày. Tên `interval` đổi thành `interval_days`.
- Thêm `ease_factor` (mặc định 2.5) cho lịch ôn. Đây là lưu trữ trạng thái;
  thuật toán SM-2 sẽ được triển khai riêng, DAO không tự tính lịch.
- Mảng ví dụ/collocations/idioms dùng `List<String>` và TypeConverter JSON TEXT;
  không tách chuỗi bằng dấu phẩy. Enum lưu theo tên TEXT, boolean là INTEGER.
- `questions.correct_answer` bổ sung đáp án chuẩn cho dạng TEXT; loại lựa chọn
  đơn dùng `question_options.is_correct`. Câu hỏi thuộc đúng một topic, lesson
  hoặc nhóm kiểm tra đầu vào, được kiểm tra trong `saveQuestion`.
- `attempt_answers` thêm snapshot nội dung câu hỏi, lựa chọn và giải thích để
  kết quả đã nộp không đổi nội dung khi kho câu hỏi được sửa sau này.
- `learning_activity` bổ sung ngày học và số từ học/ôn, số quiz hoàn thành để
  thống kê ngày có thể tính lại chính xác sau khi hiệu chỉnh/xóa một hoạt động.

Ràng buộc PK/FK/unique được SQLite thực thi. Các ràng buộc nghiệp vụ khác
được kiểm tra trong DAO transaction hoặc Repository; không phải mọi cột đều
có CHECK trong SQL. Repository nội dung cần bổ sung validation đầu vào theo
SRS trước khi nối màn hình Admin (tiêu đề trống, độ dài nội dung, cấp độ…).

## Ràng buộc và chính sách xóa

| Quan hệ / dữ liệu | Chính sách |
| --- | --- |
| Dữ liệu cá nhân → user_profile | RESTRICT: không xóa hồ sơ còn dữ liệu liên quan |
| Word → topic | SET NULL: xóa chủ đề giữ lại từ; bị chặn nếu chủ đề còn question/attempt |
| Tiến độ / ghi chú / sổ tay → word | RESTRICT: không mất dữ liệu học khi xóa từ |
| Flashcard → collection | CASCADE: xóa bộ thẻ xóa thẻ trong bộ |
| Option → question | CASCADE, nhưng đáp án đã dùng trong lịch sử sẽ chặn xóa |
| Answer → attempt | CASCADE: xóa một lần làm bài xóa các đáp án của lần đó |
| Question / attempt → topic, lesson | RESTRICT: giữ liên kết nội dung và lịch sử |
| Answer → question, selected option | RESTRICT: không tạo lịch sử mồ côi |

FK ghép `(selected_option_id, question_id)` đảm bảo đáp án lựa chọn thật sự
thuộc câu hỏi tương ứng. Unique `(attempt_id, question_id)` tránh hai câu trả
lời cho cùng một câu trong một lần làm bài.

Unique `(user_uid, word_id)` áp dụng cho tiến độ, sổ tay và ghi chú: mỗi người
có một bản ghi mỗi loại cho một từ. `daily_study_stat` dùng PK ghép
`(user_uid, study_date)`.

Màn hình hồ sơ đã có thông báo khi xóa bị chặn bởi dữ liệu học. Luồng xóa toàn
bộ dữ liệu/tài khoản chưa có; không tự cascade từ hồ sơ để tránh xóa vượt quá
phạm vi nút “Xóa hồ sơ trên thiết bị”.

## API quan trọng

### Từ vựng và ngữ pháp

`VocabularyDao`: thêm/sửa/xóa topic và word, đọc topic kèm số từ, danh sách từ,
tra theo từ và tìm kiếm cục bộ. `GrammarDao`: CRUD bài học, lọc theo cấp độ.
Tìm kiếm dùng `instr` với bind parameter, `%` và `_` được hiểu là ký tự thật.

### Sổ tay và flashcard

`NotebookDao.add/get/updateNote/remove` thao tác sổ tay theo UID và word ID.
`saveWordNote` thêm/cập nhật ghi chú chi tiết trong transaction, giữ ID cũ;
xóa từ khỏi sổ tay không xóa từ chung hoặc tiến độ ôn.

`FlashcardDao.saveProgress` lưu lịch ôn, `observeDue(uid, now)` lấy các từ
đến hạn tại mốc thời gian truyền vào. Caller cần cập nhật `now` khi bắt đầu
phiên mới, Flow không tự thay đổi mốc thời gian theo đồng hồ.

CRUD bộ thẻ/thẻ cá nhân kiểm tra UID sở hữu. `addCard` xác nhận bộ thẻ thuộc
UID trong cùng transaction trước khi chèn; không cho chuyển thẻ sang bộ khác
qua thao tác chỉnh nội dung.

### Quiz

`saveQuestion(question, options)` lưu đồng thời câu hỏi và các lựa chọn;
loại SINGLE_CHOICE cần ít nhất hai lựa chọn, đúng một đáp án đúng. TEXT cần
đáp án chuẩn và không chứa lựa chọn. Chỉnh câu hỏi sẽ thay bộ lựa chọn; nếu
lựa chọn đã được tham chiếu trong lịch sử, transaction bị chặn và rollback.
Khi cần sửa cấu trúc câu đã dùng, tạo câu hỏi mới thay vì xóa lịch sử cũ.

`saveAttempt(attempt, answers)` lưu kết quả và toàn bộ đáp án trong transaction,
kiểm tra số câu, số đúng, phạm vi câu hỏi và option, rồi tự lấy snapshot từ
nội dung hiện tại. Kết quả đã hoàn thành là bất biến: làm lại tạo attempt mới,
không cung cấp chức năng sửa điểm tùy ý. `getAttempt`, `observeAttempts` và
`deleteAttempt` đều lọc UID. Sắp xếp options theo `orderIndex` khi hiển thị,
vì `@Relation` không cam kết thứ tự.

DAO kiểm tra tính nhất quán của kết quả được truyền vào, **không thay thế bộ
chấm điểm**. Logic so sánh đáp án, tính XP, giới hạn số câu, lựa chọn ngẫu
nhiên và nghiệp vụ kiểm tra đầu vào vẫn thuộc Repository/domain sau này.

### Hoạt động học và thống kê ngày

`StudyDao.record(activity)` ghi hoạt động và tính lại thống kê trong **một
transaction**. Caller tạo `activityId` ổn định cho cùng một phiên học:

- Cùng ID và cùng nội dung: trả false, không cộng XP lần hai.
- Cùng ID nhưng khác nội dung: báo lỗi; không ghi đè hoạt động khác.
- Lỗi ghi thống kê: rollback cả hoạt động.

`revise(uid, activity)` hiệu chỉnh một hoạt động thuộc UID rồi tính lại ngày
cũ và ngày mới nếu thay ngày. `delete(uid, id)` xóa và tính lại thống kê;
ngày không còn hoạt động sẽ không còn dòng thống kê. UI biểu đồ có thể tự
bổ sung ngày 0 để đủ 7 ngày.

Ngày `study_date` là ISO `yyyy-MM-dd`, được caller tính theo múi giờ người
học tại thời điểm hoàn thành. Lưu ngày này để đổi múi giờ sau đó không tự di
chuyển lịch sử sang ngày khác. `source_id` là tham chiếu nghiệp vụ đa hình,
không có FK vì có thể trỏ từ, bài học hoặc quiz.

Không chỉnh `daily_study_stat` trực tiếp; nó là dữ liệu tổng hợp của activity.
Tính streak từ các ngày có hoạt động và quy tắc “ngày học hợp lệ” sẽ được
triển khai trong domain. Sửa/xóa activity là API bảo trì, không phải chức
năng cho người học tự đổi XP.

Nộp quiz và ghi activity là hai DAO riêng. Khi tích hợp, Repository phải bọc
chúng trong `database.withTransaction { ... }`, dùng activity ID ổn định và
kiểm tra retry trước khi tạo attempt mới, để toàn bộ phiên hoàn tất là nguyên
tử và không sinh kết quả/XP trùng.

## Phạm vi xác thực

DAO là tầng dữ liệu nội bộ, không có phiên Firebase và không tự cấp quyền.
Mọi Repository cá nhân cần lấy UID từ UserSession, kiểm tra UID đúng người
đang đăng nhập trước khi gọi DAO; không nhận UID tùy ý từ ô nhập UI. DAO lọc
UID để tránh thao tác chéo ngoài ý muốn, nhưng người gọi nội bộ vẫn chịu
trách nhiệm chọn UID tin cậy. Admin/Firestore cần kiểm soát quyền phía server
khi được triển khai.

## Migration và kiểm thử

- `EngMateDatabase` version 2 dùng `AutoMigration(from = 1, to = 2)`.
- Schema `1.json` giữ nguyên; schema `2.json` được Room sinh và cần commit.
- Không dùng `fallbackToDestructiveMigration`.
- Dữ liệu hồ sơ v1 được giữ nguyên; 14 bảng mới bắt đầu rỗng.
- Kiểm thử migration tạo database từ **schema v1 đã export**, mở bằng Room
  v2 và kiểm tra thông tin hồ sơ, version, FK và thao tác trên bảng mới.

```bash
./gradlew :app:testDemoDebugUnitTest :app:lintDemoDebug
./gradlew :app:assembleDemoDebug :app:assembleLiveDebug
```

Các test database mới chạy bằng Robolectric Android API 26: CRUD, JSON,
unique, FK, quyền sở hữu theo UID, cascade/restrict, snapshot, chống lặp XP,
hiệu chỉnh thống kê qua hai ngày và rollback khi cố ý gây lỗi ghi thống kê.
Các test hồ sơ/giao diện cũ cũng tiếp tục chạy. Đây chưa phải kiểm thử trên
thiết bị Android thật.
