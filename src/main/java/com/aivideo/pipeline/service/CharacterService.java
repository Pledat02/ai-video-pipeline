package com.aivideo.pipeline.service;

import com.aivideo.pipeline.domain.Character;
import com.aivideo.pipeline.exception.NotFoundException;
import com.aivideo.pipeline.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Thư viện nhân vật tái sử dụng được cho nhiều video. */
@Service
@RequiredArgsConstructor
public class CharacterService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final CharacterRepository repository;

    @Value("${pipeline.work-dir}")
    private String workDir;

    @Transactional
    public Character create(String name, String description, MultipartFile image,
            MultipartFile storyboardImage) {
        String cleanName = clean(name);
        if (cleanName == null) {
            throw new IllegalArgumentException("Tên nhân vật không được để trống");
        }
        Character character = new Character();
        character.setName(cleanName);
        character.setDescription(clean(description));
        character = repository.save(character);
        if (image != null && !image.isEmpty()) saveImage(character, image);
        saveStoryboardImage(character, storyboardImage);
        return character;
    }

    public List<Character> findAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public Character findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân vật id=" + id));
    }

    @Transactional
    public Character update(Long id, String name, String description, MultipartFile image,
            MultipartFile storyboardImage, boolean removeStoryboard) {
        Character character = findById(id);
        if (clean(name) != null) character.setName(clean(name));
        if (description != null) character.setDescription(clean(description));
        if (removeStoryboard) {
            clearStoryboardImage(character);
            character.setStoryboardImageExt(null);
        }
        character = repository.save(character);
        if (image != null && !image.isEmpty()) saveImage(character, image);
        saveStoryboardImage(character, storyboardImage);
        return character;
    }

    @Transactional
    public void delete(Long id) {
        Character character = findById(id);
        clearImage(character);
        clearStoryboardImage(character);
        repository.delete(character);
    }

    private void saveImage(Character character, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Chỉ chấp nhận ảnh PNG, JPEG hoặc WebP");
        }
        String ext = extensionOf(contentType);
        try {
            Path dir = Path.of(workDir);
            Files.createDirectories(dir);
            clearImage(character);
            file.transferTo(dir.resolve("character-" + character.getId() + "." + ext));
            character.setImageExt(ext);
            repository.save(character);
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được ảnh nhân vật", e);
        }
    }

    private void clearImage(Character character) {
        if (character.getImageExt() == null) return;
        try {
            Files.deleteIfExists(Path.of(workDir).resolve("character-" + character.getId() + "." + character.getImageExt()));
        } catch (IOException ignored) {
            // dọn file cũ thất bại không nên chặn thao tác chính
        }
    }

    private void saveStoryboardImage(Character character, MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Chỉ chấp nhận ảnh PNG, JPEG hoặc WebP cho storyboard");
        }
        String ext = extensionOf(contentType);
        try {
            Files.createDirectories(Path.of(workDir));
            clearStoryboardImage(character);
            file.transferTo(storyboardPath(character.getId(), ext));
            character.setStoryboardImageExt(ext);
            repository.save(character);
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được ảnh storyboard", e);
        }
    }

    private void clearStoryboardImage(Character character) {
        if (character.getStoryboardImageExt() == null) return;
        try {
            Files.deleteIfExists(storyboardPath(character.getId(), character.getStoryboardImageExt()));
        } catch (IOException ignored) {
            // dọn file cũ thất bại không nên chặn thao tác chính
        }
    }

    private Path storyboardPath(Long id, String ext) {
        return Path.of(workDir).resolve("character-" + id + "-storyboard." + ext);
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
