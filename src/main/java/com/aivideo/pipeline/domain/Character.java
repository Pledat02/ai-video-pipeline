package com.aivideo.pipeline.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Nhân vật tái sử dụng được cho nhiều video - mô tả (description) được chèn vào
 * prompt sinh kịch bản + sinh ảnh để nhân vật xuất hiện nhất quán. Ảnh đại diện
 * (imageExt) chỉ để người dùng nhận diện trong thư viện, không dùng để AI vẽ theo.
 */
@Entity
@Table(name = "characters")
@Getter
@Setter
@NoArgsConstructor
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Đuôi file ảnh đại diện đã upload (png/jpg/webp), null nếu chưa có ảnh. */
    @Column(length = 10)
    private String imageExt;

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
