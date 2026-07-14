package com.aivideo.pipeline.service;

import com.aivideo.pipeline.domain.JobStatus;
import com.aivideo.pipeline.domain.VideoJob;
import com.aivideo.pipeline.dto.CreateJobRequest;
import com.aivideo.pipeline.dto.UpdateScriptRequest;
import com.aivideo.pipeline.exception.InvalidStateException;
import com.aivideo.pipeline.exception.NotFoundException;
import com.aivideo.pipeline.repository.VideoJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
        VideoJob job = new VideoJob();
        job.setTopic(request.topic());
        job = jobRepository.save(job);
        orchestrator.generateScript(job.getId()); // chạy nền, trả response ngay
        return job;
    }

    public List<VideoJob> findAll() {
        return jobRepository.findAllByOrderByCreatedAtDesc();
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
     * theo audio) khi render, thay vì nền màu đặc. Mỗi lần gọi thay thế toàn bộ bộ ảnh
     * cũ, thứ tự lưu đúng theo thứ tự file trong request.
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
        try {
            Path dir = Path.of(workDir);
            Files.createDirectories(dir);
            clearExistingImages(dir, id);
            int index = 1;
            for (MultipartFile file : files) {
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
