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
 * Sinh kịch bản bằng Gemini API (Google AI Studio) - có free tier thật sự,
 * dùng khi chưa muốn trả phí Anthropic API.
 * Kích hoạt khi pipeline.script-generation.provider=gemini.
 */
@Service
@ConditionalOnProperty(prefix = "pipeline.script-generation", name = "provider", havingValue = "gemini")
@Slf4j
public class GeminiScriptGenerationService implements ScriptGenerationService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;

    public GeminiScriptGenerationService(
            RestClient.Builder restClientBuilder,
            @Value("${pipeline.script-generation.gemini.api-key}") String apiKey,
            @Value("${pipeline.script-generation.gemini.model}") String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY chưa được set nhưng pipeline.script-generation.provider=gemini");
        }
        this.model = model;
        this.restClient = restClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public String generateScript(String topic) {
        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of(
                                "text", """
                                        Bạn là biên kịch chuyên viết kịch bản video kể chuyện ngắn (3-5 phút khi đọc).
                                        Chỉ trả về lời thoại đọc trực tiếp, không thêm ghi chú đạo diễn hay tiêu đề.
                                        """
                        ))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", "Viết kịch bản video về chủ đề: " + topic))
                ))
        );

        String rawResponse = restClient.post()
                .uri("/models/{model}:generateContent", model)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        return extractText(rawResponse);
    }

    private String extractText(String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode textNode = root.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode()) {
                throw new IllegalStateException("Gemini response không có text: " + rawResponse);
            }
            return textNode.asText();
        } catch (Exception e) {
            log.error("Lỗi parse response từ Gemini: {}", rawResponse, e);
            throw new IllegalStateException("Không parse được response từ Gemini API", e);
        }
    }
}
