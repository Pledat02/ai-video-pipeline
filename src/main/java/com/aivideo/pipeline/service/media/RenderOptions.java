package com.aivideo.pipeline.service.media;

import java.nio.file.Path;

public record RenderOptions(
        boolean subtitlesEnabled,
        String aspectRatio,
        String sceneMotion,
        Path musicPath,
        int musicVolumePercent
) {}
