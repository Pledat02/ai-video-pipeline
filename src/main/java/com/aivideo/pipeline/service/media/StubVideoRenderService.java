package com.aivideo.pipeline.service.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(prefix = "pipeline.video-render", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubVideoRenderService implements VideoRenderService {

    private final Path workDir;

    public StubVideoRenderService(@Value("${pipeline.work-dir}") String workDir) {
        this.workDir = Path.of(workDir);
    }

    @Override
    public Path render(Path audioPath, String script, Long jobId) {
        try {
            Path out = workDir.resolve("job-" + jobId + "-video.txt");
            // Stub: TUẦN 5-6 thay bằng FFmpeg thật.
            Files.writeString(out, "VIDEO PLACEHOLDER ghép từ " + audioPath.getFileName());
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Không render được video", e);
        }
    }
}
