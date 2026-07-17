package com.aivideo.pipeline.controller;

import com.aivideo.pipeline.dto.CharacterResponse;
import com.aivideo.pipeline.service.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Thư viện nhân vật: tạo 1 lần, dùng lại cho nhiều video (chọn qua characterId
 * khi tạo job hoặc sản xuất lại).
 */
@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CharacterResponse create(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile faceImage,
            @RequestParam(required = false) MultipartFile fullBodyImage,
            @RequestParam(required = false) MultipartFile outfitImage,
            @RequestParam(required = false) MultipartFile storyboardImage) {
        return CharacterResponse.from(characterService.create(name, description, image,
                faceImage, fullBodyImage, outfitImage, storyboardImage));
    }

    @GetMapping
    public List<CharacterResponse> findAll() {
        return characterService.findAll().stream().map(CharacterResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CharacterResponse findById(@PathVariable Long id) {
        return CharacterResponse.from(characterService.findById(id));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CharacterResponse update(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile faceImage,
            @RequestParam(required = false) MultipartFile fullBodyImage,
            @RequestParam(required = false) MultipartFile outfitImage,
            @RequestParam(required = false) MultipartFile storyboardImage) {
        return CharacterResponse.from(characterService.update(id, name, description, image,
                faceImage, fullBodyImage, outfitImage, storyboardImage));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        characterService.delete(id);
    }
}
