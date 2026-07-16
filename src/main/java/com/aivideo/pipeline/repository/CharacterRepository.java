package com.aivideo.pipeline.repository;

import com.aivideo.pipeline.domain.Character;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterRepository extends JpaRepository<Character, Long> {

    List<Character> findAllByOrderByCreatedAtDesc();
}
