package com.aivideo.pipeline.service.media;

import java.nio.file.Path;

/**
 * TUẦN 7: Upload qua YouTube Data API v3 (Google API Client cho Java).
 * Việc cần làm: OAuth 2.0 flow, set title/description/tags,
 * và QUAN TRỌNG: khai báo nội dung synthetic/AI theo chính sách YouTube.
 */
public interface YoutubeUploadService {

    /**
     * @param videoPath file video hoàn chỉnh
     * @param title     tiêu đề video
     * @return videoId trên YouTube
     */
    String upload(Path videoPath, String title);
}
