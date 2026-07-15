package com.aivideo.pipeline.service.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(prefix = "pipeline.text-to-speech", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubTextToSpeechService implements TextToSpeechService {

    private final Path workDir;

    public StubTextToSpeechService(@Value("${pipeline.work-dir}") String workDir) {
        this.workDir = Path.of(workDir);
    }

    @Override
    public Path synthesize(String script, Long jobId, String voice, int ratePercent, boolean subtitlesEnabled) {
        try {
            Files.createDirectories(workDir);
            Path out = workDir.resolve("job-" + jobId + "-audio.txt");
            // Stub: ghi tạm file text. TUẦN 4: thay bằng file mp3 thật từ edge-tts.
            Files.writeString(out, "AUDIO PLACEHOLDER cho kịch bản dài " + script.length() + " ký tự");
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file audio", e);
        }
    }
}
