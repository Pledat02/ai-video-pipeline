package com.aivideo.pipeline.dto;

/**
 * Tuỳ chọn render khi "sản xuất lại" một job COMPLETED. Mọi field đều optional -
 * field nào null thì giữ nguyên giá trị đang có trên job (khác với CreateJobRequest,
 * nơi field null sẽ được gán giá trị mặc định vì job chưa tồn tại).
 */
public record ReproduceRequest(
        String voice,
        Long characterId,
        String imageAgent,
        Integer imageCount,
        String language,
        Integer speechRatePercent,
        Boolean subtitlesEnabled,
        String aspectRatio,
        String imageStyle,
        String sceneMotion,
        Integer musicVolumePercent
) {}
