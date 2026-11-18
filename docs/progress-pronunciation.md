# Thống kê tiến độ và luyện phát âm

## Ghi nhận tiến độ
`RoomProgressRecorder` (thay `NoOpProgressRecorder`) ghi mỗi hành động học thành một dòng `learning_activity`;
`StudyDao` tự tính lại `daily_study_stat`, màn **Tiến độ** đọc từ đó (chuỗi ngày, XP, từ đã học, phút học mỗi ngày).

| Hành động | XP | Thời gian ước tính | Ghi chú |
| --- | --- | --- | --- |
| Hoàn thành quiz / kiểm tra trình độ | XP của lượt làm | 30 giây/câu | tính 1 quiz hoàn thành |
| Ôn một thẻ flashcard | 10 nếu "Đã thuộc", 3 nếu chưa | 15 giây | tính 1 từ đã ôn; từ đạt MASTERED lần đầu tính 1 từ đã học |
| Mở một bài ngữ pháp | 5 | 2 phút | mỗi bài tính một lần mỗi ngày |
| Luyện phát âm một lượt | điểm/10 (tối đa 10) | 20 giây | |

Thời gian chỉ là ước tính theo từng hành động (app chưa đo thời gian thực). Khách chưa đăng nhập không được ghi.
Dữ liệu `learning_activity` được đồng bộ lên Firestore (xem [cloud-sync.md](cloud-sync.md)).

## Luyện phát âm (ghi âm)
- Nút mic cạnh mỗi câu ví dụ trong bài ngữ pháp, nút **Luyện phát âm** ở cuối bài, và chip **Luyện nói** ở chi tiết từ.
- Luồng: nghe câu mẫu (TTS) → bấm **Ghi âm** (xin quyền micro lần đầu) → đọc → app nhận dạng bằng `SpeechRecognizer` của Android (en-US).
- Chấm điểm (`PronunciationScorer`): so từng từ của câu mẫu với câu nhận dạng được, tính tỉ lệ từ đúng theo thứ tự (LCS);
  từ dài từ 5 chữ cái chấp nhận sai một ký tự. Từ đúng tô xanh, từ sai tô đỏ.
- Cần dịch vụ nhận dạng giọng nói của Google trên máy (thường có sẵn) và thường cần mạng.
- App không lưu file ghi âm; chỉ dùng văn bản nhận dạng được để chấm điểm.

## Giới hạn
Điểm dựa trên nhận dạng giọng nói nên chỉ đánh giá được việc đọc đúng từ, không chấm trọng âm hay ngữ điệu.
