package com.aivideo.pipeline.service;

import com.aivideo.pipeline.domain.JobStatus;
import com.aivideo.pipeline.domain.VideoJob;
import com.aivideo.pipeline.repository.VideoJobRepository;
import com.aivideo.pipeline.repository.VideoShotRepository;
import com.aivideo.pipeline.service.media.ScriptGenerationService;
import com.aivideo.pipeline.service.media.ImageAgentRouter;
import com.aivideo.pipeline.service.media.RenderOptions;
import com.aivideo.pipeline.service.media.TextToSpeechService;
import com.aivideo.pipeline.service.media.VideoRenderService;
import com.aivideo.pipeline.service.media.YoutubeUploadService;
import com.aivideo.pipeline.service.media.StoryboardSoundDesignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Files;

/**
 * Trái tim của hệ thống: chạy pipeline bất đồng bộ, cập nhật trạng thái từng bước.
 * Chú ý pattern: mỗi bước load lại job từ DB, đổi status, save - để dashboard
 * luôn thấy tiến độ realtime, và nếu app crash giữa chừng vẫn biết đang ở bước nào.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineOrchestrator {

    @Value("${pipeline.work-dir}")
    private String workDir;

    private final VideoJobRepository jobRepository;
    private final ScriptGenerationService scriptService;
    private final TextToSpeechService ttsService;
    private final ImageAgentRouter imageAgentRouter;
    private final VideoRenderService renderService;
    private final YoutubeUploadService uploadService;
    private final VideoShotRepository shotRepository;
    private final StoryboardSoundDesignService soundDesignService;

    /** Giai đoạn 1: sinh kịch bản rồi DỪNG LẠI chờ người duyệt. */
    @Async("pipelineExecutor")
    public void generateScript(Long jobId) {
        try {
            VideoJob job = updateStatus(jobId, JobStatus.SCRIPTING);
            String script = scriptService.generateScript(job.getTopic(), scriptSource(job), job.getTargetDurationSeconds(),
                    job.getLanguage(), job.getCharacterDescription());
            script = absorbCastManifest(job, script);
            job.setScriptContent(script);
            job.setStatus(JobStatus.SCRIPT_READY);
            jobRepository.save(job);
            log.info("Job {} đã sinh xong kịch bản, chờ duyệt", jobId);
        } catch (Exception e) {
            markFailed(jobId, "Lỗi sinh kịch bản: " + e.getMessage());
        }
    }

    private String scriptSource(VideoJob job) {
        String source = job.getSourceContent() == null ? "" : job.getSourceContent();
        if (!"storyboard_animatic".equals(job.getCreationMode())) return source;
        String cast = job.getCastDescription() == null ? "" : "\nDàn nhân vật phụ: " + job.getCastDescription();
        return """
                QUY TẮC HỘI THOẠI BẮT BUỘC:
                - Kịch bản phải là lời đối thoại trực tiếp của các nhân vật trong cảnh.
                - Không có người kể chuyện, lời dẫn, voice-over hoặc đoạn văn tường thuật.
                - Mỗi dòng phải theo định dạng TÊN NHÂN VẬT: câu thoại.
                - Chỉ viết những câu nhân vật thực sự nói thành tiếng; truyền tải hành động và cảm xúc qua lời thoại.
                - Không đọc mô tả góc máy, hiệu ứng, hành động hoặc tên cảnh.

                KHAI BÁO DÀN NHÂN VẬT BẮT BUỘC:
                - Mở đầu output bằng khối sau (không phải lời thoại, sẽ bị hệ thống tách ra):
                [CAST]
                TÊN NHÂN VẬT: loài (người/mèo/chim...), giới tính, tuổi, ngoại hình + trang phục ngắn gọn bằng tiếng Anh
                (một dòng cho MỖI nhân vật có thoại trong kịch bản)
                [/CAST]
                - Sau [/CAST] mới tới phần lời thoại.
                """ + cast + "\n\nPROMPT SẢN XUẤT:\n" + source;
    }

    private static final java.util.regex.Pattern CAST_BLOCK = java.util.regex.Pattern.compile(
            "(?is)\\[CAST\\]\\s*(.*?)\\s*\\[/CAST\\]\\s*");

    /**
     * Kịch bản chỉ chứa TÊN người nói ("AN: ...") - cái tên không cho model vẽ ảnh biết
     * AN là người hay mèo. LLM được yêu cầu khai báo khối [CAST] mô tả ngoại hình từng
     * nhân vật; tách khối đó vào castDescription (nguồn cho prompt vẽ ảnh) và cắt khỏi
     * kịch bản để TTS không đọc nó thành tiếng.
     */
    private String absorbCastManifest(VideoJob job, String script) {
        if (script == null || !"storyboard_animatic".equals(job.getCreationMode())) return script;
        java.util.regex.Matcher matcher = CAST_BLOCK.matcher(script);
        if (!matcher.find()) return script;
        String manifest = matcher.group(1).trim().replaceAll("\\R+", " | ");
        String existing = job.getCastDescription();
        job.setCastDescription(existing == null || existing.isBlank() ? manifest : existing + " | " + manifest);
        return matcher.replaceAll("").trim();
    }

    /** Giai đoạn 2: sau khi duyệt - chạy TTS -> render -> upload. */
    @Async("pipelineExecutor")
    public void produceAndUpload(Long jobId) {
        try {
            VideoJob job = updateStatus(jobId, JobStatus.GENERATING_AUDIO);
            Path audio = ttsService.synthesize(job.getScriptContent(), jobId, job.getVoice(),
                    job.getSpeechRatePercent() == null ? 0 : job.getSpeechRatePercent(),
                    job.getSubtitlesEnabled() == null || job.getSubtitlesEnabled());
            job.setAudioPath(audio.toString());

            if (!"none".equalsIgnoreCase(job.getImageAgent())) {
                job.setStatus(JobStatus.GENERATING_IMAGES);
                jobRepository.save(job);
                imageAgentRouter.generate(job.getImageAgent(), job.getTopic(), job.getScriptContent(),
                        job.getImageCount() == null ? 6 : job.getImageCount(), jobId,
                        job.getImageStyle(), job.getAspectRatio(), job.getCharacterDescription());
            }

            job.setStatus(JobStatus.RENDERING);
            jobRepository.save(job);
            RenderOptions renderOptions = new RenderOptions(
                    job.getSubtitlesEnabled() == null || job.getSubtitlesEnabled(),
                    job.getAspectRatio(), job.getSceneMotion(), findMusic(jobId),
                    job.getMusicVolumePercent() == null ? 18 : job.getMusicVolumePercent());
            Path video = renderService.render(audio, job.getScriptContent(), jobId, renderOptions);
            job.setVideoPath(video.toString());

            job.setStatus(JobStatus.UPLOADING);
            jobRepository.save(job);
            String videoId = uploadService.upload(video, job.getTopic());

            job.setYoutubeVideoId(videoId);
            job.setStatus(JobStatus.COMPLETED);
            jobRepository.save(job);
            log.info("Job {} hoàn thành, YouTube ID: {}", jobId, videoId);
        } catch (Exception e) {
            markFailed(jobId, "Lỗi sản xuất video: " + e.getMessage());
        }
    }

    @Async("pipelineExecutor")
    public void generateStoryboardKeyframes(Long jobId) {
        try {
            VideoJob job = updateStatus(jobId, JobStatus.GENERATING_KEYFRAMES);
            var shots = shotRepository.findByJobIdOrderByShotNumber(jobId);
            String shotScript = shots.stream().map(shot -> shot.getTitle() + ": " + shot.getVisualPrompt())
                    .collect(java.util.stream.Collectors.joining("\n"));
            imageAgentRouter.generate(job.getImageAgent(), job.getTopic(), shotScript, shots.size(), jobId,
                    "anime sakuga animatic", job.getAspectRatio(), combinedCharacterDescription(job));
            for (var shot : shots) {
                shot.setImageExt(findImageExt(jobId, shot.getShotNumber()));
                shot.setApproved(false);
            }
            shotRepository.saveAll(shots);
            job.setStatus(JobStatus.KEYFRAMES_REVIEW);
            jobRepository.save(job);
        } catch (Exception e) {
            markFailed(jobId, "Lỗi sinh keyframe storyboard: " + e.getMessage());
        }
    }

    /** Tiếp tục lượt sinh bị gián đoạn và không ghi đè các keyframe đã tồn tại. */
    @Async("pipelineExecutor")
    public void resumeStoryboardKeyframes(Long jobId) {
        try {
            VideoJob job = updateStatus(jobId, JobStatus.GENERATING_KEYFRAMES);
            var shots = shotRepository.findByJobIdOrderByShotNumber(jobId);
            for (var shot : shots) {
                String existing = findImageExtOrNull(jobId, shot.getShotNumber());
                if (existing != null) {
                    shot.setImageExt(existing);
                    continue;
                }
                imageAgentRouter.generateSingle(job.getImageAgent(), job.getTopic(), shot.getVisualPrompt(), jobId,
                        shot.getShotNumber(), "anime sakuga animatic", job.getAspectRatio(),
                        combinedCharacterDescription(job), shot.getSeed());
                shot.setImageExt(findImageExt(jobId, shot.getShotNumber()));
                shot.setApproved(false);
                shotRepository.save(shot);
            }
            shotRepository.saveAll(shots);
            job.setErrorMessage(null);
            job.setStatus(JobStatus.KEYFRAMES_REVIEW);
            jobRepository.save(job);
        } catch (Exception e) {
            markFailed(jobId, "Lỗi tiếp tục sinh keyframe: " + e.getMessage());
        }
    }

    @Async("pipelineExecutor")
    public void renderApprovedStoryboard(Long jobId) {
        try {
            VideoJob job = updateStatus(jobId, JobStatus.GENERATING_AUDIO);
            Path audio = ttsService.synthesize(job.getScriptContent(), jobId, job.getVoice(),
                    job.getSpeechRatePercent() == null ? 0 : job.getSpeechRatePercent(),
                    job.getSubtitlesEnabled() == null || job.getSubtitlesEnabled());
            job.setAudioPath(audio.toString());
            job.setStatus(JobStatus.RENDERING);
            jobRepository.save(job);
            var shots = shotRepository.findByJobIdOrderByShotNumber(jobId);
            Path soundDesign = soundDesignService.build(jobId, shots);
            Path backgroundTrack = findMusic(jobId);
            if (backgroundTrack == null) backgroundTrack = soundDesign;
            RenderOptions options = new RenderOptions(job.getSubtitlesEnabled() == null || job.getSubtitlesEnabled(),
                    job.getAspectRatio(), "anime_sakuga", backgroundTrack,
                    soundDesign != null ? 35 : (job.getMusicVolumePercent() == null ? 18 : job.getMusicVolumePercent()),
                    shots.stream().map(shot -> shot.getDurationSeconds() == null ? 2.0 : shot.getDurationSeconds()).toList());
            Path video = renderService.render(audio, job.getScriptContent(), jobId, options);
            job.setVideoPath(video.toString());
            job.setStatus(JobStatus.UPLOADING);
            jobRepository.save(job);
            job.setYoutubeVideoId(uploadService.upload(video, job.getTopic()));
            job.setStatus(JobStatus.COMPLETED);
            jobRepository.save(job);
        } catch (Exception e) {
            markFailed(jobId, "Lỗi render storyboard: " + e.getMessage());
        }
    }

    /** Mô tả C1 + cast phụ theo format thống nhất; marker "Supporting cast:" được
     * McpImageGenerationService dùng để chọn câu đếm nhân vật phù hợp (1 vs nhiều). */
    public static String combinedCharacterDescription(VideoJob job) {
        String main = job.getCharacterDescription() == null ? "" : job.getCharacterDescription();
        if (job.getCastDescription() == null || job.getCastDescription().isBlank()) return main;
        return "Main character: " + main + " Supporting cast: " + job.getCastDescription();
    }

    private String findImageExt(Long jobId, int index) {
        String found = findImageExtOrNull(jobId, index);
        if (found != null) return found;
        throw new IllegalStateException("Không tìm thấy keyframe P" + String.format("%02d", index));
    }

    private String findImageExtOrNull(Long jobId, int index) {
        for (String ext : java.util.List.of("png", "jpg", "jpeg", "webp")) {
            if (Files.exists(Path.of(workDir).resolve("job-" + jobId + "-image-" + index + "." + ext))) return ext;
        }
        return null;
    }

    private Path findMusic(Long jobId) {
        for (String ext : java.util.List.of("mp3", "wav", "m4a")) {
            Path candidate = Path.of(workDir).resolve("job-" + jobId + "-music." + ext);
            if (Files.exists(candidate)) return candidate;
        }
        return null;
    }

    private VideoJob updateStatus(Long jobId, JobStatus status) {
        VideoJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(status);
        return jobRepository.save(job);
    }

    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    private void markFailed(Long jobId, String message) {
        log.error("Job {} thất bại: {}", jobId, message);
        String truncated = message.length() > ERROR_MESSAGE_MAX_LENGTH
                ? message.substring(0, ERROR_MESSAGE_MAX_LENGTH)
                : message;
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(truncated);
            jobRepository.save(job);
        });
    }
}
