package com.aivideo.pipeline.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/** Cấu hình tạo một video; chỉ topic là đủ cho luồng cũ. */
public record CreateJobRequest(
        @Size(max = 500, message = "Chủ đề tối đa 500 ký tự") String topic,
        String sourceContent,
        String scriptContent,
        Long characterId,
        Integer targetDurationSeconds,
        String voice,
        String imageAgent,
        Integer imageCount,
        String language,
        Integer speechRatePercent,
        Boolean subtitlesEnabled,
        String aspectRatio,
        String imageStyle,
        String sceneMotion,
        Integer musicVolumePercent,
        String creationMode,
        String directorPrompt,
        Long characterC2Id,
        Long characterC3Id,
        Long characterC4Id,
        List<Long> castCharacterIds
) {}
