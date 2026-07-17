package com.aivideo.pipeline.domain;

/**
 * Vòng đời của một job video. Mỗi bước pipeline chuyển job sang trạng thái kế tiếp.
 * Nếu bất kỳ bước nào lỗi -> FAILED (kèm errorMessage để debug).
 */
public enum JobStatus {
    GENERATING_IMAGES,
    PENDING,            // Vừa tạo, chờ sinh kịch bản
    SCRIPTING,          // Đang gọi LLM sinh kịch bản
    SCRIPT_READY,       // Kịch bản đã sinh xong, CHỜ NGƯỜI DUYỆT (bước bắt buộc!)
    SHOT_PLAN_READY,    // Đã tách danh sách cảnh P01-Pxx, chờ duyệt kế hoạch
    GENERATING_KEYFRAMES,
    KEYFRAMES_REVIEW,   // Đã có ảnh, chờ duyệt đủ 12 keyframe
    APPROVED,           // Người dùng đã duyệt/sửa kịch bản, sẵn sàng sản xuất
    GENERATING_AUDIO,   // Đang tạo giọng đọc TTS
    RENDERING,          // Đang ghép video bằng FFmpeg
    UPLOADING,          // Đang upload lên YouTube
    COMPLETED,          // Xong! youtubeVideoId đã có giá trị
    FAILED              // Lỗi ở bước nào đó, xem errorMessage
}
