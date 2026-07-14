package com.aivideo.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body của POST /api/jobs */
public record CreateJobRequest(
        @NotBlank(message = "Chủ đề không được để trống")
        @Size(max = 500, message = "Chủ đề tối đa 500 ký tự")
        String topic
) {}
