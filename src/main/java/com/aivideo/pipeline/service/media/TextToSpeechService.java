package com.aivideo.pipeline.service.media;

import java.nio.file.Path;

/**
 * TUẦN 4: Tích hợp TTS. Gợi ý bắt đầu với edge-tts (miễn phí, giọng Việt tốt):
 * gọi tiến trình `edge-tts --voice vi-VN-HoaiMyNeural --text ... --write-media out.mp3`
 * qua ProcessBuilder, hoặc dùng Google Cloud TTS nếu muốn thuần API.
 */
public interface TextToSpeechService {

    /**
     * @param script  nội dung kịch bản đã duyệt
     * @param jobId   dùng để đặt tên file output
     * @return đường dẫn file audio (mp3/wav) đã sinh
     */
    Path synthesize(String script, Long jobId, String voice, int ratePercent, boolean subtitlesEnabled);
}
