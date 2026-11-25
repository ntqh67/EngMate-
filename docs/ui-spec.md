# Đặc tả giao diện EngMate

Trạng thái: **bản nháp chờ duyệt**. Nguồn chuẩn hình ảnh: Figma "EngMate · Brand + Đông" (bảng màu Pastel Sky & Mango,
thang chữ, icon, mockup 14–20, E01, E05). Tài liệu này mô tả bố cục và hành vi; token cụ thể nằm ở `docs/design-system.md`.

## 0. Nguyên tắc dùng cho mọi màn

- **Một hero, một nút chính.** Mỗi màn có một khối nổi bật cho việc quan trọng nhất và tối đa một nút chính; nút chính
  nằm ở `StickyBottomBar` khi màn có hành động kết thúc (lưu, làm bài, tiếp tục).
- **Khoảng trắng có mục đích.** Không để nửa dưới màn trống: nội dung ngắn thì nút chính xuống đáy, phần còn lại lấp
  bằng gợi ý hoặc hoạt động gần đây có thật.
- **Vòng lặp động lực nhất quán:** Mục tiêu hôm nay (phút), Chuỗi ngày học, XP, trạng thái trên từng bài/chủ đề, ăn mừng nhẹ khi xong.
- **Ít khung lồng khung.** Gom nội dung liên quan vào một bề mặt `surfaceContainerLow`, ngăn bằng divider; không viền
  quanh mọi thẻ.
- **Trạng thái trống luôn có bước tiếp theo** (một câu hướng dẫn + một nút).
- **Một cấp chọn.** Không có hai tầng tab cho cùng một việc.
- **Màn toàn trang** (chi tiết bài, quiz, chi tiết từ, ôn tập, cài đặt, nhắc học) ẩn thanh điều hướng dưới.
- Mọi màn: phím Back hệ thống luôn hoạt động; nội dung không bị thanh hệ thống/bàn phím che; vùng chạm ≥ 48dp.

Ký hiệu trong sơ đồ: `[Nút]` là nút, `(◯)` là vòng tiến độ, `▓░` là thanh tiến độ, `›` là mở trang khác. Icon trong
app là vector, không phải emoji.

### Thanh điều hướng dưới

4 mục giữ nguyên: Trang chủ · Học · Sổ tay · Hồ sơ. Mục đang chọn: chỉ báo `primaryContainer`, icon **và** nhãn cùng
màu `onPrimaryContainer` (sửa lỗi nhãn cam, icon xanh hiện nay).

### Thuật ngữ

Sổ tay (bỏ "Vocab Handbook") · Ôn tập (phiên flashcard) · Bài tập (quiz của một bài) · Kiểm tra trình độ ·
Mục tiêu hôm nay · Chuỗi ngày học · XP. Nút làm quiz luôn là "Làm bài tập".

---

## 1. Trang chủ (Khang)

**Mục đích:** trả lời "hôm nay mình nên học gì" trong 3 giây. **Hành động chính:** bấm vào việc nên làm ngay
(ôn từ đến hạn hoặc tiếp tục bài).

```
┌────────────────────────────────────┐
│ Chào buổi chiều,            [🔍][🔔]│ ← tiêu đề Nunito; 🔍 mở Tra từ
│ Eastern                             │
│ ┌────────────────────────────────┐ │
│ │ (◯ 10/15') Mục tiêu hôm nay    │ │ ← HeroCard nền brand #A7CEFB
│ │  Còn 5 phút nữa       [Học ngay]│ │
│ │  [🔥 6 ngày]  [⚡ 120 XP]        │ │ ← StreakChip (cam), XpChip (iris)
│ └────────────────────────────────┘ │
│ ┌───────────────┐┌───────────────┐ │
│ │ 12 từ cần ôn  ││ Tiếp tục       │ │ ← 2 thẻ việc nên làm, cùng chiều cao
│ │ đến hạn hôm nay││ Hiện tại đơn   │ │
│ │ [Ôn ngay]     ││ Điểm tốt nhất  │ │
│ │               ││ 80% ▓▓▓▓░     │ │
│ └───────────────┘└───────────────┘ │
│ Học nhanh                           │
│ [Ôn tập ][Ngữ pháp]                 │ ← lưới 2×2, icon + nhãn
│ [Từ vựng][Kiểm tra trình độ]        │
│ Gợi ý cho trình độ A2        Xem › │
│  ▸ Thì quá khứ đơn       A2  Mới    │ ← ListRow, tối đa 3
│  ▸ So sánh hơn           A2  Mới    │
│ Hoạt động gần đây            Xem › │
│  ✓ Hiện tại đơn · 8/10 · +80 XP     │ ← 3 lượt làm mới nhất
└────────────────────────────────────┘
```

| Khối | Nội dung / component | Nguồn dữ liệu | Khi chưa có dữ liệu |
|---|---|---|---|
| Lời chào | `greeting()` + tên; icon Tra từ | `ProfileRepository.observe` | Khách: "Chào bạn" |
| Mục tiêu hôm nay | `HeroCard` + `ProgressRing` phút hôm nay / `dailyGoalMinutes`, `StreakChip`, `XpChip` (tổng XP) | `StudyDao.observeAllStats` (studySeconds hôm nay), hồ sơ | Chưa có hồ sơ → hero chào mừng (xem dưới). 0 phút → vòng rỗng, "Bắt đầu 5 phút đầu tiên" |
| Từ cần ôn | `InfoCard` số từ + `[Ôn ngay]` mở Ôn tập từ đến hạn | `ReviewRepository.observeDueCount` | 0 từ → thẻ đổi thành "Không có từ đến hạn · Thêm từ vào Sổ tay ›" |
| Tiếp tục | `InfoCard`: bài mở gần nhất, điểm tốt nhất + thanh | hoạt động GRAMMAR gần nhất (`StudyDao.observeActivities`, sourceId = lessonId); điểm tốt nhất từ `observeHistory` theo `lessonId` | Chưa mở bài nào → bài tiếp theo chưa làm (theo `lessonId`, sửa lỗi so theo tên) |
| Học nhanh | 4 ô `ShortcutTile` | điều hướng có sẵn | luôn hiện |
| Gợi ý theo trình độ | `SectionHeader` + 3 `ListRow` bài cùng `englishLevel`, chưa làm trước | bài ngữ pháp + hồ sơ + lịch sử | không có hồ sơ → ẩn; hết bài cùng trình độ → ẩn |
| Hoạt động gần đây | 3 `ListRow` lượt làm; bấm mở Kết quả | `observeHistory` | chưa có lượt → ẩn khối |

- **Lần đầu (đã đăng nhập, chưa có hoạt động):** HeroCard "Chào mừng đến EngMate" + `[Làm bài kiểm tra trình độ]`
  (chính) và `[Bắt đầu bài đầu tiên]` (phụ). Ẩn Từ cần ôn, Tiếp tục, Hoạt động gần đây.
- **Khách:** HeroCard "Học thử miễn phí · Đăng nhập để lưu tiến độ và chuỗi ngày học" + `[Đăng nhập]`; giữ Học nhanh
  (Ôn tập/Kiểm tra trình độ yêu cầu đăng nhập như hiện nay) và danh sách bài.
- **Tải:** skeleton cho hero và 2 thẻ. **Lỗi:** `ErrorState` gọn trong vùng hero + `[Thử lại]`, phần còn lại vẫn hiện.
- **Phản hồi:** đạt mục tiêu ngày → vòng chuyển success, nhãn "Đã đạt mục tiêu hôm nay", chip nảy nhẹ một lần.
- **Khác với prompt:** lưới lối tắt là Ôn tập · Ngữ pháp · Từ vựng · Kiểm tra trình độ thay cho "Quiz", vì app không có
  quiz đứng riêng (quiz luôn thuộc một bài); ô Ngữ pháp đã dẫn tới đó.
- **Trường thêm vào `HomeUiState`** (có unit test): `todayMinutes`, `goalMinutes`, `streak`, `totalXp`, `dueCount`,
  `continueLesson` + `bestPercent`, `suggestions`, `recent`, `isFirstRun`.

## 2. Tra từ (Khang) — màn mới tách từ Trang chủ

**Mục đích:** tra một từ nhanh. **Hành động chính:** mở một kết quả. Logic giữ nguyên (`searchFlow`, `HomeViewModel.open`).

```
┌────────────────────────────────────┐
│ [←] [🔍 Tìm từ tiếng Anh…      ✕ ] │ ← AppSearchField, tự lấy tiêu điểm
│ Tìm gần đây                         │
│ [travel] [apple] [schedule]         │ ← ChipRow (chỉ trong phiên, không lưu DB)
│ ─────────────────────────────────── │
│ travel   /ˈtræv.əl/          verb  ›│ ← ListRow: từ, IPA, loại từ, nghĩa 1 dòng
│ đi du lịch                          │
│ traveller  …                       ›│
└────────────────────────────────────┘
```

- **Trống (chưa gõ):** minh hoạ nhỏ + "Gõ một từ tiếng Anh để xem nghĩa, phát âm và ví dụ".
- **Đang tìm:** thanh tiến độ mảnh dưới ô tìm. **Không thấy:** "Không tìm thấy “x”" + gợi ý kiểm tra chính tả.
- **Mất mạng:** `FeedbackBanner` "Không có kết nối Internet. Đang hiện từ đã lưu trong máy." + kết quả cục bộ.
- "Tìm gần đây" giữ trong ViewModel (bộ nhớ), không ghi CSDL.

## 3. Tab Học (Đông + Khang)

**Mục đích:** chọn thứ để học. Bỏ hai tầng tab: tiêu đề "Học" + `AppSegmentedTabs` (Ngữ pháp | Từ vựng) ngay dưới, cùng
một hàng tiêu đề, không có `TabRow` thứ hai và không lặp tiêu đề "Ngữ pháp".

### 3.1 Danh sách ngữ pháp (Đông)

**Hành động chính:** mở một bài.

```
┌────────────────────────────────────┐
│ Học                        [🔍]     │ ← 🔍 mở ô lọc bài (thay ô tìm luôn hiện)
│ [ Ngữ pháp | Từ vựng ]              │
│ Đã học 3/17 bài                     │
│ ▓▓▓░░░░░░░░░░░░░░░                  │ ← LinearProgressWithLabel
│ [Tất cả][A1][A2][B1]                │ ← ChipRow một hàng
│ Trình độ A1                         │
│ (A1) Hiện tại đơn (Present          │ ← tên tối đa 2 dòng, không cắt
│      Simple)                 ✓ 80% │ ← trạng thái phải
│      Thói quen, sự thật hiển nhiên  │ ← mô tả 1 dòng
│ ─────────────────────────────────── │
│ (A1) Hiện tại tiếp diễn       Mới  │
└────────────────────────────────────┘
```

- Mỗi bài là `ListRow`: huy hiệu trình độ có màu (A1 xanh nhạt, A2 xanh, B1 iris, B2 cam) thay icon quyển sách giống nhau;
  bỏ chip trình độ trùng ở cuối dòng. Nhóm theo trình độ trong **một** bề mặt, ngăn bằng divider.
- Trạng thái: `✓ 80%` (điểm tốt nhất, success khi ≥ 80%), `60%` (đã làm), `Mới` (chưa làm). Nguồn: `observeHistory`
  theo `lessonId`. Khách: không hiện trạng thái.
- Bài phù hợp trình độ hồ sơ có nhãn nhỏ "Phù hợp với bạn".
- **Tải:** skeleton 5 dòng. **Lỗi:** `ErrorState` + `[Thử lại]`. **Không có bài:** `EmptyState`. **Lọc không ra:**
  "Không có bài phù hợp" + `[Xoá bộ lọc]`.
- **Trường thêm vào `GrammarLessonsUiState`:** `bestPercentByLesson`, `doneCount`, `profileLevel` (test).

### 3.2 Từ vựng theo chủ đề (Khang)

```
│ [ Ngữ pháp | Từ vựng ]              │
│ 15 chủ đề · 446 từ                  │
│ ┌───────────────┐┌───────────────┐ │ ← lưới 2 cột
│ │ [icon] Công   ││ [icon] Công   │ │
│ │ nghệ          ││ việc          │ │
│ │ 31 từ · B1    ││ 29 từ · A2    │ │
│ └───────────────┘└───────────────┘ │
```

- Lưới 2 cột, icon theo chủ đề (từ bộ icon Figma), số từ + trình độ. Tiến độ từng chủ đề **chưa có nguồn** → không hiện,
  ghi vào `docs/ui-review-team.md`.
- **Danh sách từ của chủ đề:** `AppTopBar` tên chủ đề; `ListRow` từ (đậm) + IPA (Noto Sans/hệ thống) + nghĩa; nút loa
  bên phải phát âm nhanh. Trống: `EmptyState` "Chủ đề chưa có từ".

## 4. Bài lý thuyết (Đông)

**Mục đích:** hiểu một điểm ngữ pháp rồi luyện. **Hành động chính:** `[Làm bài tập (n câu)]` ở `StickyBottomBar`.
Bỏ 3 tab và bỏ thanh điều hướng dưới trên màn này.

```
┌────────────────────────────────────┐
│ [←]                         [🔖?]   │ ← không lặp tên bài trên thanh
│ (A1) · 2 phút đọc                   │
│ Hiện tại đơn                        │ ← headlineMedium Nunito
│ Present Simple                      │ ← phần trong ngoặc tách dòng phụ
│ Thói quen, sự thật hiển nhiên…      │
│ ┌ Công thức ─────────────────────┐ │ ← khung brand
│ │ Khẳng định  S + V(s/es)        │ │
│ │ Phủ định    S + do/does not + V│ │
│ │ Nghi vấn    Do/Does + S + V?   │ │
│ └────────────────────────────────┘ │
│ Cách dùng                           │
│ • Thói quen …                       │
│ Dấu hiệu nhận biết                  │
│ [always][usually][every day]        │ ← chip
│ Ví dụ                               │
│ She goes to school.           [🔊] │ ← Anh đậm, Việt nhạt, loa TTS có sẵn
│ Cô ấy đi học.                       │
│ ┌ Lưu ý ─────────────────────────┐ │ ← nền secondaryContainer + icon lightbulb
│ │ Quy tắc thêm -es: …            │ │
│ └────────────────────────────────┘ │
├────────────────────────────────────┤
│ [ Làm bài tập (10 câu) ]            │ ← StickyBottomBar
└────────────────────────────────────┘
```

- **Tách khối từ `content`** (hàm thuần trong presentation, có test): dòng `Nhãn:` mở một mục, dòng `•` thuộc mục đang mở.
  - "Cấu trúc", "Cấu trúc chung" → khối Công thức; dòng `• Khẳng định: …` tách nhãn/công thức.
  - "Cách dùng" → danh sách gạch đầu dòng. "Dấu hiệu" → chip (tách dấu phẩy).
  - "Lưu ý", "Mẹo", "Quy tắc …" → khối Lưu ý.
  - Nhãn khác ("So sánh", "Theo sau là V-ing", …) → mục có tiêu đề riêng.
  - Không nhận ra nhãn nào → hiện đoạn văn như cũ.
- Nút chính hiện số câu thật (`questionsForLesson().size`, tối đa 10); 0 câu → nút tắt, nhãn "Bài này chưa có bài tập".
- Khách bấm nút → mở đăng nhập (giữ hành vi cũ).
- Thời gian đọc: số từ / 150, tối thiểu 1 phút.
- **Tải:** skeleton. **Không còn bài:** `EmptyState` + `[Quay lại]`.

## 5. Quiz, Kết quả, Kiểm tra trình độ (Đông)

### 5.1 Làm bài

**Hành động chính:** Kiểm tra → Tiếp tục.

```
┌────────────────────────────────────┐
│ [✕] ▓▓▓▓▓░░░░░░░░░░░    3/10  ⚡30 │ ← đóng, thanh chia đoạn, câu x/y, XP phiên
│                                     │
│ Chọn đáp án đúng                    │ ← labelLarge, loại câu
│ She ___ to school every day.        │ ← headlineSmall
│                                     │
│          (khoảng thở)               │
│ ┌────────────────────────────────┐ │
│ │ A  go                          │ │ ← đáp án dồn xuống vùng ngón cái
│ │ B  goes                     ✓  │ │
│ │ C  going                       │ │
│ │ D  gone                        │ │
│ └────────────────────────────────┘ │
├────────────────────────────────────┤
│ ✓ Chính xác!            +10 XP      │ ← StickyBottomBar đổi màu success/error
│ Đáp án đúng: goes · Giải thích…  ˅  │ ← giải thích gập/mở, không đẩy nội dung
│ [ Tiếp tục ]                        │
└────────────────────────────────────┘
```

- Đáp án có ký tự A–D thay radio; đã chọn: viền primary 2dp + nền primaryContainer; sau kiểm tra: đúng = success +
  icon check, chọn sai = error + icon x. Điền từ: ô nhập lớn, bàn phím "Xong" = Kiểm tra.
- Trả lời đúng: `XpChip` "+10 XP" nảy nhẹ (scale 1→1.15→1, 250ms) + rung nhẹ (haptic `TextHandleMove`/`Confirm`); sai: rung
  `Reject` nếu máy hỗ trợ. Tắt chuyển động khi máy bật giảm chuyển động.
- XP phiên = 10 × số câu đúng đã kiểm tra (đúng công thức `QuizScoring.xp`).
- Lỗi lưu: `FeedbackBanner` error trên thanh đáy + `[Thử lại]` (và `[Tạo hồ sơ]` khi thiếu hồ sơ). Hộp thoại thoát giữ nguyên.
- Tải: skeleton câu hỏi. Lỗi tải / không đủ câu: `ErrorState`/`EmptyState` như cũ, giao diện mới.

### 5.2 Kết quả

**Hành động chính:** `[Xong]`; phụ: `[Làm lại]`.

```
┌────────────────────────────────────┐
│ [✕]                                 │
│          ( ◯ 8/10 )                 │ ← ProgressRing lớn, Nunito số
│          Tuyệt vời!                 │ ← lời theo mức điểm
│   Hiện tại đơn · Bài tập            │
│ ┌──────┐┌──────┐┌──────┐            │
│ │ +80  ││ 80%  ││ 🔥 6 │            │ ← XP · Độ chính xác · Chuỗi ngày
│ │ XP   ││chính ││ ngày │            │
│ └──────┘└──────┘└──────┘            │
│ Xem lại  [Tất cả 10][Câu sai 2][Đúng]│
│ ✗ Câu 3 · Bạn chọn: go · Đúng: goes │
├────────────────────────────────────┤
│ [Làm lại]          [ Xong ]         │
└────────────────────────────────────┘
```

- ≥ 80%: ăn mừng nhẹ (hạt confetti vẽ Canvas ~1.2s), tắt khi giảm chuyển động. Lời: ≥ 80% "Tuyệt vời!", 50–79% "Khá lắm!",
  < 50% "Cố thêm chút nữa nhé".
- Chuỗi ngày lấy từ nguồn tiến độ (cùng phép tính màn Tiến độ); không có → ẩn ô.
- Bộ lọc giữ logic cũ (mặc định Câu sai khi có).

### 5.3 Kiểm tra trình độ

- **Giới thiệu:** logo mark + minh hoạ huy hiệu A1–B2 quanh chữ E (theo Figma F04), tiêu đề "Tìm điểm xuất phát của bạn",
  3 dòng thông tin (15 câu · khoảng 10 phút · không trừ điểm); `StickyBottomBar` `[Bắt đầu]` + nút chữ "Để sau".
- **Làm bài:** dùng chung màn 5.1.
- **Kết quả:** huy hiệu trình độ lớn (vòng tròn brand, chữ B1 Nunito 45sp), "Bạn đúng 11/15 câu", một câu giải thích
  trình độ (A1 "Bạn nắm các cấu trúc cơ bản nhất"…), dòng xác nhận đã cập nhật hồ sơ (icon + màu); `[Xong]` chính,
  `[Xem chi tiết]` phụ.

## 6. Ôn tập (flashcard) — Khang, giao diện Đông

**Mục đích:** nhớ lại từ. **Hành động chính:** lật thẻ rồi chấm Đã thuộc/Chưa thuộc. Giữ 2 mức chấm (quyết định 4a).
Hai lối vào: Sổ tay (một sổ) và **"Ôn ngay" trên Trang chủ: các từ đến hạn** (`ReviewRepository.dueWords`, quyết định 3a).

```
┌────────────────────────────────────┐
│ [✕] ▓▓▓▓▓▓░░░░░░░░░    6/12 [⋮]    │ ← ⋮: Thẻ trước, Xáo trộn
│ ┌────────────────────────────────┐ │
│ │                                │ │
│ │   Định nghĩa                   │ │ ← mặt trước: gợi ý, che từ
│ │   máy ảnh                      │ │
│ │   “He bought a new _____.”     │ │
│ │                                │ │
│ │      Chạm để lật thẻ           │ │
│ └────────────────────────────────┘ │ ← lật rotationY 300ms
├────────────────────────────────────┤
│ [ Lật thẻ ]                         │ ← trước khi lật
│ [Chưa thuộc ✗]   [Đã thuộc ✓]       │ ← sau khi lật (error/success tonal)
└────────────────────────────────────┘
```

- Mặt sau: từ (Nunito lớn), IPA, loại từ, nút nghe, ghi chú của tôi. Thẻ chiếm phần giữa màn, nội dung căn giữa theo chiều dọc.
- **Tổng kết phiên:** `ProgressRing` đã thuộc/tổng, 2 `StatCard` (Đã thuộc, Chưa thuộc), lời khích lệ; `[Học lại thẻ chưa thuộc]`
  chính, `[Làm lại từ đầu]` phụ, nút chữ "Xong".
- **Trống:** sổ tay trống → `EmptyState` + `[Tra từ]`; không có từ đến hạn → "Bạn đã ôn hết từ đến hạn hôm nay" + `[Về Trang chủ]`.
- Thay đổi ViewModel: thêm `startDue()` dùng `dueWords()` có sẵn, tiêu đề "Ôn tập hôm nay"; chấm điểm và lưu SM-2 giữ nguyên.

## 7. Chi tiết từ và Luyện phát âm (Khang; chấm điểm: Đông)

**Mục đích:** hiểu và ghi nhớ một từ. **Hành động chính:** `[Thêm vào Sổ tay]` (đã lưu: `[Đã lưu vào Sổ tay ✓]`, bấm để quản lý).

```
┌────────────────────────────────────┐
│ [←]                        [Sửa]   │ ← một hành động Sửa (mở menu: nghĩa, ví dụ, cụm từ, ghi chú)
│ app                                 │ ← Nunito displaySmall
│ /æp/ · danh từ                      │ ← IPA Noto Sans/hệ thống
│ [🔊 Nghe]  [🎤 Luyện nói]           │
│ ứng dụng                            │ ← nghĩa Việt titleLarge; nhãn "Nghĩa của bạn" nếu có
│ ┌────────────────────────────────┐ │ ← MỘT bề mặt, ngăn divider
│ │ Định nghĩa                     │ │
│ │ a program on a phone           │ │
│ │ ────────────────────────────── │ │
│ │ Ví dụ                          │ │
│ │ This app is free.              │ │
│ │ ────────────────────────────── │ │
│ │ Cụm từ đi kèm                  │ │
│ │ + Thêm cụm từ                  │ │ ← mục trống thu thành nút chữ
│ │ ────────────────────────────── │ │
│ │ Ghi chú của tôi                │ │
│ │ + Thêm ghi chú                 │ │
│ └────────────────────────────────┘ │
│ Nguồn: Wiktionary …                 │
├────────────────────────────────────┤
│ [ + Thêm vào Sổ tay ]               │
└────────────────────────────────────┘
```

- Nhãn mục viết hoa đầu câu (bỏ IN HOA); bỏ các icon bút chì rời; xoá ví dụ/cụm từ của mình bằng icon x có contentDescription.
- Chưa có nghĩa Việt: khối `FeedbackBanner` info "Chưa có nghĩa tiếng Việt" + `[Thêm nghĩa của bạn]`.
- Bottom sheet chỉnh sửa giữ nguyên chức năng, áp component mới.

### 7.1 Luyện phát âm — `AppBottomSheet` thay hộp thoại

```
┌────────────────────────────────────┐
│            ───                      │
│ Luyện phát âm                  [✕] │
│            comfortable              │ ← từ/câu mục tiêu lớn
│           /ˈkʌmf.tə.bəl/            │
│        [🔊 Nghe mẫu]                │
│                                     │
│             ( 🎤 )                  │ ← nút micro 72dp giữa màn
│        Chạm để ghi âm               │
└────────────────────────────────────┘
```

- 3 trạng thái: **chờ** (micro + "Chạm để ghi âm") · **đang nghe** (micro đỏ + vòng nhịp động theo `onRmsChanged`,
  "Đang nghe… chạm để dừng") · **kết quả** (`ProgressRing` điểm, từng từ tô đúng/sai kèm icon, "Bạn đã đọc: …", gợi ý theo mức điểm, `[Thử lại]`).
- Lỗi không nghe thấy: icon + "Không nghe thấy giọng nói" + hướng dẫn (nói gần micro, kiểm tra quyền micro, máy ảo cần bật
  micro máy chủ) + `[Thử lại]`.
- **Sửa chấm điểm (commit riêng, có test):** so khớp theo từ (LCS như cũ); điểm = số từ khớp / (số từ mục tiêu + số từ thừa).
  Ví dụ: "boy boy" với "boy" → 1/(1+1) = 50%; thiếu từ vẫn bị trừ như cũ. Từ thừa hiện ở kết quả với nhãn "thừa".

## 8. Sổ tay (Khang)

**Mục đích:** quản lý và ôn từ đã lưu. **Hành động chính:** Ôn tập một sổ tay.

```
Danh sách sổ tay                     Chi tiết một sổ tay
┌──────────────────────────────┐    ┌──────────────────────────────┐
│ Sổ tay                  [+]  │    │ [←] ABC                  [⋮] │ ← ⋮: Đổi tên?, Xoá sổ tay
│ ┌──────────────────────────┐ │    │ 12 từ · 4 đã thuộc           │
│ │ ABC                      │ │    │ ▓▓▓▓░░░░░░                   │
│ │ 12 từ · 4 đã thuộc   ▓▓░ │ │    │ [🔍 Tìm trong sổ tay       ] │
│ └──────────────────────────┘ │    │ camera   máy ảnh    Đang học │ ← ListRow + trạng thái
│ ┌──────────────────────────┐ │    │ ← vuốt để xoá, snackbar Hoàn tác
│ │ Phỏng vấn  …             │ │    ├──────────────────────────────┤
│ └──────────────────────────┘ │    │ [ Ôn tập 12 thẻ ]            │ ← StickyBottomBar
└──────────────────────────────┘    └──────────────────────────────┘
```

- Danh sách: tiêu đề "Sổ tay" + nút `+` tạo sổ (`AppDialog` cũ). Mỗi sổ là một hàng trong một bề mặt chung, có tiến độ.
- Chi tiết: ô tìm lọc tại chỗ; trạng thái mỗi từ Mới / Đang học / Đã thuộc (`HandbookEntry.status`) dạng `ListRow` status.
- **Vuốt để xoá** (quyết định 5): gọi `HandbookRepository.removeWord`, snackbar "Đã gỡ “camera”" + `Hoàn tác` → `addWord`.
  Thêm vào `NotebookViewModel`: `removeWord`, `undoRemove`, `query` (có test).
- **Trống:** chưa có sổ → `EmptyState` "Gom từ muốn nhớ vào Sổ tay" + `[Tra từ]` và `[Xem chủ đề từ vựng]`; sổ trống → `EmptyState` + `[Tra từ]`.
- **Khách:** `LoginPrompt` giao diện mới (minh hoạ + lợi ích + `[Đăng nhập]`).

## 9. Hồ sơ (Huân) — đề xuất gộp với Tiến độ

**Đề xuất khác prompt (lý do):** tab Hồ sơ hiện có hai tầng (`Hồ sơ | Tiến độ` + tiêu đề). Người học mở tab này để xem
**tiến độ** hằng ngày, còn sửa hồ sơ thì hiếm. Vì vậy tab Hồ sơ = đầu trang hồ sơ + Tiến độ (mục 10) trong một trang cuộn,
không có tab con; sửa hồ sơ thành màn riêng "Sửa hồ sơ".

```
Tab Hồ sơ                            Sửa hồ sơ (màn riêng)
┌──────────────────────────────┐    ┌──────────────────────────────┐
│ Hồ sơ                   [⚙]  │    │ [✕] Sửa hồ sơ                │
│ (avatar) Eastern       [Sửa] │    │ (avatar đổi ảnh)             │
│ email · (A1) · 🔥6 · ⚡120   │    │ ┌──────────────────────────┐ │
│ ───────────────────────────  │    │ │ Tên hiển thị [Eastern  ] │ │
│ (nội dung Tiến độ, mục 10)   │    │ │ ──────────────────────── │ │
│                              │    │ │ Trình độ                 │ │
│                              │    │ │ [A1][A2][B1][B2][C1][C2] │ │ ← cuộn ngang
│                              │    │ │ Chưa biết trình độ?      │ │
│                              │    │ │ Làm bài kiểm tra 15 câu ›│ │
│                              │    │ │ ──────────────────────── │ │
│                              │    │ │ Mục tiêu mỗi ngày        │ │
│                              │    │ │ [10][15][30][45][60] phút│ │
│                              │    │ └──────────────────────────┘ │
│                              │    ├──────────────────────────────┤
│                              │    │ [Hủy]      [ Lưu hồ sơ ]     │
└──────────────────────────────┘    └──────────────────────────────┘
```

- Chip trình độ giữ đủ A1–C2 như validator; ghi chú lệch SRS (C1/C2) vào `docs/ui-review-team.md`. Chip mục tiêu gồm giá trị
  đang có nếu không thuộc danh sách; validator 5–120 giữ nguyên.
- Lỗi xác thực hiện dưới trường như cũ. Lưu thành công → snackbar + quay lại.
- **Chưa có hồ sơ (cổng bắt buộc):** màn Tạo hồ sơ dùng cùng form, thanh trên có nút chữ "Để sau" và "Đăng xuất" trong menu ⋮.
- **Xoá hồ sơ** chuyển vào Cài đặt (mục 11), có `ConfirmDialog`.

## 10. Thống kê tiến độ (Huân)

```
│ Tiến độ               [7 ngày ▾] │ ← bộ chọn 7 ngày / 30 ngày
│ ┌──────────┐┌──────────┐          │
│ │ 🔥 6 ngày││ ⚡ 120 XP │          │ ← StatCard 2×2
│ │ Chuỗi    ││ Tổng XP  │          │
│ ├──────────┤├──────────┤          │
│ │ 24 từ    ││ 12 từ    │          │
│ │ đã học   ││ cần ôn › │          │ ← bấm → Ôn tập từ đến hạn
│ └──────────┘└──────────┘          │
│ Thời gian học                      │
│ 15'┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄ mục tiêu  │ ← vạch mục tiêu ngày
│  ▁ ▃ ▁ ▅ █ ▂ ▆                     │ ← cột Canvas, trục phút
│ T5 T6 T7 CN T2 T3 T4               │
│ Chuỗi 7 ngày  ● ● ○ ● ● ● ●        │ ← chấm ngày có học
│ Bài tập  ( ◯ 82% ) đúng · 6 bài    │ ← ProgressRing độ chính xác + số bài
```

- **Chưa có hoạt động:** `EmptyState` "Hoàn thành bài học đầu tiên để bắt đầu chuỗi ngày học" + `[Học ngay]` (mở tab Học);
  không hiện toàn số 0.
- 30 ngày: cột hẹp hơn, nhãn trục mỗi 7 ngày. Dữ liệu lọc từ `observeAllStats` có sẵn (chỉ hiển thị).
- Chưa có bài tập → ẩn khối độ chính xác.
- **Trường thêm vào `ProgressState`:** `goalMinutes`, `range`, `days` (danh sách theo khoảng), `activeDays7`, `quizCount` (test).

## 11. Cài đặt (Huân + Đông)

```
│ [←] Cài đặt                        │
│ Học tập                            │
│  Kiểm tra trình độ              ›  │
│  Nhắc học hằng ngày   20:00     ›  │
│ Tài khoản                          │
│  dongnguyen…@gmail.com             │
│  Sửa hồ sơ                      ›  │
│  Xoá hồ sơ trên thiết bị           │ ← error, ConfirmDialog
│  Đăng xuất                         │ ← error, ConfirmDialog
│ Phiên bản 0.1.0                    │
```

## 12. Nhắc học (Đông)

**Hành động chính:** bật/tắt và chọn giờ (lưu ngay như hiện nay).

```
│ [←] Nhắc học                       │
│ ┌────────────────────────────────┐ │
│ │ 🔔 Nhắc học hằng ngày     [●━] │ │ ← hero bề mặt brand khi bật
│ │ Nhắc lúc 20:00 mỗi ngày        │ │
│ └────────────────────────────────┘ │
│ Giờ nhắc                           │
│ [07:00][12:00][20:00][21:30][Khác] │ ← chip nhanh + "Khác" mở TimePicker
│ Xem trước thông báo                │
│ ┌ EngMate · 20:00 ───────────────┐ │ ← xem trước nội dung thông báo thật
│ │ Đến giờ học rồi!               │ │
│ │ Dành vài phút ôn ngữ pháp…     │ │
│ └────────────────────────────────┘ │
│ Thông báo có thể đến trễ vài phút… │
```

- Thiếu quyền: `FeedbackBanner` error + `[Mở cài đặt]`. Tắt: phần giờ và xem trước mờ đi nhưng vẫn đọc được.
- Chip giờ nhanh gọi `setTime` có sẵn; không thêm trường lưu mới.

## 13. Đăng nhập, Đăng ký, Quên mật khẩu (Huân)

- Theo mockup 02–04: logo ngang trên cùng, tiêu đề Nunito, form trên `surface`, nút chính 52dp, liên kết phụ là nút chữ.
- Thay toàn bộ màu cứng bằng token; `LineIcon` giữ lại nhưng nhận màu từ theme. Bỏ chữ "(sắp có)" trên nút Google: ẩn nút
  khi chưa hỗ trợ.
- Thông báo lỗi/thành công dùng `FeedbackBanner` (icon + màu).
- Màn khởi động: nền brand + logo mark (theme `windowBackground` và `styles.xml`), không thêm màn splash riêng.

## 14. Trạng thái dùng chung

| Trạng thái | Component | Quy tắc |
|---|---|---|
| Đang tải | `LoadingState` (skeleton theo hình nội dung) | không dùng vòng quay giữa màn trống, trừ khi < 300ms |
| Trống | `EmptyState` (minh hoạ vector nhỏ + câu hướng dẫn + nút) | luôn có hành động tiếp theo |
| Lỗi | `ErrorState` (icon + câu dễ hiểu + `[Thử lại]`) | không hiện thông báo kỹ thuật |
| Phản hồi tạm | Snackbar | hành động hoàn tác khi có |
| Thông tin trong trang | `FeedbackBanner` (info/success/warning/error, luôn có icon) | |

## 15. Chuyển động

- 150–300ms, easing chuẩn Material; dùng `animate*AsState`, `AnimatedVisibility`, `AnimatedContent`.
- Lật thẻ 300ms; XP nảy 250ms; confetti ~1.2s; vòng mục tiêu chạy tới giá trị 600ms khi mở Trang chủ lần đầu.
- Máy bật "Xoá hiệu ứng động" (`Settings.Global.ANIMATOR_DURATION_SCALE = 0`): bỏ confetti, nảy và lật, chuyển ngay.

## 16. Không đổi

Entity, DAO ghi, schema, migration, validator, quy tắc chấm quiz/SM-2, `firestore.rules`, luồng đăng nhập/đồng bộ,
cấu trúc điều hướng bằng state trong `EngMateApp.kt` (chỉ thêm route Tra từ, Sửa hồ sơ, Ôn tập từ đến hạn).
Ngoại lệ có chủ đích: công thức điểm luyện phát âm (mục 7.1).
