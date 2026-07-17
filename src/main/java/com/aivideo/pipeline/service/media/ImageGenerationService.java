package com.aivideo.pipeline.service.media;

public interface ImageGenerationService {
    String provider();
    void generateImages(String topic, String script, int count, Long jobId, String imageStyle, String aspectRatio,
            String characterDescription);

    void generateSingleImage(String topic, String visualPrompt, Long jobId, int outputIndex,
            String imageStyle, String aspectRatio, String characterDescription, long seed);
}
