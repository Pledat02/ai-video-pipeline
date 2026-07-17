package com.aivideo.pipeline.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Nhân vật tái sử dụng: mô tả được chèn vào prompt và imageExt được Local MCP dùng
 * làm identity reference. Với character sheet ngang, service tự lấy vùng hero thay
 * vì truyền toàn bộ các ô. Storyboard vẫn chỉ dùng để định hướng bố cục từng cảnh.
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

    /** Lưới 4x3 = 12 ô; ô thứ N được cắt ra làm ảnh khởi tạo img2img cho cảnh N. */
    @Column(length = 10)
    private String storyboardImageExt;

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
