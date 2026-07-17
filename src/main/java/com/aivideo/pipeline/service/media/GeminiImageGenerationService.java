package com.aivideo.pipeline.service.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiImageGenerationService implements ImageGenerationService {
    private static final String SINGLE_FRAME_RULE = " Output exactly ONE single edge-to-edge full-frame image for this shot."
            + " No collage, grid, contact sheet, storyboard sheet, split screen, comic panels, borders, or multiple views.";
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final Path workDir;
    private final String apiKey;
    private final String model;

    public GeminiImageGenerationService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
            @Value("${pipeline.work-dir}") String workDir,
            @Value("${pipeline.image-generation.gemini.api-key:}") String apiKey,
            @Value("${pipeline.image-generation.gemini.model:gemini-2.5-flash-image}") String model) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.workDir = Path.of(workDir);
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public void generateImages(String topic, String script, int count, Long jobId, String imageStyle, String aspectRatio,
            String characterDescription) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Chưa có GEMINI_API_KEY để agent tạo ảnh hoạt động");
        }
        try {
            Files.createDirectories(workDir);
            String[] scenes = script.split("(?<=[.!?])\\s+|\\R+");
            RestClient client = restClientBuilder.baseUrl("https://generativelanguage.googleapis.com/v1beta")
                    .defaultHeader("x-goog-api-key", apiKey).build();
            String characterNote = characterDescription == null || characterDescription.isBlank() ? ""
                    : " Main character must look exactly the same in every image: " + characterDescription + ".";
            for (int i = 0; i < count; i++) {
                String scene = scenes.length == 0 ? script : scenes[Math.min(i * scenes.length / count, scenes.length - 1)];
                String shotDirection = AnimeSakugaPreset.enabled(imageStyle)
                        ? AnimeSakugaPreset.shotDirection(i, count) : "";
                String prompt = "Create a " + imageStyle + " " + aspectRatio + " film still, no text, consistent characters and visual style."
                        + SINGLE_FRAME_RULE + characterNote + " Video topic: " + topic + ". Scene: " + scene + shotDirection;
                Map<String, Object> body = Map.of(
                        "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                        "generationConfig", Map.of("responseModalities", List.of("IMAGE")));
                String raw = client.post().uri("/models/{model}:generateContent", model).body(body)
                        .retrieve().body(String.class);
                saveFirstImage(raw, jobId, i + 1);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không lưu được ảnh do Gemini tạo", e);
        }
    }

    @Override
    public String provider() {
        return "gemini";
    }

    @Override
    public void generateSingleImage(String topic, String visualPrompt, Long jobId, int outputIndex,
            String imageStyle, String aspectRatio, String characterDescription, long seed) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("Chưa có GEMINI_API_KEY");
        try {
            Files.createDirectories(workDir);
            String prompt = "Create one " + imageStyle + " " + aspectRatio + " keyframe, no text."
                    + SINGLE_FRAME_RULE + " Topic: " + topic + ". Shot: " + visualPrompt + ". Keep cast consistent: "
                    + (characterDescription == null ? "" : characterDescription) + ". Seed hint: " + seed;
            RestClient client = restClientBuilder.baseUrl("https://generativelanguage.googleapis.com/v1beta")
                    .defaultHeader("x-goog-api-key", apiKey).build();
            Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("responseModalities", List.of("IMAGE")));
            String raw = client.post().uri("/models/{model}:generateContent", model).body(body)
                    .retrieve().body(String.class);
            saveFirstImage(raw, jobId, outputIndex);
        } catch (IOException e) {
            throw new IllegalStateException("Không lưu được keyframe Gemini P" + outputIndex, e);
        }
    }

    private void saveFirstImage(String raw, Long jobId, int index) throws IOException {
        JsonNode parts = objectMapper.readTree(raw).path("candidates").path(0).path("content").path("parts");
        for (JsonNode part : parts) {
            JsonNode inline = part.path("inlineData");
            if (!inline.path("data").isMissingNode()) {
                String mime = inline.path("mimeType").asText("image/png");
                String ext = mime.contains("jpeg") ? "jpg" : mime.contains("webp") ? "webp" : "png";
                Files.write(workDir.resolve("job-" + jobId + "-image-" + index + "." + ext),
                        Base64.getDecoder().decode(inline.path("data").asText()));
                return;
            }
        }
        throw new IllegalStateException("Gemini không trả về ảnh cho cảnh " + index);
    }
}
