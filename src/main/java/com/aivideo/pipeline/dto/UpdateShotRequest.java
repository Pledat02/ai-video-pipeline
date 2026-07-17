package com.aivideo.pipeline.dto;

public record UpdateShotRequest(String narration, String visualPrompt, String camera, Double durationSeconds) {}
