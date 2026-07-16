package com.aivideo.pipeline.service.media;

/**
 * TUẦN 3: Tích hợp LLM API (Claude/Gemini) để sinh kịch bản từ chủ đề.
 * Tách interface để dễ thay implementation và dễ mock khi viết test.
 */
public interface ScriptGenerationService {

    /**
     * @param topic chủ đề video do người dùng nhập
     * @param characterDescription mô tả nhân vật chính (tuỳ chọn) - giúp kịch bản
     *        nhắc tới nhân vật nhất quán, dùng chung với prompt sinh ảnh
     * @return nội dung kịch bản hoàn chỉnh (lời đọc cho video)
     */
    String generateScript(String topic, String sourceContent, Integer targetDurationSeconds, String language,
            String characterDescription);
}
