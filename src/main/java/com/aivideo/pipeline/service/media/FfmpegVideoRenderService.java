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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Render video bằng FFmpeg: slideshow nhiều ảnh (chia đều thời lượng theo audio,
 * mỗi ảnh là 1 input riêng ghép bằng filter "concat" - KHÔNG dùng concat demuxer,
 * vì demuxer + "-r" output cho ra thời lượng sai lệch với ảnh tĩnh, xem ghi chú ở
 * buildSlideshowFilterComplex) hoặc nền màu đặc nếu job không có ảnh nào, cộng phụ
 * đề (nếu có file .srt do bước TTS sinh ra).
 * Kích hoạt khi pipeline.video-render.provider=ffmpeg.
 */
@Service
@ConditionalOnProperty(prefix = "pipeline.video-render", name = "provider", havingValue = "ffmpeg")
@Slf4j
public class FfmpegVideoRenderService implements VideoRenderService {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp");
    private static final int MAX_IMAGE_COUNT = 20;

    private final Path workDir;
    private final String executable;
    private final String probeExecutable;
    private final String resolution;
    private final String backgroundColor;
    private final int fontSize;

    public FfmpegVideoRenderService(
            @Value("${pipeline.work-dir}") String workDir,
            @Value("${pipeline.video-render.ffmpeg.executable}") String executable,
            @Value("${pipeline.video-render.ffmpeg.probe-executable}") String probeExecutable,
            @Value("${pipeline.video-render.ffmpeg.resolution}") String resolution,
            @Value("${pipeline.video-render.ffmpeg.background-color}") String backgroundColor,
            @Value("${pipeline.video-render.ffmpeg.font-size}") int fontSize) {
        this.workDir = Path.of(workDir);
        this.executable = executable;
        this.probeExecutable = probeExecutable;
        this.resolution = resolution;
        this.backgroundColor = backgroundColor;
        this.fontSize = fontSize;
    }

    @Override
    public Path render(Path audioPath, String script, Long jobId, RenderOptions options) {
        try {
            Files.createDirectories(workDir);
            Path videoFile = workDir.resolve("job-" + jobId + "-video.mp4");
            // Đặt tên trùng convention của EdgeTtsTextToSpeechService - nếu TTS provider
            // khác không sinh subtitle, đơn giản là bỏ qua bước burn phụ đề.
            Path subtitleFile = workDir.resolve("job-" + jobId + "-subtitle.srt");
            List<Path> images = findImages(jobId);
            boolean hasSubtitle = options.subtitlesEnabled() && Files.exists(subtitleFile);
            String outputResolution = resolutionFor(options.aspectRatio());
            Path musicPath = options.musicPath();
            boolean hasMusic = musicPath != null && Files.exists(musicPath);
            if (!hasSubtitle) {
                log.warn("Job {} không tìm thấy file subtitle {}, render video không phụ đề", jobId, subtitleFile);
            }

            List<String> command = new ArrayList<>(List.of(executable, "-y"));
            String output;

            if (!images.isEmpty()) {
                double audioDuration = probeAudioDurationSeconds(audioPath);
                double perImageDuration = audioDuration / images.size();
                String perImageDurationStr = String.format(Locale.ROOT, "%.3f", perImageDuration);

                for (Path image : images) {
                    command.addAll(List.of("-loop", "1", "-t", perImageDurationStr, "-i", image.toString()));
                }
                int audioInputIndex = images.size();
                command.addAll(List.of("-i", audioPath.toString()));
                int musicInputIndex = audioInputIndex + 1;
                if (hasMusic) command.addAll(List.of("-stream_loop", "-1", "-i", musicPath.toString()));

                command.add("-filter_complex");
                String filters = buildSlideshowFilterComplex(images.size(), hasSubtitle, subtitleFile,
                        outputResolution, "kenburns".equals(options.sceneMotion()));
                if (hasMusic) filters += ";" + audioMixFilter(audioInputIndex, musicInputIndex, options.musicVolumePercent());
                command.add(filters);
                command.addAll(List.of("-map", "[outv]", "-map", hasMusic ? "[outa]" : audioInputIndex + ":a"));
            } else {
                command.addAll(List.of("-f", "lavfi", "-i", "color=c=" + backgroundColor + ":s=" + outputResolution + ":r=25"));
                command.addAll(List.of("-i", audioPath.toString()));
                if (hasMusic) {
                    command.addAll(List.of("-stream_loop", "-1", "-i", musicPath.toString(), "-filter_complex",
                            audioMixFilter(1, 2, options.musicVolumePercent()), "-map", "0:v", "-map", "[outa]"));
                }
                if (hasSubtitle) {
                    command.add("-vf");
                    command.add(subtitlesFilter(subtitleFile));
                }
            }

            command.addAll(List.of(
                    "-shortest",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "192k",
                    videoFile.toString()
            ));

            output = runProcess(command, TIMEOUT);
            if (!Files.exists(videoFile)) {
                throw new IllegalStateException("ffmpeg chạy xong nhưng không thấy file video: " + videoFile
                        + " | log: " + lastLines(output, 20));
            }

            log.info("Job {} đã render video tại {} ({} ảnh)", jobId, videoFile, images.size());
            return videoFile;
        } catch (IOException e) {
            throw new UncheckedIOException("Không gọi được tiến trình ffmpeg", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị gián đoạn khi chờ ffmpeg", e);
        }
    }

    /**
     * Ghép nhiều ảnh bằng filter "concat" (không phải concat DEMUXER): mỗi ảnh đã là
     * 1 input riêng với "-loop 1 -t <duration>" nên có thời lượng chính xác ngay từ
     * input, không phụ thuộc việc ffmpeg tự lặp frame theo "-r" output như concat
     * demuxer (đã test thực tế: demuxer + "-r" output cho ra video dài hơn hẳn so
     * với tổng duration khai báo - sai lệch không dự đoán được).
     */
    private String buildSlideshowFilterComplex(int imageCount, boolean hasSubtitle, Path subtitleFile,
            String outputResolution, boolean kenBurns) {
        String[] dims = outputResolution.split("x");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < imageCount; i++) {
            sb.append("[").append(i).append(":v]scale=").append(dims[0]).append(":").append(dims[1])
                    .append(":force_original_aspect_ratio=decrease,pad=").append(dims[0]).append(":").append(dims[1])
                    .append(":(ow-iw)/2:(oh-ih)/2,setsar=1,");
            if (kenBurns) sb.append("zoompan=z='min(zoom+0.0008,1.12)':d=1:s=").append(outputResolution).append(":fps=25,");
            sb.append("fps=25[v").append(i).append("];");
        }
        for (int i = 0; i < imageCount; i++) {
            sb.append("[v").append(i).append("]");
        }
        sb.append("concat=n=").append(imageCount).append(":v=1:a=0");
        if (hasSubtitle) {
            sb.append("[concatv];[concatv]").append(subtitlesFilter(subtitleFile)).append("[outv]");
        } else {
            sb.append("[outv]");
        }
        return sb.toString();
    }

    private String audioMixFilter(int voiceIndex, int musicIndex, int volumePercent) {
        double volume = Math.max(0, Math.min(volumePercent, 100)) / 100.0;
        return "[" + musicIndex + ":a]volume=" + String.format(Locale.ROOT, "%.2f", volume)
                + "[bg];[" + voiceIndex + ":a][bg]amix=inputs=2:duration=first:dropout_transition=2[outa]";
    }

    private String resolutionFor(String aspectRatio) {
        return switch (aspectRatio == null ? "16:9" : aspectRatio) {
            case "9:16" -> "720x1280";
            case "1:1" -> "1080x1080";
            case "4:5" -> "1080x1350";
            default -> resolution;
        };
    }

    private String subtitlesFilter(Path subtitleFile) {
        return "subtitles='" + escapeForFilter(subtitleFile) + "':force_style='FontSize="
                + fontSize + ",PrimaryColour=&HFFFFFF&,OutlineColour=&H000000&,BorderStyle=1'";
    }

    /** Tìm các ảnh do người dùng upload qua POST /api/jobs/{id}/images, đúng thứ tự 1..N. */
    private List<Path> findImages(Long jobId) {
        List<Path> images = new ArrayList<>();
        for (int i = 1; i <= MAX_IMAGE_COUNT; i++) {
            for (String ext : IMAGE_EXTENSIONS) {
                Path candidate = workDir.resolve("job-" + jobId + "-image-" + i + "." + ext);
                if (Files.exists(candidate)) {
                    images.add(candidate);
                    break;
                }
            }
        }
        return images;
    }

    /** Gọi ffprobe để lấy độ dài audio (giây), dùng chia đều thời lượng slideshow. */
    private double probeAudioDurationSeconds(Path audioPath) throws IOException, InterruptedException {
        List<String> command = List.of(
                probeExecutable, "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                audioPath.toString()
        );
        String output = runProcess(command, PROBE_TIMEOUT);
        try {
            return Double.parseDouble(output.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Không đọc được duration audio từ ffprobe: " + output, e);
        }
    }

    private String runProcess(List<String> command, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(command.get(0) + " timeout sau " + timeout);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(command.get(0) + " thất bại (exit " + process.exitValue() + "): "
                    + lastLines(output, 20));
        }
        return output;
    }

    /** ffmpeg filter escaping: dùng "/" thay "\" và escape dấu ":" (ổ đĩa Windows kiểu C:). */
    private String escapeForFilter(Path path) {
        String s = path.toAbsolutePath().toString().replace("\\", "/");
        return s.replace(":", "\\:");
    }

    private String lastLines(String text, int n) {
        String[] lines = text.split("\\R");
        int from = Math.max(0, lines.length - n);
        return String.join("\n", Arrays.copyOfRange(lines, from, lines.length));
    }
}
