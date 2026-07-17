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
                """ + cast + "\n\nPROMPT SẢN XUẤT:\n" + source;
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

    private String combinedCharacterDescription(VideoJob job) {
        return (job.getCharacterDescription() == null ? "" : job.getCharacterDescription())
                + (job.getCastDescription() == null ? "" : " Supporting cast: " + job.getCastDescription());
    }

    private String findImageExt(Long jobId, int index) {
        for (String ext : java.util.List.of("png", "jpg", "jpeg", "webp")) {
            if (Files.exists(Path.of(workDir).resolve("job-" + jobId + "-image-" + index + "." + ext))) return ext;
        }
        throw new IllegalStateException("Không tìm thấy keyframe P" + String.format("%02d", index));
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
