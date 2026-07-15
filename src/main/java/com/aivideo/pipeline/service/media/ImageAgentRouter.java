package com.aivideo.pipeline.service.media;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ImageAgentRouter {
    private final List<ImageGenerationService> providers;

    public ImageAgentRouter(List<ImageGenerationService> providers) {
        this.providers = providers;
    }

    public void generate(String provider, String topic, String script, int count, Long jobId,
            String imageStyle, String aspectRatio) {
        ImageGenerationService service = providers.stream()
                .filter(candidate -> candidate.provider().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Image agent chưa được hỗ trợ: " + provider));
        service.generateImages(topic, script, count, jobId, imageStyle, aspectRatio);
    }
}
