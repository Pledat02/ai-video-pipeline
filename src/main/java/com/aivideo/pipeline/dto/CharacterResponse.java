package com.aivideo.pipeline.dto;

import com.aivideo.pipeline.domain.Character;

import java.time.Instant;

public record CharacterResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        String faceImageUrl,
        String fullBodyImageUrl,
        String outfitImageUrl,
        String storyboardImageUrl,
        Instant createdAt
) {
    public static CharacterResponse from(Character character) {
        String imageUrl = character.getImageExt() == null ? null
                : "/media/character-" + character.getId() + "." + character.getImageExt();
        return new CharacterResponse(
                character.getId(),
                character.getName(),
                character.getDescription(),
                imageUrl,
                mediaUrl(character, "face", character.getFaceImageExt()),
                mediaUrl(character, "full-body", character.getFullBodyImageExt()),
                mediaUrl(character, "outfit", character.getOutfitImageExt()),
                mediaUrl(character, "storyboard", character.getStoryboardImageExt()),
                character.getCreatedAt()
        );
    }

    private static String mediaUrl(Character character, String role, String ext) {
        return ext == null ? null : "/media/character-" + character.getId() + "-" + role + "." + ext;
    }
}
