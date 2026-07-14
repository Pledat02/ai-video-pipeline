package com.aivideo.pipeline.repository;

import com.aivideo.pipeline.domain.JobStatus;
import com.aivideo.pipeline.domain.VideoJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoJobRepository extends JpaRepository<VideoJob, Long> {

    List<VideoJob> findByStatusOrderByCreatedAtAsc(JobStatus status);

    List<VideoJob> findAllByOrderByCreatedAtDesc();
}
