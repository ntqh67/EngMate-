# Bộ thiết kế EngMate

Nguồn chuẩn: Figma "EngMate · Brand + Đông" (bảng màu Pastel Sky & Mango, thang chữ, icon). Mã nguồn:
`core/ui/theme` (token) và `core/ui/components` (component, mỗi cái có Preview sáng/tối trong `ComponentPreviews.kt`).
Đặc tả từng màn: [ui-spec.md](ui-spec.md).

## 1. Màu

Luôn lấy màu từ `MaterialTheme.colorScheme` hoặc `EngMateTheme.brand`; không viết mã màu trong màn hình.

| Vai trò | Sáng | Tối | Dùng cho |
|---|---|---|---|
| `primary` / `onPrimary` | #2C71B7 / #FFFFFF | #A7CEFB / #03294F | nút chính, liên kết, tiến độ |
| `primaryContainer` | #E1F0FF / #03294F | #0D4172 / #E1F0FF | chip đang chọn, nút tonal, mặt sau flashcard |
| `secondary` / `secondaryContainer` | #A24E09 / #FFE1D1 | #FC9C62 / #612C04 | khối Lưu ý, chuỗi ngày (chữ) |
| `tertiary` / `tertiaryContainer` | #694CCD / #E6E5FE | #CECAFF / #3E2586 | XP, huy hiệu |
| `background` | #F3F9FF | #101419 | nền màn |
| `surface`, `surfaceContainerLow` | #FFFFFF | #101419, #181C22 | thẻ, nhóm nội dung |
| `surfaceContainerHigh` | #E1EAF3 | #252A31 | rãnh tiến độ, ô tìm, chip phụ |
| `onSurface` / `onSurfaceVariant` | #101419 / #3E4349 | #EBF1F8 / #CBD2D9 | chữ chính / chữ phụ |
| `outline` / `outlineVariant` | #757B81 / #DDE4EB | #9FA5AC / #3E4349 | viền ô nhập / divider |
| `error` / `errorContainer` | #BA2D3A / #FDE8E9 | #F2A0A7 / #7A1520 | sai, xoá |
| `brand` / `onBrand` | #A7CEFB / #0D4172 | #0D4172 / #E1F0FF | khối hero (một khối mỗi màn) |
| `accent` / `onAccent` | #F2843A / #441C00 | như sáng | chuỗi ngày, năng lượng — **không chữ trắng trên cam** |
| success (`SemanticColors.kt`) | #1E8450, nền #E6F6EC | #6DD58C, nền #0B4F2C | đúng, đã thuộc |

Ghi chú: nút tonal mặc định của Material lấy `secondaryContainer` (cam); dùng `TonalButton` để có nền xanh.

## 2. Chữ

| Vai trò Material | Font | Cỡ/dòng |
|---|---|---|
| display, headline | Nunito Bold–ExtraBold | 57/64 … 24/32 |
| title | Be Vietnam Pro SemiBold | 22/30, 16/24, 14/20 |
| body | Be Vietnam Pro Regular | 16/26, 14/22, 12/18 |
| label | Be Vietnam Pro SemiBold/Medium | 14/20, 12/16, 11/16 |
| `EngMateTheme.type.stat` | Nunito ExtraBold | 40/48 — số trên vòng điểm |
| `EngMateTheme.type.vocabulary` | Nunito ExtraBold | 32/40 — từ vựng |
| `EngMateTheme.type.ipa` | font hệ thống | 16/24 — phiên âm (đủ ký hiệu IPA) |

Dòng cao hơn Material khoảng 2sp để dấu tiếng Việt không chạm nhau. Font nằm trong `res/font` (Nunito bản variable, Be Vietnam
Pro 3 độ đậm); giấy phép OFL trong `assets/licenses`.

## 3. Khoảng cách, bo góc, tầng

- Spacing (`Spacing`): 4 · 8 · 12 · 16 · 24 · 32; vùng chạm tối thiểu 48dp; nút chính/phụ 52dp.
- Lề màn (`screenPadding`): 16dp, 20dp từ 412dp.
- Bo góc: 8 (small) · 12 (medium) · 16 (large, thẻ) · 28 (extraLarge, nút, ô tìm, segmented).
- Tầng: nền xanh nhạt → thẻ trắng (`surfaceContainerLow`) → vùng lồng `surfaceContainerHigh`. Không viền quanh mọi thẻ, không bóng đậm.

## 4. Icon

Bộ nét 2px bo tròn theo Figma (Lucide, giấy phép ISC trong `assets/licenses`), file `res/drawable/ic_*.xml`. Không dùng emoji
hay ký tự (✓ ✎ ›) làm icon. Icon có nghĩa phải có `contentDescription`; icon trang trí để `null`.

## 5. Component (`core/ui/components`)

| Nhóm | Component |
|---|---|
| Khung | `AppTopBar`, `TopBarAction`, `AppSegmentedTabs`, `AppSearchField`, `AppDialog`, `ConfirmDialog`, `AppBottomSheet` |
| Nút | `PrimaryButton`, `SecondaryButton`, `TonalButton`, `ButtonPair`, `StickyBottomBar` |
| Nội dung | `SectionHeader` (có "Xem tất cả"), `HeroCard`, `InfoCard`, `SurfaceGroup` + `GroupDivider`, `StatCard`, `ListRow` + `StatusTag`, `ShortcutTile`, `ProgressRing`, `LinearProgressWithLabel`, `SegmentedProgress`, `LevelChip`, `ChipRow`, `IpaText` |
| Động lực | `StreakChip`, `XpChip` |
| Trạng thái | `EmptyState`, `ErrorState`, `LoadingState` (skeleton), `SkeletonBlock`, `FeedbackBanner` |

Các hàm cũ (`BackTopBar`, `LevelBadge`, `ConfirmDeleteDialog`, `ChipRow`, `MessageEffect`) giữ nguyên chữ ký và gọi sang component mới.

## 6. Nguyên tắc bố cục

1. Mỗi màn có **một** khối nổi bật (hero) và **một** nút chính; nút chính ở `StickyBottomBar` khi màn có hành động kết thúc.
2. Khoảng trắng có mục đích: không để nửa dưới màn trống vô nghĩa; nội dung ngắn thì đưa nút chính xuống đáy.
3. Vòng lặp động lực nhất quán: Mục tiêu hôm nay, Chuỗi ngày học, XP, trạng thái từng bài; ăn mừng nhẹ khi hoàn thành.
4. Gom nội dung liên quan vào một `SurfaceGroup`, ngăn bằng divider; không lồng thẻ trong thẻ.
5. Trạng thái trống luôn có một câu hướng dẫn và một nút dẫn tới bước tiếp theo.
6. Một cấp chọn: không có hai tầng tab cho cùng một việc.
7. Đúng/sai luôn có icon kèm màu; tương phản chữ ≥ 4.5:1.
8. Chuyển động 150–300ms, tắt khi máy bật "Xoá hiệu ứng động" (`rememberReduceMotion()`).

## 7. Thuật ngữ

| Dùng | Không dùng |
|---|---|
| Sổ tay | Vocab Handbook |
| Ôn tập, Ôn tập hôm nay | Review, ôn flashcard |
| Bài tập, Làm bài tập | Quiz, Làm quiz (trên nút) |
| Kiểm tra trình độ | Placement test |
| Mục tiêu hôm nay, Mục tiêu mỗi ngày | Daily goal |
| Chuỗi ngày học | Streak |
| XP | điểm kinh nghiệm |
| Hủy | Huỷ |

Viết hoa chữ đầu câu, không viết IN HOA cho nhãn hay nút.
