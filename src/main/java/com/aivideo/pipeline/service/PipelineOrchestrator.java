package com.aivideo.pipeline.service;

import com.aivideo.pipeline.domain.JobStatus;
import com.aivideo.pipeline.domain.VideoJob;
import com.aivideo.pipeline.repository.VideoJobRepository;
import com.aivideo.pipeline.service.media.ScriptGenerationService;
import com.aivideo.pipeline.service.media.TextToSpeechService;
import com.aivideo.pipeline.service.media.VideoRenderService;
import com.aivideo.pipeline.service.media.YoutubeUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Trái tim của hệ thống: chạy pipeline bất đồng bộ, cập nhật trạng thái từng bước.
 * Chú ý pattern: mỗi bước load lại job từ DB, đổi status, save - để dashboard
 * luôn thấy tiến độ realtime, và nếu app crash giữa chừng vẫn biết đang ở bước nào.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineOrchestrator {

    private final VideoJobRepository jobRepository;
    private final ScriptGenerationService scriptService;
    private final TextToSpeechService ttsService;
    private final VideoRenderService renderService;
    private final YoutubeUploadService uploadService;

    /** Giai đoạn 1: sinh kịch bản rồi DỪNG LẠI chờ người duyệt. */
    @Async("pipelineExecutor")
    public void generateScript(Long jobId) {
        try {
            VideoJob job = updateStatus(jobId, JobStatus.SCRIPTING);
            String script = scriptService.generateScript(job.getTopic());
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
            Path audio = ttsService.synthesize(job.getScriptContent(), jobId);
            job.setAudioPath(audio.toString());

            job.setStatus(JobStatus.RENDERING);
            jobRepository.save(job);
            Path video = renderService.render(audio, job.getScriptContent(), jobId);
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
