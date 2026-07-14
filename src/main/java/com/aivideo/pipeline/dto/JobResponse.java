package com.aivideo.pipeline.dto;

import com.aivideo.pipeline.domain.JobStatus;
import com.aivideo.pipeline.domain.VideoJob;

import java.time.Instant;

/** DTO trả về cho client - không expose entity trực tiếp (best practice) */
public record JobResponse(
        Long id,
        String topic,
        String scriptContent,
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
                job.getStatus(),
                job.getYoutubeVideoId(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
