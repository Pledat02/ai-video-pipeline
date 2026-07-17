package com.aivideo.pipeline.service.media;

import com.aivideo.pipeline.domain.VideoShot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class StoryboardSoundDesignService {
    private final Path workDir;
    private final Path libraryDir;
    private final String ffmpeg;

    public StoryboardSoundDesignService(@Value("${pipeline.work-dir}") String workDir,
            @Value("${pipeline.sound-effects.library-dir:./sound-effects}") String libraryDir,
            @Value("${pipeline.video-render.ffmpeg.executable:ffmpeg}") String ffmpeg) {
        this.workDir = Path.of(workDir);
        this.libraryDir = Path.of(libraryDir);
        this.ffmpeg = ffmpeg;
    }

    /** Uses local effects when available; otherwise creates a procedural crowd/impact bed. */
    public Path build(Long jobId, List<VideoShot> shots) {
        try {
            Files.createDirectories(workDir);
            double duration = Math.max(5, shots.stream().mapToDouble(s -> s.getDurationSeconds() == null ? 2 : s.getDurationSeconds()).sum());
            Path output = workDir.resolve("job-" + jobId + "-sound-design.wav");
            List<Path> local = List.of("crowd.wav", "footsteps.wav", "ball.wav", "impact.wav", "net.wav").stream()
                    .map(libraryDir::resolve).filter(Files::isRegularFile).toList();
            List<String> command = new ArrayList<>(List.of(ffmpeg, "-y"));
            if (local.isEmpty()) {
                command.addAll(List.of("-f", "lavfi", "-i", "anoisesrc=color=pink:amplitude=0.035:duration=" + fmt(duration),
                        "-f", "lavfi", "-i", "sine=frequency=74:duration=" + fmt(duration),
                        "-filter_complex", "[0:a]highpass=f=120,lowpass=f=6500[crowd];[1:a]volume=0.035,tremolo=f=2.4:d=0.85[pulse];[crowd][pulse]amix=inputs=2:duration=longest,afade=t=in:st=0:d=0.5,afade=t=out:st="
                                + fmt(Math.max(0, duration - .5)) + ":d=0.5[out]", "-map", "[out]"));
            } else {
                for (Path effect : local) command.addAll(List.of("-stream_loop", "-1", "-i", effect.toString()));
                StringBuilder filter = new StringBuilder();
                for (int i = 0; i < local.size(); i++) filter.append("[").append(i).append(":a]atrim=duration=")
                        .append(fmt(duration)).append(",volume=").append(i == 0 ? "0.28" : "0.16")
                        .append("[s").append(i).append("];");
                for (int i = 0; i < local.size(); i++) filter.append("[s").append(i).append("]");
                filter.append("amix=inputs=").append(local.size()).append(":duration=longest[out]");
                command.addAll(List.of("-filter_complex", filter.toString(), "-map", "[out]"));
            }
            command.addAll(List.of("-t", fmt(duration), "-c:a", "pcm_s16le", output.toString()));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String logOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(Duration.ofMinutes(2).toSeconds(), TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("FFmpeg sound design thất bại: " + logOutput);
            }
            return output;
        } catch (IOException | RuntimeException e) {
            log.warn("Không tạo được sound design, tiếp tục không SFX: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String fmt(double value) { return String.format(Locale.ROOT, "%.3f", value); }
}
