package com.aivideo.pipeline.dto;

import jakarta.validation.constraints.NotBlank;

/** Body của PUT /api/jobs/{id}/script - người dùng sửa kịch bản trước khi duyệt */
public record UpdateScriptRequest(
        @NotBlank(message = "Kịch bản không được để trống")
        String scriptContent
) {}
