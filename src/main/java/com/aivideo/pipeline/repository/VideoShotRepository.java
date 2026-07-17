package com.aivideo.pipeline.repository;

import com.aivideo.pipeline.domain.VideoShot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoShotRepository extends JpaRepository<VideoShot, Long> {
    List<VideoShot> findByJobIdOrderByShotNumber(Long jobId);
    Optional<VideoShot> findByJobIdAndShotNumber(Long jobId, Integer shotNumber);
    void deleteByJobId(Long jobId);
}
