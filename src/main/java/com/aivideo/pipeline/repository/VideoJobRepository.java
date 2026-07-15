package com.aivideo.pipeline.repository;

import com.aivideo.pipeline.domain.JobStatus;
import com.aivideo.pipeline.domain.VideoJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoJobRepository extends JpaRepository<VideoJob, Long> {

    List<VideoJob> findByStatusOrderByCreatedAtAsc(JobStatus status);

    Page<VideoJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
