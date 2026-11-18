# Demo: ngữ pháp, quiz, kiểm tra trình độ, nhắc học

Main đã bỏ bản demo; chạy bản **liveDebug** (`./gradlew :app:assembleLiveDebug`, cần
`app/google-services.json`) và đăng nhập. Dữ liệu mẫu: 3 bài ngữ pháp × 10 câu và 15 câu kiểm tra trình độ.

> Sau khi rebase lên main, màn quiz, kết quả, kiểm tra trình độ và nhắc học chưa được nối vào
> điều hướng mới, nên các bước dưới đây chỉ chạy được sau khi nối lại lối vào.

Chuẩn bị: mở tab **Hồ sơ**, bấm "Tạo hồ sơ" và lưu (để lưu được kết quả). Muốn thử ca "chưa có
hồ sơ" thì làm ca đó trước bước này.

## Luồng chính

1. **Làm bài tập ngữ pháp**: tab Ngữ pháp → chọn một bài → "Làm bài tập".
   - Mỗi câu: chọn một đáp án hoặc gõ câu trả lời; nút "Kiểm tra" chỉ bật khi đã trả lời.
   - Bấm "Kiểm tra": hiện Đúng/Sai (màu và icon), đáp án đúng, giải thích. Không đổi được câu đã kiểm tra.
   - Câu điền từ: thử gõ thừa khoảng trắng hoặc chữ hoa (vd. `  GOES `) vẫn được chấm đúng.
   - Xoay màn hình giữa chừng: câu đang làm và lựa chọn vẫn giữ nguyên.
2. **Xem kết quả và câu sai**: câu cuối bấm "Xem kết quả".
   - Màn kết quả hiện số câu đúng, % đúng, XP; danh sách mở sẵn ở "Câu sai" nếu có câu sai.
   - Đổi chip Tất cả / Câu sai / Câu đúng. "Làm lại" bắt đầu bài mới cùng nguồn; "Xong" đóng.
   - Lượt làm cũng có trong tab Lịch sử.
3. **Kiểm tra trình độ**: Hồ sơ → "Kiểm tra trình độ" → "Bắt đầu", làm 15 câu.
   - Kết quả hiện trình độ (A1–B2) và "Đã cập nhật trình độ vào hồ sơ"; mở Hồ sơ để thấy trình độ mới.
   - "Xem chi tiết" mở phần xem lại; "Để sau" ở màn giới thiệu thì đóng mà không làm bài.
4. **Bật nhắc học**: Hồ sơ → "Nhắc học hằng ngày".
   - Bấm "Giờ nhắc", chọn giờ **sau 1–2 phút** so với hiện tại, bấm Lưu (snackbar "Đã lưu").
   - Bật công tắc, Android 13+ hỏi quyền thông báo → Cho phép. Chờ thông báo "Đến giờ học rồi!";
     bấm vào để mở app.
   - Thông báo có thể **trễ vài phút** khi máy tiết kiệm pin. Khi demo nên cắm sạc hoặc tắt tối ưu pin
     cho EngMate (Cài đặt → Ứng dụng → EngMate → Pin → Không hạn chế).

## Ca lỗi cần thử

| Ca | Cách thử | Kết quả mong đợi |
| --- | --- | --- |
| Chưa có hồ sơ | Xoá hồ sơ (Hồ sơ → "Xóa hồ sơ trên thiết bị"), làm một bài đến câu cuối | Báo cần tạo hồ sơ, giữ câu trả lời. "Tạo hồ sơ" mở tab Hồ sơ; tạo xong, mở lại "Làm bài tập" của bài đó: bài làm vẫn còn, bấm "Thử lại" để lưu |
| Thoát giữa chừng | Trả lời 1–2 câu rồi bấm mũi tên quay lại hoặc nút Back | Hỏi "Thoát bài làm? Kết quả sẽ không được lưu"; Thoát thì không có lượt mới trong Lịch sử |
| Thoát khi chưa trả lời | Mở bài, bấm quay lại ngay | Đóng luôn, không hỏi |
| Từ chối quyền thông báo | Bật công tắc nhắc học, chọn "Không cho phép" | Công tắc về tắt, hiện thông báo kèm nút "Mở cài đặt" |
| Bài chưa có câu hỏi | Hồ sơ → Bài ngữ pháp → tạo bài mới, rồi mở bài đó và "Làm bài tập" | "Bài này chưa có câu hỏi" và nút quay lại |
| Thiếu câu kiểm tra trình độ | Không có sẵn trong dữ liệu mẫu; chỉ xảy ra khi xoá bớt câu kiểm tra trình độ | Báo cần đủ 15 câu và số câu hiện có |
