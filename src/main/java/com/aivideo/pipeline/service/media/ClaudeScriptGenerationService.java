package com.aivideo.pipeline.service.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sinh kịch bản bằng Claude Messages API.
 * Kích hoạt khi pipeline.script-generation.provider=claude.
 */
@Service
@ConditionalOnProperty(prefix = "pipeline.script-generation", name = "provider", havingValue = "claude")
@Slf4j
public class ClaudeScriptGenerationService implements ScriptGenerationService {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;
    private final int maxTokens;

    public ClaudeScriptGenerationService(
            RestClient.Builder restClientBuilder,
            @Value("${pipeline.script-generation.claude.api-key}") String apiKey,
            @Value("${pipeline.script-generation.claude.model}") String model,
            @Value("${pipeline.script-generation.claude.max-tokens}") int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ANTHROPIC_API_KEY chưa được set nhưng pipeline.script-generation.provider=claude");
        }
        this.model = model;
        this.maxTokens = maxTokens;
        this.restClient = restClientBuilder
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public String generateScript(String topic, String sourceContent, Integer targetDurationSeconds, String language) {
        String duration = targetDurationSeconds == null ? "3-5 phút" : "khoảng " + targetDurationSeconds + " giây";
        String material = sourceContent == null || sourceContent.isBlank() ? "" : "\nTư liệu nguồn cần bám sát:\n" + sourceContent;
        topic = topic + "\nThời lượng mục tiêu: " + duration + "\nNgôn ngữ đầu ra: " + languageName(language) + material;
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", """
                        Bạn là biên kịch chuyên viết kịch bản video kể chuyện ngắn (3-5 phút khi đọc).
                        Chỉ trả về lời thoại đọc trực tiếp, không thêm ghi chú đạo diễn hay tiêu đề.
                        """,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", "Viết kịch bản video về chủ đề: " + topic
                ))
        );

        String rawResponse = restClient.post()
                .uri("/messages")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        return extractText(rawResponse);
    }

    private String languageName(String code) {
        return Map.of("vi", "Vietnamese", "en", "English", "ja", "Japanese", "ko", "Korean", "zh-CN", "Simplified Chinese")
                .getOrDefault(code, "Vietnamese");
    }

    private String extractText(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    return block.path("text").asText();
                }
            }
            throw new IllegalStateException("Claude response không có text block: " + rawResponse);
        } catch (Exception e) {
            log.error("Lỗi parse response từ Claude: {}", rawResponse, e);
            throw new IllegalStateException("Không parse được response từ Claude API", e);
        }
    }
}
