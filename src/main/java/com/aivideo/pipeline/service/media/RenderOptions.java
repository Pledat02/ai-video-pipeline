package com.aivideo.pipeline.service.media;

import java.nio.file.Path;
import java.util.List;

public record RenderOptions(
        boolean subtitlesEnabled,
        String aspectRatio,
        String sceneMotion,
        Path musicPath,
        int musicVolumePercent,
        List<Double> shotDurations
) {
    public RenderOptions(boolean subtitlesEnabled, String aspectRatio, String sceneMotion,
            Path musicPath, int musicVolumePercent) {
        this(subtitlesEnabled, aspectRatio, sceneMotion, musicPath, musicVolumePercent, List.of());
    }
}
