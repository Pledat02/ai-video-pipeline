package com.aivideo.pipeline.dto;

import com.aivideo.pipeline.domain.JobStatus;
import com.aivideo.pipeline.domain.VideoJob;

import java.time.Instant;

/** DTO trả về cho client - không expose entity trực tiếp (best practice) */
public record JobResponse(
        Long id,
        String topic,
        String scriptContent,
        String sourceContent,
        Long characterId,
        String characterDescription,
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
        JobStatus status,
        String youtubeVideoId,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static JobResponse from(VideoJob job) {
        return new JobResponse(
                job.getId(),
                job.getTopic(),
                job.getScriptContent(),
                job.getSourceContent(),
                job.getCharacterId(),
                job.getCharacterDescription(),
                job.getTargetDurationSeconds(),
                job.getVoice(),
                job.getImageAgent(),
                job.getImageCount(),
                job.getLanguage(),
                job.getSpeechRatePercent(),
                job.getSubtitlesEnabled(),
                job.getAspectRatio(),
                job.getImageStyle(),
                job.getSceneMotion(),
                job.getMusicVolumePercent(),
                job.getStatus(),
                job.getYoutubeVideoId(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
