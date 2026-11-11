# EngMate

Ứng dụng Android tự học tiếng Anh, tập trung vào từ vựng, flashcard SM-2,
ngữ pháp, quiz và thói quen học mỗi ngày.

## Trạng thái

Repository hiện chứa **khung thư mục và tài liệu kiến trúc**. Chưa khởi tạo
Android/Gradle, chưa có mã chức năng, chưa thể build hoặc chạy ứng dụng.
Các file `.gitkeep` giúp Git lưu những thư mục chưa có mã nguồn; xóa chúng
khi thư mục có file triển khai thực tế.

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

## Bước triển khai tiếp theo

1. Khởi tạo Android project và Gradle Wrapper; xác nhận applicationId
   (`com.engmate.app` hiện là package dự kiến).
2. Thiết lập Compose, Hilt, Navigation và theme theo Figma.
3. Thống nhất ERD rồi triển khai Room, migrations và dữ liệu mẫu.
4. Cấu hình Firebase project và triển khai xác thực, hồ sơ.
5. Phát triển từng chức năng, bổ sung kiểm thử tương ứng.

Khi cấu hình Firebase, đặt file cấu hình môi trường tại
`app/google-services.json`; file này được bỏ qua trong Git để nhóm chủ động
chọn môi trường. Không đưa service-account key vào ứng dụng Android.
