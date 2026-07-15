package com.aivideo.pipeline.service;

import com.aivideo.pipeline.domain.JobStatus;
import com.aivideo.pipeline.domain.VideoJob;
import com.aivideo.pipeline.repository.VideoJobRepository;
import com.aivideo.pipeline.service.media.ScriptGenerationService;
import com.aivideo.pipeline.service.media.ImageAgentRouter;
import com.aivideo.pipeline.service.media.RenderOptions;
import com.aivideo.pipeline.service.media.TextToSpeechService;
import com.aivideo.pipeline.service.media.VideoRenderService;
import com.aivideo.pipeline.service.media.YoutubeUploadService;
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

    /** Giai đoạn 1: sinh kịch bản rồi DỪNG LẠI chờ người duyệt. */
    @Async("pipelineExecutor")
    public void generateScript(Long jobId) {
        try {
            VideoJob job = updateStatus(jobId, JobStatus.SCRIPTING);
            String script = scriptService.generateScript(job.getTopic(), job.getSourceContent(), job.getTargetDurationSeconds(), job.getLanguage());
            job.setScriptContent(script);
            job.setStatus(JobStatus.SCRIPT_READY);
            jobRepository.save(job);
            log.info("Job {} đã sinh xong kịch bản, chờ duyệt", jobId);
        } catch (Exception e) {
            markFailed(jobId, "Lỗi sinh kịch bản: " + e.getMessage());
        }
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
                        job.getImageStyle(), job.getAspectRatio());
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
