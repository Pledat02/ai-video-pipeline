# PRD — AI Video Pipeline

## 1. Bối cảnh & vấn đề

Sản xuất video kể chuyện (storytelling) để đăng YouTube tốn nhiều công đoạn thủ công: viết kịch bản, thu âm, dựng video, đăng tải. Với sự hỗ trợ của AI (LLM sinh kịch bản, TTS đọc, FFmpeg dựng), phần lớn quy trình có thể tự động hoá — nhưng cần giữ lại một bước kiểm soát của con người để đảm bảo chất lượng nội dung và tuân thủ chính sách kiếm tiền nội dung AI của YouTube (yêu cầu "original value").

**Mục tiêu dự án:** xây một hệ thống backend (Spring Boot) minh hoạ pipeline sản xuất video bán tự động, dùng làm dự án portfolio để chứng minh năng lực xử lý bất đồng bộ, tích hợp API bên thứ ba, và thiết kế state machine — phục vụ phỏng vấn xin việc.

## 2. Đối tượng người dùng

| Persona | Nhu cầu |
|---|---|
| Người vận hành (chính tác giả) | Nộp chủ đề video, duyệt/sửa kịch bản trước khi render, theo dõi tiến độ job, xem lỗi khi thất bại |
| Nhà tuyển dụng / người review portfolio | Đọc code, thấy kiến trúc rõ ràng, xử lý lỗi tốt, tách lớp hợp lý |

Không có người dùng cuối (end-user) xem video trực tiếp qua hệ thống — sản phẩm đầu ra là video được đăng lên YouTube, xem tại đó.

## 3. Phạm vi (Scope)

### Trong phạm vi (v1 — theo lộ trình 8 tuần)
- Tạo job từ 1 chủ đề (topic) qua REST API
- Sinh kịch bản tự động bằng LLM (Claude hoặc Gemini, có thể chọn qua config)
- Con người xem/sửa/duyệt kịch bản trước khi render (bắt buộc, không thể bỏ qua)
- Sau khi duyệt: tự động TTS → render video (FFmpeg + phụ đề) → upload YouTube
- Theo dõi trạng thái job qua state machine, xem lỗi cụ thể khi thất bại, retry job lỗi
- Chạy được ngay với stub (không cần API key) để demo nhanh

### Ngoài phạm vi (v1)
- Giao diện người dùng (dashboard web) — v1 chỉ có REST API, thao tác qua curl/Postman
- Đa người dùng / multi-tenant, phân quyền
- Chỉnh sửa video sau khi render (cắt, thêm nhạc nền, hiệu ứng)
- Lên lịch đăng bài tự động theo khung giờ
- Phân tích hiệu suất video sau khi đăng (view, retention)

## 4. Luồng nghiệp vụ chính (User Flow)

```
POST /api/jobs {topic}
   → job status: PENDING → SCRIPTING (LLM chạy nền)
   → SCRIPT_READY (chờ duyệt)

GET /api/jobs/{id}          → xem kịch bản, trạng thái
PUT /api/jobs/{id}/script    → (tuỳ chọn) sửa nội dung kịch bản

POST /api/jobs/{id}/approve
   → GENERATING_AUDIO → RENDERING → UPLOADING → COMPLETED
   → (nếu bước nào lỗi) → FAILED, có errorMessage cụ thể

POST /api/jobs/{id}/retry    → chạy lại job đang FAILED
```

## 5. Yêu cầu chức năng (Functional Requirements)

| # | Yêu cầu | Trạng thái |
|---|---|---|
| FR1 | Tạo job mới từ topic, validate input | ✅ Đã có |
| FR2 | Sinh kịch bản tự động khi tạo job (async, không block request) | ✅ Đã có (stub + Claude + Gemini) |
| FR3 | Cho phép xem toàn bộ danh sách job và chi tiết 1 job | ✅ Đã có |
| FR4 | Cho phép sửa kịch bản trước khi duyệt | ✅ Đã có |
| FR5 | Duyệt kịch bản → tự động chạy tiếp TTS/render/upload | ✅ Đã có (stub); TTS/render/upload thật chưa làm |
| FR6 | Retry job khi FAILED | ✅ Đã có |
| FR7 | Lưu lại lý do lỗi cụ thể khi job thất bại | ✅ Đã có (vừa fix bug truncate error_message) |
| FR8 | Chuyển trạng thái job phải theo đúng state machine, không cho nhảy bước sai | ✅ Đã có (`VideoJobService.requireStatus()` chặn transition sai, ném `InvalidStateException`) |
| FR9 | Sinh giọng đọc (TTS) từ kịch bản | ⏳ Chưa làm — Tuần 4 |
| FR10 | Render video từ audio + kịch bản (phụ đề .srt) | ⏳ Chưa làm — Tuần 5-6 |
| FR11 | Upload video lên YouTube qua Data API v3 | ⏳ Chưa làm — Tuần 7 |

## 6. Yêu cầu phi chức năng (Non-functional)

- **Đồng thời hoá render**: chỉ chạy 1 job render tại một thời điểm (tài nguyên FFmpeg nặng) — đã thiết kế qua thread pool riêng (`AsyncConfig`).
- **Khả năng phục hồi**: job lỗi không làm crash toàn hệ thống; lỗi được ghi nhận vào DB, có thể retry.
- **Dễ thay thế provider**: mỗi bước pipeline (script/TTS/render/upload) tách interface riêng, chuyển đổi qua config (`pipeline.script-generation.provider`), không sửa code business logic.
- **Chi phí**: ưu tiên chọn được provider có free tier (Gemini) để demo không tốn phí; Claude vẫn hỗ trợ khi có credit.
- **Dev experience**: chạy được ngay bằng `mvn spring-boot:run` với H2 in-memory, không cần cài DB ngoài.

## 7. Rủi ro & câu hỏi mở

| Rủi ro | Ghi chú |
|---|---|
| Free tier LLM (Gemini) có thể bị giới hạn quota/region, không ổn định để demo | Đã gặp thực tế: quota free tier = 0 cho key hiện tại, cần kiểm tra lại project Google Cloud |
| YouTube Data API v3 có giới hạn quota upload/ngày cho project mới | Cần xin quota tăng nếu test nhiều lần |
| FFmpeg render là bước dễ lỗi nhất (định dạng, timeout, tài nguyên) | Dự trù buffer thời gian ở Tuần 5-6 theo lộ trình |
| Chính sách YouTube về nội dung AI có thể thay đổi | Không kiểm soát được — chỉ tuân thủ tốt nhất có thể (giữ bước duyệt kịch bản) |

## 8. Định nghĩa hoàn thành (Definition of Done — v1)

Một job đi được trọn vẹn từ `POST /api/jobs {topic}` đến `COMPLETED` với:
- Kịch bản do LLM thật sinh ra (không phải stub)
- Audio do TTS thật tạo ra
- Video render thật bằng FFmpeg, có phụ đề
- Video được upload thành công lên YouTube (dù ở chế độ unlisted/private để test)
- Toàn bộ log/lỗi ở mỗi bước có thể truy vết được qua `errorMessage` hoặc log ứng dụng

## 9. Liên kết

- Kiến trúc & hướng dẫn chạy: [README.md](README.md)
- Lộ trình triển khai theo tuần/ngày: xem lịch sử trao đổi hoặc bổ sung file `ROADMAP.md` nếu cần tách riêng.
