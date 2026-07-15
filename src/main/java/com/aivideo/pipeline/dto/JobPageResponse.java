package com.aivideo.pipeline.dto;

import com.aivideo.pipeline.domain.VideoJob;
import org.springframework.data.domain.Page;

import java.util.List;

/** DTO phân trang cho danh sách job. */
public record JobPageResponse(
        List<JobResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static JobPageResponse from(Page<VideoJob> page) {
        return new JobPageResponse(
                page.getContent().stream().map(JobResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
