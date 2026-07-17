package com.aivideo.pipeline.dto;

import com.aivideo.pipeline.domain.VideoShot;

public record ShotResponse(Long id, Long jobId, Integer shotNumber, String title, String narration,
        String visualPrompt, String camera, Double durationSeconds, Long seed, Boolean approved, String imageUrl) {
    public static ShotResponse from(VideoShot shot) {
        String url = shot.getImageExt() == null ? null
                : "/media/job-" + shot.getJobId() + "-image-" + shot.getShotNumber() + "." + shot.getImageExt();
        return new ShotResponse(shot.getId(), shot.getJobId(), shot.getShotNumber(), shot.getTitle(),
                shot.getNarration(), shot.getVisualPrompt(), shot.getCamera(), shot.getDurationSeconds(),
                shot.getSeed(), shot.getApproved(), url);
    }
}
