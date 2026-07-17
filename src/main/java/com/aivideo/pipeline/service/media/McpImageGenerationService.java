package com.aivideo.pipeline.service.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aivideo.pipeline.repository.VideoJobRepository;
import com.aivideo.pipeline.repository.CharacterRepository;
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
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

@Service
public class McpImageGenerationService implements ImageGenerationService {
    /** Phrased positively on purpose: a diffusion text encoder has no notion of negation, so
     * naming the forbidden layouts ("collage", "grid", "storyboard sheet") in the positive
     * prompt makes the model draw exactly those. At a fixed seed the previous negated wording
     * produced a 7-panel collage where this wording produced a single clean frame. Keep the
     * ban-list wording confined to SINGLE_FRAME_NEGATIVE below. */
    private static final String SINGLE_FRAME_RULE = " Single full-frame cinematic still,"
            + " one continuous camera view filling the entire frame.";
    /** Only meaningful for MCP servers that honour negativePrompt with CFG > 1; the bundled
     * ComfyUI bridge zeroes the negative conditioning and runs the turbo model at CFG 1, so
     * this is inert there rather than a second line of defence. */
    private static final String SINGLE_FRAME_NEGATIVE = "collage, grid, contact sheet, storyboard sheet,"
            + " split screen, comic panels, multiple panels, diptych, triptych, inset frame, borders";
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
    private final VideoJobRepository jobRepository;
    private final CharacterRepository characterRepository;

    public McpImageGenerationService(ObjectMapper objectMapper, RestClient.Builder builder,
            VideoJobRepository jobRepository, CharacterRepository characterRepository,
            @Value("${pipeline.work-dir}") String workDir,
            @Value("${pipeline.image-generation.mcp.server-url:}") String serverUrl,
            @Value("${pipeline.image-generation.mcp.tool-name:generate_image}") String toolName,
            @Value("${pipeline.image-generation.mcp.auth-token:}") String authToken,
            @Value("${pipeline.image-generation.mcp.allow-remote:false}") boolean allowRemote,
            @Value("${pipeline.image-generation.mcp.timeout-seconds:180}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.jobRepository = jobRepository;
        this.characterRepository = characterRepository;
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
            String imageStyle, String aspectRatio, String characterDescription) {
        URI endpoint = validateEndpoint();
        try {
            Files.createDirectories(workDir);
            String sessionId = initialize(endpoint);
            sendInitialized(endpoint, sessionId);
            String[] scenes = script.split("(?<=[.!?])\\s+|\\R+");
            int[] dimensions = dimensions(aspectRatio);
            String characterNote = characterDescription == null || characterDescription.isBlank() ? ""
                    : " Main character must look exactly the same in every image: " + characterDescription + ".";
            for (int i = 0; i < count; i++) {
                String scene = scenes.length == 0 ? script : scenes[Math.min(i * scenes.length / count, scenes.length - 1)];
                String shotDirection = AnimeSakugaPreset.enabled(imageStyle)
                        ? AnimeSakugaPreset.shotDirection(i, count) : "";
                Map<String, Object> arguments = new LinkedHashMap<>();
                arguments.put("prompt", "Create a " + imageStyle + " scene with consistent characters, no text."
                        + SINGLE_FRAME_RULE + characterNote + " Topic: " + topic + ". Scene: " + scene + shotDirection);
                arguments.put("negativePrompt", "text, subtitles, watermark, logo, blurry, low quality, "
                        + SINGLE_FRAME_NEGATIVE);
                arguments.put("width", dimensions[0]);
                arguments.put("height", dimensions[1]);
                arguments.put("style", imageStyle);
                arguments.put("aspectRatio", aspectRatio);
                arguments.put("sceneIndex", i + 1);
                arguments.put("seed", Math.abs((jobId + ":" + (i + 1)).hashCode()));
                addStoryboardReference(arguments, jobId, i + 1);
                JsonNode result = rpc(endpoint, sessionId, "tools/call",
                        Map.of("name", toolName, "arguments", arguments));
                saveImage(result, jobId, i + 1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("MCP image agent thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public void generateSingleImage(String topic, String visualPrompt, Long jobId, int outputIndex,
            String imageStyle, String aspectRatio, String characterDescription, long seed) {
        URI endpoint = validateEndpoint();
        try {
            Files.createDirectories(workDir);
            String sessionId = initialize(endpoint);
            sendInitialized(endpoint, sessionId);
            int[] dimensions = dimensions(aspectRatio);
            String characterNote = characterDescription == null || characterDescription.isBlank() ? ""
                    : " Main character and cast must remain consistent: " + characterDescription + ".";
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("prompt", "Create a " + imageStyle + " keyframe, no text." + SINGLE_FRAME_RULE
                    + characterNote + " Topic: " + topic + ". Shot: " + visualPrompt);
            arguments.put("negativePrompt", "text, subtitles, watermark, logo, blurry, malformed anatomy,"
                    + " duplicate characters, " + SINGLE_FRAME_NEGATIVE);
            arguments.put("width", dimensions[0]);
            arguments.put("height", dimensions[1]);
            arguments.put("seed", seed);
            arguments.put("sceneIndex", outputIndex);
            addStoryboardReference(arguments, jobId, outputIndex);
            JsonNode result = rpc(endpoint, sessionId, "tools/call", Map.of("name", toolName, "arguments", arguments));
            saveImage(result, jobId, outputIndex);
        } catch (Exception e) {
            throw new IllegalStateException("MCP không tạo lại được P" + String.format("%02d", outputIndex)
                    + ": " + e.getMessage(), e);
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

    private void addStoryboardReference(Map<String, Object> arguments, Long jobId, int shotNumber) {
        jobRepository.findById(jobId).flatMap(job -> job.getCharacterId() == null
                ? java.util.Optional.empty() : characterRepository.findById(job.getCharacterId())).ifPresent(character -> {
            if (character.getStoryboardImageExt() == null) return;
            Path file = workDir.resolve("character-" + character.getId() + "-storyboard."
                    + character.getStoryboardImageExt());
            if (!Files.isRegularFile(file)) return;
            try {
                BufferedImage sheet = ImageIO.read(file.toFile());
                int index = Math.max(0, Math.min(11, shotNumber - 1));
                int column = index % 4;
                int row = index / 4;
                int x0 = column * sheet.getWidth() / 4;
                int y0 = row * sheet.getHeight() / 3;
                int x1 = (column + 1) * sheet.getWidth() / 4;
                int y1 = (row + 1) * sheet.getHeight() / 3;
                BufferedImage panel = sheet.getSubimage(x0, y0, x1 - x0, y1 - y0);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(panel, "png", output);
                arguments.put("storyboardReferenceBase64", Base64.getEncoder().encodeToString(output.toByteArray()));
                arguments.put("storyboardStrength", 0.72);
            } catch (Exception ignored) {
                // A broken optional storyboard must not block text-to-image fallback.
            }
        });
    }
}
