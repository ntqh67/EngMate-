# Thiết kế lại giao diện: việc cần Huân và Khang xem

Nhánh `feat/ui-refactor` chỉ đổi giao diện, thêm trường hiển thị vào ViewModel (lấy từ repository/Flow có sẵn) và sửa công thức
chấm luyện phát âm. Không đổi entity, DAO ghi, schema, migration, validator hay `firestore.rules`.

## Khang (home, vocabulary, flashcard, dictionary, notebook)

| Việc | Chi tiết |
|---|---|
| Review commit | `refactor(home): redesign home dashboard`, `refactor(vocabulary,flashcard,dictionary,notebook): redesign vocabulary screens` |
| Hành vi mới đã được nhóm duyệt | **Ôn tập hôm nay**: `FlashcardViewModel.startDue()` dùng `ReviewRepository.dueWords()` có sẵn, chấm SM-2 không đổi. **Vuốt để gỡ từ** trong Sổ tay: `NotebookViewModel.removeWord/undoRemove` gọi `removeWord/addWord` có sẵn. Tra từ tách thành màn riêng, logic tìm giữ nguyên. |
| Thiếu dữ liệu | **Tiến độ theo chủ đề từ vựng** (bao nhiêu từ của chủ đề đã học): chưa có truy vấn nên lưới chủ đề chỉ hiện số từ. Cần một hàm đọc kiểu `observeTopicProgress(uid)` (đếm `word_progress` theo `topic_id`). |
| Thiếu dữ liệu | Icon riêng cho từng chủ đề: hiện dùng chung một icon, đổi màu xen kẽ. Nếu thêm trường icon/ảnh cho chủ đề thì lưới sẽ đẹp hơn. |
| Test chập chờn | `NotebookViewModelTest.createShowsValidationErrorsAndKeepsDialogOpen` thỉnh thoảng lỗi khi chạy cả bộ: `create()` gán `_dialog = null` trước `_message`, test đọc `message` ngay khi thấy dialog đóng. Chạy lại riêng thì qua. Đề xuất: gán `_message` trước khi đóng dialog. |
| "Tìm gần đây" | Chỉ giữ trong bộ nhớ của `HomeViewModel` (mất khi tắt app). Muốn giữ lâu thì cần DataStore. |

## Huân (auth, profile, progress)

| Việc | Chi tiết |
|---|---|
| Review commit | `refactor(profile,progress): redesign profile and progress`, `refactor(auth): apply the design system to sign-in, reset and register` |
| Thay đổi điều hướng | Tab Hồ sơ bỏ hai tab con: thẻ hồ sơ + Tiến độ trên một trang. Sửa hồ sơ và Cài đặt là màn riêng; Xóa hồ sơ và Đăng xuất chuyển vào Cài đặt, đều có hộp thoại xác nhận. `ProfileScreenTest` đã cập nhật theo giao diện mới, hành vi giữ nguyên. |
| Trường hiển thị mới | `ProgressState`: `monthlyMinutes`, `activeDays7`, `quizCount`, `goalMinutes`, `rangeDays`, `isEmpty`, `chartMinutes` (test: `ProgressDisplayTest`). `ProgressViewModel` đọc thêm mục tiêu ngày từ `ProfileRepository`. |
| Lệch SRS | Validator cho phép trình độ **C1, C2**, trong khi bài kiểm tra chỉ gợi ý A1–B2 và nội dung chỉ có A1–B1. Giao diện giữ đủ 6 lựa chọn; cần nhóm thống nhất có giữ C1/C2 không. |
| Mục tiêu ngày | Chip gợi ý 10/15/30/45/60 phút; validator vẫn là 5–120. Giá trị khác (ví dụ 90) vẫn hiện thành chip riêng. |
| Đăng nhập Google | Nút "(sắp có)" đã ẩn. Khi làm xong thì thêm lại dưới nút Đăng nhập. |
| Lỗi Firebase | Thông báo lỗi vẫn là `localizedMessage` tiếng Anh của Firebase; nên đổi mã lỗi sang câu tiếng Việt trong `AuthViewModel`. |

## Đông (grammar, quiz, reminder, goals, giao diện chung)

- Màn **Mục tiêu học mỗi ngày** (Figma 20) chưa làm: mục tiêu đang chỉnh trong Sửa hồ sơ và hiển thị ở Trang chủ, Tiến độ.
- Tách khối bài lý thuyết dựa vào nhãn trong nội dung ("Cấu trúc:", "Cách dùng:", "Dấu hiệu:", "Lưu ý:"…). Bài mới nên giữ quy ước này.
- Nhận dạng giọng nói vẫn dùng bộ có sẵn của Android: chấm theo từ, chưa chỉ ra âm sai trong từ.

## Chưa kiểm tra

- Ảnh chụp tự kiểm tra chỉ làm ở 412dp chế độ sáng, tài khoản đã có dữ liệu. Chế độ tối, khổ 360dp, chữ 130% và trạng thái
  trống của tài khoản mới chưa chụp (theo yêu cầu bỏ bớt); các màn đều dùng token màu và cuộn được nên dự kiến không vỡ, nhưng nên
  xem lại trên máy thật.
