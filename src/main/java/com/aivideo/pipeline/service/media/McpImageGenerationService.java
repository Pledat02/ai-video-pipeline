package com.aivideo.pipeline.service.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class McpImageGenerationService implements ImageGenerationService {
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final int MAX_IMAGE_BYTES = 15 * 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final Path workDir;
    private final String serverUrl;
    private final String toolName;
    private final String authToken;
    private final boolean allowRemote;
    private final RestClient client;
    private final AtomicLong requestIds = new AtomicLong(1);

    public McpImageGenerationService(ObjectMapper objectMapper, RestClient.Builder builder,
            @Value("${pipeline.work-dir}") String workDir,
            @Value("${pipeline.image-generation.mcp.server-url:}") String serverUrl,
            @Value("${pipeline.image-generation.mcp.tool-name:generate_image}") String toolName,
            @Value("${pipeline.image-generation.mcp.auth-token:}") String authToken,
            @Value("${pipeline.image-generation.mcp.allow-remote:false}") boolean allowRemote,
            @Value("${pipeline.image-generation.mcp.timeout-seconds:180}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.workDir = Path.of(workDir);
        this.serverUrl = serverUrl;
        this.toolName = toolName;
        this.authToken = authToken;
        this.allowRemote = allowRemote;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)));
        this.client = builder.requestFactory(requestFactory).build();
    }

    @Override
    public String provider() {
        return "mcp";
    }

    @Override
    public void generateImages(String topic, String script, int count, Long jobId,
            String imageStyle, String aspectRatio) {
        URI endpoint = validateEndpoint();
        try {
            Files.createDirectories(workDir);
            String sessionId = initialize(endpoint);
            sendInitialized(endpoint, sessionId);
            String[] scenes = script.split("(?<=[.!?])\\s+|\\R+");
            int[] dimensions = dimensions(aspectRatio);
            for (int i = 0; i < count; i++) {
                String scene = scenes.length == 0 ? script : scenes[Math.min(i * scenes.length / count, scenes.length - 1)];
                Map<String, Object> arguments = new LinkedHashMap<>();
                arguments.put("prompt", "Create a " + imageStyle + " scene with consistent characters, no text. Topic: " + topic + ". Scene: " + scene);
                arguments.put("negativePrompt", "text, subtitles, watermark, logo, blurry, low quality");
                arguments.put("width", dimensions[0]);
                arguments.put("height", dimensions[1]);
                arguments.put("style", imageStyle);
                arguments.put("aspectRatio", aspectRatio);
                arguments.put("sceneIndex", i + 1);
                arguments.put("seed", Math.abs((jobId + ":" + (i + 1)).hashCode()));
                JsonNode result = rpc(endpoint, sessionId, "tools/call",
                        Map.of("name", toolName, "arguments", arguments));
                saveImage(result, jobId, i + 1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("MCP image agent thất bại: " + e.getMessage(), e);
        }
    }

    private String initialize(URI endpoint) throws Exception {
        Map<String, Object> params = Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "ai-video-pipeline", "version", "0.1.0"));
        ResponseEntity<String> response = post(endpoint, null, request("initialize", params, true));
        JsonNode root = parseTransportBody(response.getBody());
        if (root.has("error")) throw new IllegalStateException(root.path("error").path("message").asText("MCP initialize error"));
        return response.getHeaders().getFirst("Mcp-Session-Id");
    }

    private void sendInitialized(URI endpoint, String sessionId) {
        post(endpoint, sessionId, Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
    }

    private JsonNode rpc(URI endpoint, String sessionId, String method, Map<String, Object> params) throws Exception {
        ResponseEntity<String> response = post(endpoint, sessionId, request(method, params, true));
        JsonNode root = parseTransportBody(response.getBody());
        if (root.has("error")) throw new IllegalStateException(root.path("error").path("message").asText("MCP protocol error"));
        JsonNode result = root.path("result");
        if (result.path("isError").asBoolean(false)) {
            throw new IllegalStateException(result.path("content").toString());
        }
        return result;
    }

    private ResponseEntity<String> post(URI endpoint, String sessionId, Map<String, Object> body) {
        RestClient.RequestBodySpec spec = client.post().uri(endpoint)
                .header("Accept", "application/json, text/event-stream")
                .header("Content-Type", "application/json")
                .header("MCP-Protocol-Version", PROTOCOL_VERSION);
        if (sessionId != null && !sessionId.isBlank()) spec.header("Mcp-Session-Id", sessionId);
        if (authToken != null && !authToken.isBlank()) spec.header("Authorization", "Bearer " + authToken);
        return spec.body(body).retrieve().toEntity(String.class);
    }

    private Map<String, Object> request(String method, Map<String, Object> params, boolean withId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        if (withId) request.put("id", requestIds.getAndIncrement());
        request.put("method", method);
        request.put("params", params);
        return request;
    }

    private JsonNode parseTransportBody(String body) throws Exception {
        if (body == null || body.isBlank()) return objectMapper.createObjectNode();
        if (!body.stripLeading().startsWith("data:")) return objectMapper.readTree(body);
        JsonNode last = null;
        for (String line : body.split("\\R")) {
            if (line.startsWith("data:")) last = objectMapper.readTree(line.substring(5).trim());
        }
        if (last == null) throw new IllegalStateException("MCP SSE response không có data");
        return last;
    }

    private void saveImage(JsonNode result, Long jobId, int index) throws Exception {
        for (JsonNode block : result.path("content")) {
            if ("image".equals(block.path("type").asText()) && block.hasNonNull("data")) {
                writeBase64(block.path("data").asText(), block.path("mimeType").asText("image/png"), jobId, index);
                return;
            }
            JsonNode resource = block.path("resource");
            if ("resource".equals(block.path("type").asText()) && resource.hasNonNull("blob")) {
                writeBase64(resource.path("blob").asText(), resource.path("mimeType").asText("image/png"), jobId, index);
                return;
            }
        }
        JsonNode structured = result.path("structuredContent");
        if (structured.hasNonNull("imageBase64")) {
            writeBase64(structured.path("imageBase64").asText(), structured.path("mimeType").asText("image/png"), jobId, index);
            return;
        }
        throw new IllegalStateException("Tool " + toolName + " không trả về image/base64");
    }

    private void writeBase64(String data, String mimeType, Long jobId, int index) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(data);
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) throw new IllegalStateException("Ảnh MCP rỗng hoặc lớn hơn 15 MB");
        String ext = mimeType.contains("jpeg") ? "jpg" : mimeType.contains("webp") ? "webp" : "png";
        Files.write(workDir.resolve("job-" + jobId + "-image-" + index + "." + ext), bytes);
    }

    private URI validateEndpoint() {
        if (serverUrl == null || serverUrl.isBlank()) throw new IllegalStateException("MCP_IMAGE_SERVER_URL chưa được cấu hình");
        URI uri = URI.create(serverUrl);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException("MCP server chỉ hỗ trợ http/https");
        }
        String host = uri.getHost();
        boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!local && !allowRemote) throw new IllegalStateException("MCP remote bị chặn; đặt MCP_IMAGE_ALLOW_REMOTE=true nếu máy chủ đáng tin cậy");
        return uri;
    }

    private int[] dimensions(String aspectRatio) {
        return switch (aspectRatio == null ? "16:9" : aspectRatio) {
            case "9:16" -> new int[]{720, 1280};
            case "1:1" -> new int[]{1024, 1024};
            case "4:5" -> new int[]{1024, 1280};
            default -> new int[]{1280, 720};
        };
    }
}
