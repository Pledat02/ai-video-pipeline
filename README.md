# AI Video Pipeline

Hệ thống tự động hoá sản xuất video kể chuyện bằng AI và đăng lên YouTube.
Dự án portfolio Spring Boot: REST API, xử lý job bất đồng bộ, tích hợp LLM/TTS/FFmpeg/YouTube API.

## Kiến trúc

```
POST /api/jobs ──> [SCRIPTING: LLM sinh kịch bản] ──> SCRIPT_READY (chờ người duyệt)
                                                            │
POST /api/jobs/{id}/approve ──> [TTS] ──> [FFmpeg render] ──> [Upload YouTube] ──> COMPLETED
```

Nguyên tắc: **con người duyệt kịch bản trước khi render** — vừa kiểm soát chất lượng,
vừa đáp ứng yêu cầu "original value" trong chính sách kiếm tiền của YouTube với nội dung AI.

## Chạy thử ngay (chưa cần API key nào)

```bash
mvn spring-boot:run
```

Tạo job:
```bash
curl -X POST localhost:8080/api/jobs -H "Content-Type: application/json" \
  -d '{"topic": "Truyện Thạch Sanh kể theo phong cách kinh dị nhẹ"}'
```

Xem trạng thái:
```bash
curl localhost:8080/api/jobs/1
```

Duyệt kịch bản (khi status = SCRIPT_READY):
```bash
curl -X POST localhost:8080/api/jobs/1/approve
```

Job sẽ chạy qua các stub và kết thúc ở COMPLETED với youtubeVideoId giả.

## Lộ trình thay stub bằng module thật

| Tuần | Module | File cần thay | Công nghệ |
|------|--------|---------------|-----------|
| 3 | Sinh kịch bản | `StubScriptGenerationService` | Claude/Gemini API qua RestClient |
| 4 | Giọng đọc | `StubTextToSpeechService` | edge-tts (miễn phí) hoặc Google Cloud TTS |
| 5-6 | Render video | `StubVideoRenderService` | FFmpeg qua ProcessBuilder + phụ đề .srt |
| 7 | Upload | `StubYoutubeUploadService` | YouTube Data API v3 + OAuth 2.0 |
| 8 | Hoàn thiện | PostgreSQL, dashboard, deploy | Docker, VPS |

## Điểm nhấn kỹ thuật (để kể trong phỏng vấn)

- Xử lý bất đồng bộ với `@Async` + thread pool riêng (render nặng nên giới hạn 1 job/lúc)
- State machine đơn giản quản lý vòng đời job, kiểm soát chuyển trạng thái hợp lệ
- Tách interface cho từng bước pipeline: dễ test, dễ thay implementation
- Xử lý lỗi tập trung với `@RestControllerAdvice`, job lỗi lưu message + retry được
- DTO tách biệt entity, validation với Bean Validation
