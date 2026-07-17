package com.aivideo.pipeline.service;

import com.aivideo.pipeline.domain.JobStatus;
import com.aivideo.pipeline.domain.VideoJob;
import com.aivideo.pipeline.domain.VideoShot;
import com.aivideo.pipeline.dto.UpdateShotRequest;
import com.aivideo.pipeline.exception.InvalidStateException;
import com.aivideo.pipeline.exception.NotFoundException;
import com.aivideo.pipeline.repository.VideoJobRepository;
import com.aivideo.pipeline.repository.VideoShotRepository;
import com.aivideo.pipeline.service.media.ImageAgentRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShotPlanService {
    private static final Pattern SHOT_PATTERN = Pattern.compile(
            "(?is)P(\\d{1,2})\\s*:\\s*(.*?)(?=\\s*P\\d{1,2}\\s*:|$)");

    private final VideoShotRepository shotRepository;
    private final VideoJobRepository jobRepository;
    private final PipelineOrchestrator orchestrator;
    private final ImageAgentRouter imageAgentRouter;

    @Value("${pipeline.work-dir}")
    private String workDir;
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    @Transactional
    public List<VideoShot> createPlan(VideoJob job) {
        shotRepository.deleteByJobId(job.getId());
        int shotCount = Math.max(1, Math.min(job.getImageCount() == null ? 12 : job.getImageCount(), 48));
        List<String> visuals = parseVisuals(job.getDirectorPrompt() == null ? job.getSourceContent() : job.getDirectorPrompt(), shotCount);
        List<String> narrationParts = splitNarration(job.getScriptContent(), shotCount);
        double totalDuration = job.getTargetDurationSeconds() == null
                ? Math.max(15, wordCount(job.getScriptContent()) / 2.4) : job.getTargetDurationSeconds();
        List<VideoShot> shots = new ArrayList<>();
        for (int i = 0; i < shotCount; i++) {
            VideoShot shot = new VideoShot();
            shot.setJobId(job.getId());
            shot.setShotNumber(i + 1);
            shot.setTitle("P" + String.format("%02d", i + 1));
            shot.setNarration(narrationParts.get(i));
            shot.setVisualPrompt(visuals.get(i));
            shot.setCamera(inferCamera(visuals.get(i), i));
            int words = Math.max(1, wordCount(narrationParts.get(i)));
            int allWords = Math.max(shotCount, wordCount(job.getScriptContent()));
            shot.setDurationSeconds(Math.max(1.2, totalDuration * words / allWords));
            shot.setSeed(Math.abs((job.getId() + ":" + (i + 1)).hashCode()) * 1L);
            shot.setApproved(false);
            shots.add(shot);
        }
        return shotRepository.saveAll(shots);
    }

    public List<VideoShot> findByJob(Long jobId) {
        return shotRepository.findByJobIdOrderByShotNumber(jobId);
    }

    @Transactional
    public VideoShot update(Long jobId, int shotNumber, UpdateShotRequest request) {
        VideoShot shot = findShot(jobId, shotNumber);
        if (request.narration() != null) shot.setNarration(request.narration().trim());
        if (request.visualPrompt() != null) shot.setVisualPrompt(request.visualPrompt().trim());
        if (request.camera() != null) shot.setCamera(request.camera().trim());
        if (request.durationSeconds() != null) shot.setDurationSeconds(Math.max(0.5, request.durationSeconds()));
        shot.setApproved(false);
        return shotRepository.save(shot);
    }

    @Transactional
    public VideoShot approveShot(Long jobId, int shotNumber, boolean approved) {
        VideoShot shot = findShot(jobId, shotNumber);
        if (approved && shot.getImageExt() == null) throw new InvalidStateException("Cảnh chưa có keyframe để duyệt");
        shot.setApproved(approved);
        return shotRepository.save(shot);
    }

    @Transactional
    public VideoShot uploadKeyframe(Long jobId, int shotNumber, MultipartFile file) {
        VideoShot shot = findShot(jobId, shotNumber);
        if (file == null || file.isEmpty() || !IMAGE_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Keyframe phải là PNG, JPEG hoặc WebP");
        }
        String ext = "image/png".equals(file.getContentType()) ? "png"
                : "image/webp".equals(file.getContentType()) ? "webp" : "jpg";
        try {
            Files.createDirectories(Path.of(workDir));
            clearShotImage(jobId, shotNumber);
            file.transferTo(Path.of(workDir).resolve("job-" + jobId + "-image-" + shotNumber + "." + ext));
            shot.setImageExt(ext);
            shot.setApproved(false);
            return shotRepository.save(shot);
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được keyframe P" + String.format("%02d", shotNumber), e);
        }
    }

    @Transactional
    public VideoShot changeSeed(Long jobId, int shotNumber) {
        VideoShot shot = findShot(jobId, shotNumber);
        shot.setSeed(Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong()));
        shot.setApproved(false);
        return shotRepository.save(shot);
    }

    public VideoShot regenerate(Long jobId, int shotNumber, UpdateShotRequest request, boolean newSeed) {
        VideoJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy job id=" + jobId));
        if (job.getStatus() != JobStatus.KEYFRAMES_REVIEW) {
            throw new InvalidStateException("Chỉ tạo lại ảnh ở bước duyệt keyframe");
        }
        VideoShot shot = update(jobId, shotNumber, request == null
                ? new UpdateShotRequest(null, null, null, null) : request);
        if (newSeed) {
            shot.setSeed(Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong()));
            shot = shotRepository.save(shot);
        }
        try {
            clearShotImage(jobId, shotNumber);
            String cast = (job.getCharacterDescription() == null ? "" : job.getCharacterDescription())
                    + (job.getCastDescription() == null ? "" : " | " + job.getCastDescription());
            imageAgentRouter.generateSingle(job.getImageAgent(), job.getTopic(), shot.getVisualPrompt(), jobId,
                    shotNumber, "anime sakuga animatic", job.getAspectRatio(), cast, shot.getSeed());
            shot.setImageExt(detectImageExt(jobId, shotNumber));
            shot.setApproved(false);
            return shotRepository.save(shot);
        } catch (IOException e) {
            throw new UncheckedIOException("Không chuẩn bị được file keyframe", e);
        }
    }

    private void clearShotImage(Long jobId, int shotNumber) throws IOException {
        for (String ext : List.of("png", "jpg", "jpeg", "webp")) {
            Files.deleteIfExists(Path.of(workDir).resolve("job-" + jobId + "-image-" + shotNumber + "." + ext));
        }
    }

    private String detectImageExt(Long jobId, int shotNumber) {
        for (String ext : List.of("png", "jpg", "jpeg", "webp")) {
            if (Files.exists(Path.of(workDir).resolve("job-" + jobId + "-image-" + shotNumber + "." + ext))) return ext;
        }
        throw new IllegalStateException("Agent không trả về ảnh cho P" + String.format("%02d", shotNumber));
    }

    public VideoJob approvePlan(Long jobId) {
        VideoJob job = jobRepository.findById(jobId).orElseThrow(() -> new NotFoundException("Không tìm thấy job id=" + jobId));
        if (job.getStatus() != JobStatus.SHOT_PLAN_READY) throw new InvalidStateException("Shot Plan chưa sẵn sàng để duyệt");
        job.setStatus(JobStatus.GENERATING_KEYFRAMES);
        job.setErrorMessage(null);
        job = jobRepository.save(job);
        orchestrator.generateStoryboardKeyframes(jobId);
        return job;
    }

    public VideoJob renderApproved(Long jobId) {
        VideoJob job = jobRepository.findById(jobId).orElseThrow(() -> new NotFoundException("Không tìm thấy job id=" + jobId));
        if (job.getStatus() != JobStatus.KEYFRAMES_REVIEW) throw new InvalidStateException("Job chưa ở bước duyệt keyframe");
        List<VideoShot> shots = findByJob(jobId);
        int expected = Math.max(1, job.getImageCount() == null ? shots.size() : job.getImageCount());
        if (shots.size() != expected || shots.stream().anyMatch(s -> !Boolean.TRUE.equals(s.getApproved()))) {
            throw new InvalidStateException("Cần duyệt đủ " + expected + " keyframe trước khi render");
        }
        job.setStatus(JobStatus.APPROVED);
        job = jobRepository.save(job);
        orchestrator.renderApprovedStoryboard(jobId);
        return job;
    }

    private VideoShot findShot(Long jobId, int shotNumber) {
        return shotRepository.findByJobIdAndShotNumber(jobId, shotNumber)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy P" + String.format("%02d", shotNumber)));
    }

    private List<String> parseVisuals(String prompt, int shotCount) {
        List<String> result = new ArrayList<>();
        Matcher matcher = SHOT_PATTERN.matcher(prompt == null ? "" : prompt);
        while (matcher.find() && result.size() < shotCount) result.add(matcher.group(2).trim());
        while (result.size() < shotCount) result.add("Anime sakuga keyframe P" + String.format("%02d", result.size() + 1));
        return result;
    }

    private List<String> splitNarration(String script, int shotCount) {
        String[] parts = (script == null ? "" : script).split("(?<=[.!?])\\s+|\\R+");
        List<StringBuilder> buckets = new ArrayList<>();
        for (int i = 0; i < shotCount; i++) buckets.add(new StringBuilder());
        for (int i = 0; i < parts.length; i++) buckets.get(Math.min(shotCount - 1, i * shotCount / Math.max(1, parts.length)))
                .append(parts[i].trim()).append(' ');
        List<String> result = buckets.stream().map(value -> value.toString().trim()).toList();
        if (result.stream().allMatch(String::isBlank)) return java.util.Collections.nCopies(shotCount, "");
        return result;
    }

    private int wordCount(String text) { return text == null || text.isBlank() ? 0 : text.trim().split("\\s+").length; }

    private String inferCamera(String visual, int index) {
        String value = visual.toLowerCase();
        if (value.contains("snap-zoom") || value.contains("snap zoom")) return "snap zoom";
        if (value.contains("xoay vòng") || value.contains("orbital")) return "orbital";
        if (value.contains("góc thấp") || value.contains("low")) return "low angle tracking";
        if (value.contains("cận") || value.contains("close")) return "close-up push-in";
        return index % 2 == 0 ? "cinematic push-in" : "lateral tracking";
    }
}
