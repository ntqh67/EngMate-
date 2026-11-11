# ADR-0001: Một module Android, tổ chức theo chức năng, MVVM + Repository

- Trạng thái: Chấp nhận làm nền tảng triển khai.
- Ngày: 2026-11-11.
- Phạm vi: Ứng dụng Android EngMate; giai đoạn đồ án sáu tuần, nhóm ba người.
- Cơ sở: tài liệu đề tài/SRS, danh sách 20 màn hình và phân công Huân–Khang–Đông
  do nhóm cung cấp trong phiên làm việc.

## Bối cảnh

EngMate có các nhóm chức năng tương đối độc lập: tài khoản và tiến độ;
từ vựng và ôn tập; ngữ pháp, quiz và nhắc học. Mỗi thành viên phụ trách cả
giao diện, nghiệp vụ, dữ liệu và kiểm thử của phần mình.

Ứng dụng cần hỗ trợ học nội dung đã có trên thiết bị khi mất mạng, dùng
Firebase Authentication cho tài khoản và gọi dịch vụ từ điển trực tuyến.
Nhóm cần cấu trúc dễ tìm mã nguồn, dễ tích hợp trong sáu tuần, đồng thời
giữ được khả năng kiểm thử logic như SM-2, streak và chấm quiz.

## Quyết định

### 1. Dùng một Gradle module `app`, chia package theo chức năng

Mỗi chức năng đặt dưới `feature/<ten-chuc-nang>`. Trong chức năng, tách
`presentation`, `domain`, `data` theo nhu cầu thực tế. Không tạo sẵn lớp
rỗng hoặc interface chỉ để hoàn thành sơ đồ kiến trúc.

Một module giúp cấu hình Gradle, dependency và Hilt đơn giản. Chia package
theo chức năng giúp thành viên tìm đầy đủ mã phần mình tại một nơi, giảm
việc cùng sửa các thư mục tổng hợp lớn như `screens/` hay `viewmodels/`.

Đánh đổi: package không cưỡng chế ranh giới ở bước biên dịch như Gradle
module. Nhóm phải giữ ranh giới bằng review. Nếu quy mô tăng hoặc xuất hiện
nhu cầu tái sử dụng độc lập, có thể tách module sau.

### 2. Áp dụng MVVM và luồng trạng thái một chiều

```text
Screen -- sự kiện --> ViewModel -- gọi --> Repository interface
   ^                     |                         ^
   |                     |                         |
   +------ UiState ------+                 Repository implementation
                                                   |
                                    Room / Firebase / API / DataStore
```

- Screen hiển thị trạng thái và gửi sự kiện; không trực tiếp gọi Firebase,
  DAO hay Retrofit.
- ViewModel kiểm tra đầu vào, điều phối tác vụ và cung cấp UiState bất biến
  qua StateFlow. UI thu thập trạng thái theo vòng đời.
- Domain chứa model nghiệp vụ, Repository interface và logic Kotlin độc
  lập khi cần. Không phụ thuộc Compose, Firebase, Room hoặc Retrofit.
- Data triển khai Repository, xử lý nguồn dữ liệu và ánh xạ Entity/DTO/đối
  tượng Firebase sang model nghiệp vụ. Không đưa các kiểu SDK lên UI.
- Repository không phụ thuộc ViewModel hoặc Screen.

Luồng này giúp UI nhất quán khi tải/lỗi/thành công và cho phép kiểm thử
ViewModel bằng Repository giả mà không cần kết nối Firebase.

Không bắt buộc một UseCase cho từng thao tác CRUD. Chỉ tách UseCase hoặc
dịch vụ nghiệp vụ khi logic phức tạp, cần tái sử dụng hay kiểm thử độc lập,
ví dụ tính lịch SM-2, cộng XP, tính streak hoặc hoàn tất phiên học.

### 3. Tách xác thực khỏi hồ sơ cá nhân

`auth` cung cấp AuthRepository; FirebaseAuthRepository thực hiện các thao tác
đăng ký, đăng nhập, đăng xuất, xác thực email và đặt lại mật khẩu.
`profile` quản lý họ tên, ảnh, trình độ và mục tiêu theo UID Firebase.

Không có cột `password` trong UserProfileEntity; không lưu mật khẩu vào
Room, DataStore hoặc Firestore. Trạng thái phiên do Firebase Auth quản lý;
không dùng một cờ `isLoggedIn` tự lưu làm căn cứ xác thực.

Tạo tài khoản và tạo hồ sơ là hai thao tác riêng, không phải một transaction.
Nếu Firebase tạo tài khoản thành công nhưng lưu hồ sơ thất bại, lần tiếp theo
cần hoàn tất hoặc tạo lại hồ sơ theo UID một cách idempotent; không đăng ký
lại tài khoản. Chỉ điều hướng vào trạng thái sử dụng đầy đủ khi các điều kiện
về hồ sơ/xác thực email đã được đáp ứng theo yêu cầu sản phẩm.

AuthRepository trả về model AuthUser tối thiểu. ProfileRepository chịu trách
nhiệm dữ liệu hồ sơ. ViewModel hoặc UseCase điều phối hai Repository qua
interface, không gọi trực tiếp triển khai của chức năng khác.

### 4. Dữ liệu học được lưu cục bộ, đồng bộ là phần mở rộng

Room là nguồn dữ liệu cho nội dung đã tải, sổ tay, lịch ôn và tiến độ cục bộ.
Entity và DAO đặt trong `feature/*/data/local`; EngMateDatabase, type
converter chung và migrations nằm tại `core/database` để thống nhất schema.

DataStore chứa cài đặt nhỏ. Cài đặt theo tài khoản phải được phân biệt bằng
UID hoặc khôi phục đúng khi đổi tài khoản. Dữ liệu học cá nhân cũng phải
truy vấn theo UID; đăng xuất phải xóa trạng thái UI và tác vụ liên quan đến
phiên cũ, tránh hiển thị dữ liệu người trước.

Tra từ trực tuyến đi qua `dictionary/data/remote`; kết quả cần dùng offline
được chuyển về dữ liệu cục bộ theo chính sách cache khi triển khai. Mất mạng
không ngăn việc ôn những từ đã lưu; đăng ký, đăng nhập mới và tra từ chưa
cache vẫn cần mạng.

Firestore sync chưa nằm trong khung này. Khi bổ sung phải xác định quy tắc
xung đột, retry, xóa dữ liệu và quyền truy cập theo UID; tránh để UI ghi trực
tiếp cả Room lẫn Firestore.

### 5. Giữ phần dùng chung nhỏ và có trách nhiệm rõ

- `core/ui/theme`: màu, typography, shape theo Figma.
- `core/ui/components`: thành phần thực sự dùng lại giữa các màn hình.
- `core/database`: cấu hình Room chung và nâng cấp schema.
- `core/datastore`: cơ chế lưu cài đặt dùng chung.
- `core/audio`: hỗ trợ phát âm TTS và quản lý tài nguyên âm thanh.
- `di`: các Hilt module cung cấp SDK, database và binding Repository.
- `navigation`: khai báo route và nối các màn hình; truyền ID thay vì Entity
  hay đối tượng SDK giữa các màn hình.

`core` không phải nơi gom mọi helper hoặc nghiệp vụ. Logic chỉ dùng cho
flashcard nằm trong flashcard; logic quiz nằm trong quiz. Chức năng cần dữ
liệu của chức năng khác dùng public domain interface, không truy cập DAO
hay Repository implementation của nhau. Tránh phụ thuộc vòng.

`home` tổng hợp dữ liệu qua các Repository hiện có; chưa cần Repository
lưu trữ riêng. `progress` chịu trách nhiệm quy tắc ghi nhận hoạt động,
XP và streak; các luồng hoàn tất quiz/flashcard cần phối hợp qua interface
và thiết kế tính nguyên tử/idempotent khi triển khai để tránh cộng trùng.

### 6. Sắp xếp theo nghiệp vụ, phân công qua tài liệu

Tên package không mang tên thành viên. Huân phụ trách auth/profile/progress;
Khang phụ trách home/vocabulary/flashcard/dictionary/notebook; Đông phụ trách
grammar/quiz/reminder/goals. Thành viên có thể phối hợp trên cùng chức năng.

Review Summary của phiên flashcard thuộc `flashcard/presentation/summary`,
giao diện do Đông phối hợp với Khang thực hiện. Kết quả quiz thuộc
`quiz/presentation/result`. Kiểm tra trình độ, luyện phát âm, admin, bộ thẻ
cá nhân và bảng xếp hạng sẽ được bổ sung khi phạm vi được thống nhất.

## Phương án đã cân nhắc

| Phương án | Lý do chưa chọn |
| --- | --- |
| Chia toàn app thành các thư mục `screens`, `viewmodels`, `repositories` | Mã của một chức năng bị phân tán, khó giao ownership theo module nghiệp vụ |
| Tách ngay nhiều Gradle module theo feature/layer | Tăng cấu hình và công việc tích hợp so với nhóm ba người trong sáu tuần |
| Gọi Firebase/Room trực tiếp từ Composable | Trộn UI và dữ liệu, khó quản lý vòng đời và kiểm thử |
| Clean Architecture với UseCase cho mọi thao tác | Thêm nhiều lớp chuyển tiếp cho nghiệp vụ đơn giản; chỉ áp dụng khi có lợi ích cụ thể |
| Chỉ lưu dữ liệu học trực tuyến | Không đáp ứng yêu cầu học nội dung đã lưu khi mất mạng |

## Hệ quả và cách kiểm chứng

Lợi ích: rõ trách nhiệm, phù hợp phân công theo chiều dọc, tái sử dụng theme
và thành phần UI, có thể kiểm thử nghiệp vụ độc lập với SDK.

Chi phí: cần mapping giữa model và Entity/DTO; phải phối hợp thay đổi database,
navigation và dependency dùng chung; ranh giới package cần review thủ công.

Khi có mã triển khai:

- `src/test`: kiểm thử SM-2, streak, chấm quiz, validation và ViewModel với fake.
- `src/androidTest`: kiểm thử Room/migration và các luồng Compose quan trọng.
- Kiểm tra offline, đổi tài khoản, phục hồi khi lưu hồ sơ lỗi và tránh cộng XP
  hai lần khi hoàn tất bài học.

Ở commit khung này chỉ kiểm tra cây thư mục, tài liệu và Git; không tuyên bố
build/test Android thành công vì chưa có project Gradle.

## Các điểm chưa quyết định

- Package `com.engmate.app` là đề xuất; cần xác nhận trước khi đăng ký app Firebase.
- Figma có cả 360×800 và 412×917; sẽ thống nhất bố cục thích ứng khi triển khai UI.
- Mã F/E và phạm vi kiểm tra trình độ/đồng bộ trong SRS cần thống nhất lại.
- ERD cần bỏ password, sửa phonetic bị lặp và rà soát khóa/quan hệ trước khi tạo Entity.
- Nhắc học dùng WorkManager không đồng nghĩa đảm bảo đúng phút; cơ chế lập lịch
  và mức chính xác cần được chốt khi triển khai yêu cầu nhắc học.

ADR này ghi lại quyết định cấu trúc ban đầu. Nếu thay đổi đáng kể, tạo ADR mới
và liên kết tài liệu thay thế thay vì xóa lịch sử quyết định.
