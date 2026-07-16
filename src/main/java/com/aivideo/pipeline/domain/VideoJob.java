package com.aivideo.pipeline.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "video_jobs")
@Getter
@Setter
@NoArgsConstructor
public class VideoJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chủ đề/ý tưởng video do người dùng nhập, vd: "Truyện Tấm Cám kể theo phong cách hiện đại" */
    @Column(nullable = false, length = 500)
    private String topic;

    /** Kịch bản do LLM sinh ra, người dùng có thể sửa trước khi duyệt.
     * Không dùng @Lob: trên Postgres nó ép Hibernate đọc qua JDBC Large Object API,
     * yêu cầu phải nằm trong transaction (lỗi "Large Objects may not be used in
     * auto-commit mode" khi query ngoài @Transactional). columnDefinition=TEXT là đủ. */
    @Column(columnDefinition = "TEXT")
    private String scriptContent;

    @Column(columnDefinition = "TEXT")
    private String sourceContent;

    /** Nhân vật được chọn từ thư viện (bảng characters), có thể null nếu không dùng. */
    private Long characterId;

    /** Snapshot mô tả nhân vật tại thời điểm chọn - chèn vào mọi prompt sinh ảnh
     * và prompt sinh kịch bản để nhân vật xuất hiện nhất quán. Copy từ Character
     * thay vì tham chiếu trực tiếp, để sửa nhân vật trong thư viện sau này không
     * làm thay đổi ngược các job đã tạo trước đó. */
    @Column(columnDefinition = "TEXT")
    private String characterDescription;

    private Integer targetDurationSeconds;

    @Column(length = 100)
    private String voice;

    @Column(length = 30)
    private String imageAgent = "mcp";

    private Integer imageCount = 6;

    @Column(length = 10)
    private String language = "vi";

    private Integer speechRatePercent = 0;

    private Boolean subtitlesEnabled = true;

    @Column(length = 10)
    private String aspectRatio = "16:9";

    @Column(length = 255)
    private String imageStyle = "cinematic";

    @Column(length = 30)
    private String sceneMotion = "none";

    private Integer musicVolumePercent = 18;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status = JobStatus.PENDING;

    /** Đường dẫn file audio sau bước TTS */
    private String audioPath;

    /** Đường dẫn file video sau bước render FFmpeg */
    private String videoPath;

    /** ID video trên YouTube sau khi upload thành công */
    private String youtubeVideoId;

    /** Lưu message lỗi khi status = FAILED để dễ debug */
    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
