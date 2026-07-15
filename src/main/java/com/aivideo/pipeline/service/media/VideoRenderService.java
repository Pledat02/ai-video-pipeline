package com.aivideo.pipeline.service.media;

import java.nio.file.Path;

/**
 * TUẦN 5-6: Ghép video bằng FFmpeg qua ProcessBuilder. Ví dụ lệnh cơ bản:
 * ffmpeg -loop 1 -i background.jpg -i audio.mp3 -shortest -vf subtitles=sub.srt out.mp4
 * Học được: quản lý process, đọc stderr để bắt lỗi, timeout, xử lý file lớn.
 */
public interface VideoRenderService {

    /**
     * @param audioPath file giọng đọc từ bước TTS
     * @param script    kịch bản (để sinh phụ đề .srt)
     * @param jobId     đặt tên file output
     * @return đường dẫn file video mp4 hoàn chỉnh
     */
    Path render(Path audioPath, String script, Long jobId, RenderOptions options);
}
