package com.aivideo.pipeline.controller;

import com.aivideo.pipeline.dto.CreateJobRequest;
import com.aivideo.pipeline.dto.JobPageResponse;
import com.aivideo.pipeline.dto.JobResponse;
import com.aivideo.pipeline.dto.ReproduceRequest;
import com.aivideo.pipeline.dto.UpdateScriptRequest;
import com.aivideo.pipeline.dto.ShotResponse;
import com.aivideo.pipeline.dto.UpdateShotRequest;
import com.aivideo.pipeline.service.VideoJobService;
import com.aivideo.pipeline.service.ShotPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST API của hệ thống. Luồng sử dụng:
 * 1. POST /api/jobs                    -> tạo job, hệ thống tự sinh kịch bản nền
 * 2. GET  /api/jobs/{id}               -> theo dõi trạng thái, đọc kịch bản
 * 3. PUT  /api/jobs/{id}/script        -> (tuỳ chọn) sửa kịch bản
 * 4. POST /api/jobs/{id}/images        -> (tuỳ chọn) upload nhiều ảnh -> dựng slideshow khi render
 * 5. POST /api/jobs/{id}/approve       -> duyệt -> tự động TTS + render + upload
 * 6. POST /api/jobs/{id}/retry         -> chạy lại nếu FAILED
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class VideoJobController {

    private final VideoJobService jobService;
    private final ShotPlanService shotPlanService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse create(@Valid @RequestBody CreateJobRequest request) {
        return JobResponse.from(jobService.createJob(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createAdvanced(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String sourceContent,
            @RequestParam(required = false) String scriptContent,
            @RequestParam(required = false) Long characterId,
            @RequestParam(required = false) Integer targetDurationSeconds,
            @RequestParam(required = false) String voice,
            @RequestParam(defaultValue = "mcp") String imageAgent,
            @RequestParam(defaultValue = "6") Integer imageCount,
            @RequestParam(defaultValue = "vi") String language,
            @RequestParam(defaultValue = "0") Integer speechRatePercent,
            @RequestParam(defaultValue = "true") Boolean subtitlesEnabled,
            @RequestParam(defaultValue = "16:9") String aspectRatio,
            @RequestParam(defaultValue = "cinematic") String imageStyle,
            @RequestParam(defaultValue = "none") String sceneMotion,
            @RequestParam(defaultValue = "18") Integer musicVolumePercent,
            @RequestParam(defaultValue = "standard") String creationMode,
            @RequestParam(required = false) String directorPrompt,
            @RequestParam(required = false) Long characterC2Id,
            @RequestParam(required = false) Long characterC3Id,
            @RequestParam(required = false) Long characterC4Id,
            @RequestParam(required = false) List<Long> castCharacterIds,
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) MultipartFile musicFile) {
        return JobResponse.from(jobService.createJob(new CreateJobRequest(topic, sourceContent, scriptContent,
                characterId, targetDurationSeconds, voice, imageAgent, imageCount, language, speechRatePercent,
                subtitlesEnabled, aspectRatio, imageStyle, sceneMotion, musicVolumePercent, creationMode,
                directorPrompt, characterC2Id, characterC3Id, characterC4Id, castCharacterIds), files, musicFile));
    }

    @GetMapping
    public JobPageResponse findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return JobPageResponse.from(jobService.findAll(page, size));
    }

    @GetMapping("/{id}")
    public JobResponse findById(@PathVariable Long id) {
        return JobResponse.from(jobService.findById(id));
    }

    @PutMapping("/{id}/script")
    public JobResponse updateScript(@PathVariable Long id,
                                    @Valid @RequestBody UpdateScriptRequest request) {
        return JobResponse.from(jobService.updateScript(id, request));
    }

    @PostMapping("/{id}/images")
    public JobResponse uploadImages(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files) {
        return JobResponse.from(jobService.uploadImages(id, files));
    }

    @PostMapping("/{id}/approve")
    public JobResponse approve(@PathVariable Long id) {
        return JobResponse.from(jobService.approveScript(id));
    }

    @PostMapping("/{id}/retry")
    public JobResponse retry(@PathVariable Long id) {
        return JobResponse.from(jobService.retry(id));
    }

    @PostMapping("/{id}/reproduce")
    public JobResponse reproduce(@PathVariable Long id, @RequestBody(required = false) ReproduceRequest request) {
        return JobResponse.from(jobService.reproduce(id, request));
    }

    @GetMapping("/{id}/shots")
    public List<ShotResponse> shots(@PathVariable Long id) {
        return shotPlanService.findByJob(id).stream().map(ShotResponse::from).toList();
    }

    @PutMapping("/{id}/shots/{shotNumber}")
    public ShotResponse updateShot(@PathVariable Long id, @PathVariable int shotNumber,
            @RequestBody UpdateShotRequest request) {
        return ShotResponse.from(shotPlanService.update(id, shotNumber, request));
    }

    @PostMapping("/{id}/shots/approve-plan")
    public JobResponse approveShotPlan(@PathVariable Long id) {
        return JobResponse.from(shotPlanService.approvePlan(id));
    }

    @PostMapping("/{id}/shots/resume")
    public JobResponse resumeKeyframes(@PathVariable Long id) {
        return JobResponse.from(shotPlanService.resumeKeyframes(id));
    }

    @PostMapping("/{id}/shots/{shotNumber}/approval")
    public ShotResponse approveShot(@PathVariable Long id, @PathVariable int shotNumber,
            @RequestParam(defaultValue = "true") boolean approved) {
        return ShotResponse.from(shotPlanService.approveShot(id, shotNumber, approved));
    }

    @PostMapping(value = "/{id}/shots/{shotNumber}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ShotResponse uploadShotImage(@PathVariable Long id, @PathVariable int shotNumber,
            @RequestParam MultipartFile file) {
        return ShotResponse.from(shotPlanService.uploadKeyframe(id, shotNumber, file));
    }

    @PostMapping("/{id}/shots/{shotNumber}/new-seed")
    public ShotResponse newShotSeed(@PathVariable Long id, @PathVariable int shotNumber) {
        return ShotResponse.from(shotPlanService.changeSeed(id, shotNumber));
    }

    @PostMapping("/{id}/shots/{shotNumber}/regenerate")
    public ShotResponse regenerateShot(@PathVariable Long id, @PathVariable int shotNumber,
            @RequestParam(defaultValue = "true") boolean newSeed,
            @RequestBody(required = false) UpdateShotRequest request) {
        return ShotResponse.from(shotPlanService.regenerate(id, shotNumber, request, newSeed));
    }

    @PostMapping("/{id}/shots/render")
    public JobResponse renderShots(@PathVariable Long id) {
        return JobResponse.from(shotPlanService.renderApproved(id));
    }
}
