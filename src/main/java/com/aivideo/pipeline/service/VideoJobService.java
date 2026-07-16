package com.aivideo.pipeline.service;

import com.aivideo.pipeline.domain.JobStatus;
import com.aivideo.pipeline.domain.VideoJob;
import com.aivideo.pipeline.dto.CreateJobRequest;
import com.aivideo.pipeline.dto.ReproduceRequest;
import com.aivideo.pipeline.dto.UpdateScriptRequest;
import com.aivideo.pipeline.exception.InvalidStateException;
import com.aivideo.pipeline.exception.NotFoundException;
import com.aivideo.pipeline.repository.VideoJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Business logic + kiểm soát chuyển trạng thái hợp lệ.
 * Controller mỏng, service dày - pattern chuẩn.
 */
@Service
@RequiredArgsConstructor
public class VideoJobService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp"
    );

    private final VideoJobRepository jobRepository;
    private final PipelineOrchestrator orchestrator;

    @Value("${pipeline.work-dir}")
    private String workDir;

    @Transactional
    public VideoJob createJob(CreateJobRequest request) {
        return createJob(request, List.of(), null);
    }

    @Transactional
    public VideoJob createJob(CreateJobRequest request, List<MultipartFile> files, MultipartFile musicFile) {
        String topic = clean(request.topic());
        String source = clean(request.sourceContent());
        String script = clean(request.scriptContent());
        if (topic == null && source == null && script == null && (files == null || files.isEmpty())) {
            throw new IllegalArgumentException("Hãy nhập chủ đề, nội dung, kịch bản hoặc chọn ảnh");
        }
        if (request.targetDurationSeconds() != null &&
                (request.targetDurationSeconds() < 15 || request.targetDurationSeconds() > 3600)) {
            throw new IllegalArgumentException("Thời lượng phải từ 15 giây đến 60 phút");
        }
        VideoJob job = new VideoJob();
        job.setTopic(topic != null ? topic : source != null ? abbreviate(source) : "Video từ ảnh");
        job.setSourceContent(source);
        job.setScriptContent(script);
        job.setTargetDurationSeconds(request.targetDurationSeconds());
        job.setVoice(clean(request.voice()));
        job.setImageAgent(request.imageAgent() != null && Set.of("gemini", "mcp").contains(request.imageAgent()) ? request.imageAgent() : "none");
        job.setImageCount(Math.max(1, Math.min(request.imageCount() == null ? 6 : request.imageCount(), 12)));
        job.setLanguage(normalizeLanguage(request.language()));
        job.setSpeechRatePercent(Math.max(-50, Math.min(request.speechRatePercent() == null ? 0 : request.speechRatePercent(), 100)));
        job.setSubtitlesEnabled(request.subtitlesEnabled() == null || request.subtitlesEnabled());
        job.setAspectRatio(request.aspectRatio() != null && Set.of("16:9", "9:16", "1:1", "4:5").contains(request.aspectRatio()) ? request.aspectRatio() : "16:9");
        job.setImageStyle(clean(request.imageStyle()) == null ? "cinematic" : clean(request.imageStyle()));
        job.setSceneMotion("kenburns".equals(request.sceneMotion()) ? "kenburns" : "none");
        job.setMusicVolumePercent(Math.max(0, Math.min(request.musicVolumePercent() == null ? 18 : request.musicVolumePercent(), 100)));
        if (script != null) job.setStatus(JobStatus.SCRIPT_READY);
        job = jobRepository.save(job);
        if (files != null && !files.isEmpty()) uploadImages(job.getId(), files);
        if (musicFile != null && !musicFile.isEmpty()) saveMusic(job.getId(), musicFile);
        if (script != null) return job;
        orchestrator.generateScript(job.getId()); // chạy nền, trả response ngay
        return job;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String abbreviate(String value) {
        return value.length() <= 120 ? value : value.substring(0, 117) + "...";
    }

    private static String normalizeLanguage(String value) {
        return value != null && Set.of("vi", "en", "ja", "ko", "zh-CN").contains(value) ? value : "vi";
    }

    private void saveMusic(Long jobId, MultipartFile file) {
        String contentType = file.getContentType();
        if (!Set.of("audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4").contains(contentType)) {
            throw new IllegalArgumentException("Nhạc nền chỉ nhận MP3, WAV hoặc M4A");
        }
        String ext = contentType.contains("wav") ? "wav" : contentType.equals("audio/mp4") ? "m4a" : "mp3";
        try {
            Path dir = Path.of(workDir);
            Files.createDirectories(dir);
            for (String oldExt : List.of("mp3", "wav", "m4a")) Files.deleteIfExists(dir.resolve("job-" + jobId + "-music." + oldExt));
            file.transferTo(dir.resolve("job-" + jobId + "-music." + ext));
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được nhạc nền", e);
        }
    }

    public Page<VideoJob> findAll(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        return jobRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize));
    }

    public VideoJob findById(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy job id=" + id));
    }

    /** Người dùng chỉnh sửa kịch bản trước khi duyệt. */
    @Transactional
    public VideoJob updateScript(Long id, UpdateScriptRequest request) {
        VideoJob job = findById(id);
        requireStatus(job, JobStatus.SCRIPT_READY, "Chỉ sửa được kịch bản khi job đang chờ duyệt");
        job.setScriptContent(request.scriptContent());
        return jobRepository.save(job);
    }

    /** Duyệt kịch bản -> kích hoạt giai đoạn sản xuất. */
    @Transactional
    public VideoJob approveScript(Long id) {
        VideoJob job = findById(id);
        requireStatus(job, JobStatus.SCRIPT_READY, "Job không ở trạng thái chờ duyệt");
        job.setStatus(JobStatus.APPROVED);
        job = jobRepository.save(job);
        orchestrator.produceAndUpload(job.getId());
        return job;
    }

    /**
     * Sản xuất lại video cho job đã COMPLETED - dùng khi vừa upload ảnh mới hoặc
     * muốn đổi giọng đọc/ngôn ngữ/tốc độ/phụ đề/phong cách ảnh/cấu hình render.
     * Chạy lại từ bước TTS với kịch bản hiện có; field nào trong request bị bỏ
     * trống (null) thì giữ nguyên giá trị đang lưu trên job.
     * KHÔNG @Transactional: orchestrator.produceAndUpload chạy @Async ở thread khác
     * và đọc lại job từ DB ngay lập tức - nếu bọc trong 1 transaction ở đây, save()
     * chưa commit kịp thì thread async sẽ đọc phải dữ liệu cũ (mất các option vừa đổi).
     * jobRepository.save() tự commit ngay trong lời gọi của chính nó khi không có
     * transaction bao ngoài, nên bỏ @Transactional đảm bảo dữ liệu mới đã commit
     * trước khi produceAndUpload được gọi.
     */
    public VideoJob reproduce(Long id, ReproduceRequest request) {
        VideoJob job = findById(id);
        requireStatus(job, JobStatus.COMPLETED, "Chỉ sản xuất lại được job đã COMPLETED");
        applyReproduceOptions(job, request);
        job.setErrorMessage(null);
        job.setYoutubeVideoId(null);
        job.setStatus(JobStatus.APPROVED);
        job = jobRepository.save(job);
        orchestrator.produceAndUpload(job.getId());
        return job;
    }

    private void applyReproduceOptions(VideoJob job, ReproduceRequest request) {
        if (request == null) return;
        if (clean(request.voice()) != null) job.setVoice(clean(request.voice()));
        if (request.imageAgent() != null) {
            job.setImageAgent(Set.of("gemini", "mcp").contains(request.imageAgent()) ? request.imageAgent() : "none");
        }
        if (request.imageCount() != null) job.setImageCount(Math.max(1, Math.min(request.imageCount(), 12)));
        if (request.language() != null) job.setLanguage(normalizeLanguage(request.language()));
        if (request.speechRatePercent() != null) job.setSpeechRatePercent(Math.max(-50, Math.min(request.speechRatePercent(), 100)));
        if (request.subtitlesEnabled() != null) job.setSubtitlesEnabled(request.subtitlesEnabled());
        if (request.aspectRatio() != null && Set.of("16:9", "9:16", "1:1", "4:5").contains(request.aspectRatio())) {
            job.setAspectRatio(request.aspectRatio());
        }
        if (clean(request.imageStyle()) != null) job.setImageStyle(clean(request.imageStyle()));
        if (request.sceneMotion() != null) job.setSceneMotion("kenburns".equals(request.sceneMotion()) ? "kenburns" : "none");
        if (request.musicVolumePercent() != null) job.setMusicVolumePercent(Math.max(0, Math.min(request.musicVolumePercent(), 100)));
    }

    /** Chạy lại job bị lỗi từ đầu giai đoạn sản xuất. */
    @Transactional
    public VideoJob retry(Long id) {
        VideoJob job = findById(id);
        requireStatus(job, JobStatus.FAILED, "Chỉ retry được job đã FAILED");
        job.setErrorMessage(null);
        if (job.getScriptContent() == null) {
            job.setStatus(JobStatus.PENDING);
            job = jobRepository.save(job);
            orchestrator.generateScript(job.getId());
        } else {
            job.setStatus(JobStatus.APPROVED);
            job = jobRepository.save(job);
            orchestrator.produceAndUpload(job.getId());
        }
        return job;
    }

    private static final int MAX_IMAGE_COUNT = 20;

    /**
     * Upload nhiều ảnh cho 1 job -> FFmpeg dựng thành slideshow (chia đều thời lượng
     * theo audio) khi render, thay vì nền màu đặc. Mỗi lần gọi thay thế toàn bộ bộ ảnh cũ.
     * Sắp xếp theo TÊN FILE gốc (không theo thứ tự trong request) vì trình duyệt gửi
     * file theo thứ tự người dùng chọn trong file picker - không dự đoán được; đặt tên
     * file 01-, 02-... là cách người dùng kiểm soát thứ tự slideshow.
     */
    public VideoJob uploadImages(Long id, List<MultipartFile> files) {
        VideoJob job = findById(id);
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new IllegalArgumentException("Chưa chọn ảnh nào");
        }
        if (files.size() > MAX_IMAGE_COUNT) {
            throw new IllegalArgumentException("Tối đa " + MAX_IMAGE_COUNT + " ảnh mỗi job");
        }
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
                throw new IllegalArgumentException("Chỉ chấp nhận ảnh PNG, JPEG hoặc WebP");
            }
        }
        List<MultipartFile> sorted = files.stream()
                .sorted(java.util.Comparator.comparing(
                        f -> f.getOriginalFilename() == null ? "" : f.getOriginalFilename(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        try {
            Path dir = Path.of(workDir);
            Files.createDirectories(dir);
            clearExistingImages(dir, id);
            int index = 1;
            for (MultipartFile file : sorted) {
                String ext = switch (file.getContentType()) {
                    case "image/png" -> "png";
                    case "image/webp" -> "webp";
                    default -> "jpg";
                };
                Path dest = dir.resolve("job-" + id + "-image-" + index + "." + ext);
                file.transferTo(dest);
                index++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được ảnh", e);
        }
        return job;
    }

    private void clearExistingImages(Path dir, Long id) throws IOException {
        for (int i = 1; i <= MAX_IMAGE_COUNT; i++) {
            for (String ext : List.of("png", "jpg", "jpeg", "webp")) {
                Files.deleteIfExists(dir.resolve("job-" + id + "-image-" + i + "." + ext));
            }
        }
    }

    private void requireStatus(VideoJob job, JobStatus expected, String message) {
        if (job.getStatus() != expected) {
            throw new InvalidStateException(message + " (hiện tại: " + job.getStatus() + ")");
        }
    }
}
