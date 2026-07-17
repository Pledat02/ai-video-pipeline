package com.aivideo.pipeline.service.media;

import java.text.Normalizer;
import java.util.Locale;

/** Chuyển cảm xúc ẩn trong nội dung cảnh thành chỉ dẫn hình thể rõ ràng cho image model. */
final class SceneEmotionAnalyzer {
    private SceneEmotionAnalyzer() {}

    static String cue(String scene) {
        String value = normalize(scene);
        if (contains(value, "so hai", "hoang so", "run ray", "scared", "afraid", "fear", "startled"))
            return " Emotion: visibly frightened; widened eyes, lowered ears, tense compact posture, trembling body language.";
        if (contains(value, "buon", "that vong", "co don", "khoc", "sad", "lonely", "disappointed", "cry"))
            return " Emotion: sad and vulnerable; softened watery eyes, lowered gaze, drooping ears and tail, withdrawn posture.";
        if (contains(value, "tuc gian", "gian du", "angry", "furious", "rage"))
            return " Emotion: angry and defensive; narrowed eyes, tense jaw, ears angled back, forceful forward posture.";
        if (contains(value, "quyet tam", "dung cam", "hy vong", "determined", "brave", "hope"))
            return " Emotion: determined and hopeful; focused bright eyes, lifted head, stable forward posture.";
        if (contains(value, "nhe nhom", "doan tu", "tim thay", "relieved", "reunion", "found", "embrace"))
            return " Emotion: overwhelming relief and affection; softened smiling eyes, relaxed body, warm physical closeness.";
        if (contains(value, "vui", "cuoi", "hanh phuc", "playful", "happy", "joy", "laugh", "excited"))
            return " Emotion: joyful and playful; bright open eyes, lifted ears and tail, energetic relaxed movement.";
        if (contains(value, "to mo", "ngac nhien", "curious", "wonder", "surprised"))
            return " Emotion: curious surprise; attentive wide eyes, forward ears, head tilt and alert posture.";
        return " Emotion: natural scene-appropriate expression with readable eyes, face, ears, tail and body language.";
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
