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
