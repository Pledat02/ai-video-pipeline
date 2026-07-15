package com.aivideo.pipeline.service.media;

public interface ImageGenerationService {
    String provider();
    void generateImages(String topic, String script, int count, Long jobId, String imageStyle, String aspectRatio);
}
