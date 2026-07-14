package com.aivideo.pipeline.service.media;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

@Service
public class StubYoutubeUploadService implements YoutubeUploadService {

    @Override
    public String upload(Path videoPath, String title) {
        // Stub: trả về ID giả. TUẦN 7: thay bằng YouTube Data API v3 thật.
        return "FAKE-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
