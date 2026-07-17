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
            MultipartFile faceImage, MultipartFile fullBodyImage, MultipartFile outfitImage,
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
        saveReferenceImages(character, faceImage, fullBodyImage, outfitImage, storyboardImage);
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
            MultipartFile faceImage, MultipartFile fullBodyImage, MultipartFile outfitImage,
            MultipartFile storyboardImage) {
        Character character = findById(id);
        if (clean(name) != null) character.setName(clean(name));
        if (description != null) character.setDescription(clean(description));
        character = repository.save(character);
        if (image != null && !image.isEmpty()) saveImage(character, image);
        saveReferenceImages(character, faceImage, fullBodyImage, outfitImage, storyboardImage);
        return character;
    }

    @Transactional
    public void delete(Long id) {
        Character character = findById(id);
        clearImage(character);
        clearReferenceImages(character);
        repository.delete(character);
    }

    private void saveImage(Character character, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Chỉ chấp nhận ảnh PNG, JPEG hoặc WebP");
        }
        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
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

    private void saveReferenceImages(Character character, MultipartFile face, MultipartFile fullBody,
            MultipartFile outfit, MultipartFile storyboard) {
        saveReferenceImage(character, "face", face);
        saveReferenceImage(character, "full-body", fullBody);
        saveReferenceImage(character, "outfit", outfit);
        saveReferenceImage(character, "storyboard", storyboard);
    }

    private void saveReferenceImage(Character character, String role, MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Chỉ chấp nhận ảnh PNG, JPEG hoặc WebP cho " + role);
        }
        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        try {
            Files.createDirectories(Path.of(workDir));
            String oldExt = referenceExt(character, role);
            if (oldExt != null) Files.deleteIfExists(referencePath(character.getId(), role, oldExt));
            file.transferTo(referencePath(character.getId(), role, ext));
            setReferenceExt(character, role, ext);
            repository.save(character);
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được ảnh tham chiếu " + role, e);
        }
    }

    private void clearReferenceImages(Character character) {
        for (String role : List.of("face", "full-body", "outfit", "storyboard")) {
            String ext = referenceExt(character, role);
            if (ext == null) continue;
            try { Files.deleteIfExists(referencePath(character.getId(), role, ext)); } catch (IOException ignored) {}
        }
    }

    private Path referencePath(Long id, String role, String ext) {
        return Path.of(workDir).resolve("character-" + id + "-" + role + "." + ext);
    }

    private String referenceExt(Character character, String role) {
        return switch (role) {
            case "face" -> character.getFaceImageExt();
            case "full-body" -> character.getFullBodyImageExt();
            case "outfit" -> character.getOutfitImageExt();
            default -> character.getStoryboardImageExt();
        };
    }

    private void setReferenceExt(Character character, String role, String ext) {
        switch (role) {
            case "face" -> character.setFaceImageExt(ext);
            case "full-body" -> character.setFullBodyImageExt(ext);
            case "outfit" -> character.setOutfitImageExt(ext);
            default -> character.setStoryboardImageExt(ext);
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
