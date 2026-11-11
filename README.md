# EngMate

Ứng dụng Android tự học tiếng Anh, tập trung vào từ vựng, flashcard SM-2,
ngữ pháp, quiz và thói quen học mỗi ngày.

## Trạng thái

Đã có project Android chạy được và CRUD hồ sơ cá nhân: tạo, xem, sửa, xóa
hồ sơ cục bộ bằng Room, qua Repository và ViewModel. Giao diện Compose có
validation, trạng thái tải/lỗi và xác nhận xóa. Các chức năng khác vẫn là khung.

Có hai biến thể: **demo** chạy ngay với tài khoản cục bộ minh họa; **live** lấy
UID từ phiên Firebase Auth, không tự đăng nhập và không dùng tài khoản demo.
Xem [hướng dẫn CRUD hồ sơ](docs/profile-crud.md) để chạy và tích hợp Firebase.

Các file `.gitkeep` còn lại giữ những thư mục chưa có mã nguồn trong Git.

## Định hướng kỹ thuật

- Android Native, Kotlin, Jetpack Compose + Material 3; minSdk dự kiến 26.
- Một Gradle module `app`, tổ chức package theo chức năng.
- MVVM + Repository; Hilt cung cấp dependency, Navigation Compose điều hướng.
- Room lưu dữ liệu học; DataStore lưu cài đặt; Firebase Authentication xác thực.
- Retrofit/OkHttp gọi từ điển; TTS phát âm; WorkManager phục vụ nhắc học.
- Firestore và các tính năng mở rộng được triển khai sau phần cốt lõi.

Lý do, ranh giới phụ thuộc và đánh đổi: [ADR-0001](docs/adr/0001-android-architecture.md).

## Cây thư mục

```text
app/
└── src/
    ├── main/
    │   ├── java/com/engmate/app/
    │   │   ├── di/
    │   │   ├── navigation/
    │   │   ├── core/
    │   │   │   ├── ui/
    │   │   │   │   ├── theme/
    │   │   │   │   └── components/
    │   │   │   ├── database/migrations/
    │   │   │   ├── datastore/
    │   │   │   └── audio/
    │   │   └── feature/
    │   │       ├── auth/
    │   │       ├── profile/
    │   │       ├── progress/
    │   │       ├── home/
    │   │       ├── vocabulary/
    │   │       ├── flashcard/
    │   │       ├── dictionary/
    │   │       ├── notebook/
    │   │       ├── grammar/
    │   │       ├── quiz/
    │   │       ├── reminder/
    │   │       └── goals/
    │   ├── assets/
    │   └── res/
    │       ├── drawable/
    │       ├── font/
    │       └── values/
    ├── test/java/com/engmate/app/
    └── androidTest/java/com/engmate/app/
docs/
└── adr/
    └── 0001-android-architecture.md
```

Mẫu tổ chức một chức năng có dữ liệu Room:

```text
feature/profile/
├── data/
│   └── local/        # Entity, DAO
├── domain/          # Model nghiệp vụ, Repository interface
└── presentation/    # Screen, ViewModel, UiState
```

`auth/data` sẽ chứa triển khai Firebase Auth; `dictionary/data/remote` chứa
API service và DTO. Chỉ tạo lớp lưu trữ khi chức năng cần: `home` dùng các
Repository hiện có để tổng hợp dữ liệu, không có kho dữ liệu riêng.

## Phân công

| Thành viên | Chức năng | Trách nhiệm chung |
| --- | --- | --- |
| Huân | `auth`, `profile`, `progress` | Khung ứng dụng, Hilt, điều hướng, Firebase, review và tích hợp |
| Khang | `home`, `vocabulary`, `flashcard`, `dictionary`, `notebook` | ERD, Room, migrations, dữ liệu mẫu |
| Đông | `grammar`, `quiz`, `reminder`, `goals` | Theme/component, Figma, điều phối kiểm thử |

Màn hình Review Summary thuộc phần giao diện do Đông phụ trách; khi triển khai
sẽ đặt trong `flashcard/presentation/summary`, phối hợp với Khang về kết quả
phiên ôn. Đây là màn hình tổng kết flashcard, khác với kết quả quiz.

Mỗi thành viên làm việc trên nhánh `feat/<ten-chuc-nang>`, tạo Pull Request
vào `main`. Thay đổi database, điều hướng và dependency dùng chung cần được
phối hợp trong nhóm trước khi tích hợp.

## Chạy và kiểm thử

Mở repository bằng Android Studio hỗ trợ AGP 9.3.3, cài Android SDK 37 và
Build Tools 36.0.0. Gradle Wrapper 9.5.0 được commit trong repo; Gradle cần
JDK 17 trở lên tương thích Gradle 9.5 (môi trường đã kiểm tra dùng JDK 25).
Chọn build variant `demoDebug`, rồi Run trên Android 8.0 trở lên.

```bash
./gradlew :app:assembleDemoDebug
./gradlew :app:testDemoDebugUnitTest :app:lintDemoDebug
./gradlew :app:assembleLiveDebug
```

APK demo: `app/build/outputs/apk/demo/debug/app-demo-debug.apk`.
Hướng dẫn chi tiết và giới hạn: [CRUD hồ sơ](docs/profile-crud.md).

## Bước triển khai tiếp theo

1. Cấu hình Firebase cho `com.engmate.app`, triển khai màn hình đăng ký/đăng nhập.
2. Nối đăng ký thành công vào luồng tạo hồ sơ; bổ sung xác thực email theo SRS.
3. Mở rộng Room cho từ vựng, tiến độ, quiz và thêm migrations từ schema v1.
4. Tích hợp Navigation và các chức năng còn lại, hoàn thiện giao diện theo Figma.

`app/google-services.json` và `local.properties` được bỏ qua trong Git.
Không đưa service-account key vào ứng dụng Android.
