package com.aivideo.pipeline.dto;

import com.aivideo.pipeline.domain.Character;

import java.time.Instant;

public record CharacterResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
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
                character.getCreatedAt()
        );
    }
}
