package com.aivideo.pipeline.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "video_shots", uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "shot_number"}))
@Getter
@Setter
@NoArgsConstructor
public class VideoShot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "shot_number", nullable = false)
    private Integer shotNumber;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String narration;

    @Column(columnDefinition = "TEXT")
    private String visualPrompt;

    @Column(length = 100)
    private String camera;

    private Double durationSeconds;
    private Long seed;
    private Boolean approved = false;

    @Column(length = 10)
    private String imageExt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
