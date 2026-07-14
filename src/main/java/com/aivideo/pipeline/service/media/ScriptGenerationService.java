package com.aivideo.pipeline.service.media;

/**
 * TUẦN 3: Tích hợp LLM API (Claude/Gemini) để sinh kịch bản từ chủ đề.
 * Tách interface để dễ thay implementation và dễ mock khi viết test.
 */
public interface ScriptGenerationService {

    /**
     * @param topic chủ đề video do người dùng nhập
     * @return nội dung kịch bản hoàn chỉnh (lời đọc cho video)
     */
    String generateScript(String topic);
}
