package com.aivideo.pipeline.service.media;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Sinh giọng đọc bằng edge-tts (Microsoft Edge TTS, miễn phí, giọng Việt tự nhiên).
 * Gọi tiến trình `edge-tts` qua ProcessBuilder - cần cài: pip install edge-tts.
 * Kích hoạt khi pipeline.text-to-speech.provider=edge-tts.
 */
@Service
@ConditionalOnProperty(prefix = "pipeline.text-to-speech", name = "provider", havingValue = "edge-tts")
@Slf4j
public class EdgeTtsTextToSpeechService implements TextToSpeechService {

    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    private final Path workDir;
    private final String executable;
    private final String voice;

    public EdgeTtsTextToSpeechService(
            @Value("${pipeline.work-dir}") String workDir,
            @Value("${pipeline.text-to-speech.edge-tts.executable}") String executable,
            @Value("${pipeline.text-to-speech.edge-tts.voice}") String voice) {
        this.workDir = Path.of(workDir);
        this.executable = executable;
        this.voice = voice;
    }

    @Override
    public Path synthesize(String script, Long jobId) {
        try {
            Files.createDirectories(workDir);
            Path scriptFile = workDir.resolve("job-" + jobId + "-script.txt");
            Path audioFile = workDir.resolve("job-" + jobId + "-audio.mp3");
            Path subtitleFile = workDir.resolve("job-" + jobId + "-subtitle.srt");
            Files.writeString(scriptFile, script, StandardCharsets.UTF_8);

            List<String> command = List.of(
                    executable,
                    "-f", scriptFile.toString(),
                    "-v", voice,
                    "--write-media", audioFile.toString(),
                    "--write-subtitles", subtitleFile.toString()
            );

            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            boolean finished = process.waitFor(TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("edge-tts timeout sau " + TIMEOUT);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("edge-tts thất bại (exit " + process.exitValue() + "): " + output);
            }
            if (!Files.exists(audioFile)) {
                throw new IllegalStateException("edge-tts chạy xong nhưng không thấy file audio: " + audioFile);
            }

            log.info("Job {} đã sinh audio tại {}", jobId, audioFile);
            return audioFile;
        } catch (IOException e) {
            throw new UncheckedIOException("Không gọi được tiến trình edge-tts", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị gián đoạn khi chờ edge-tts", e);
        }
    }
}
