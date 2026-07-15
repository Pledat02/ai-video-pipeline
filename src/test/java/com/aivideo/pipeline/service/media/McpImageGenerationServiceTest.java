package com.aivideo.pipeline.service.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class McpImageGenerationServiceTest {
    @TempDir Path tempDir;

    @Test
    void initializesSessionCallsToolAndSavesImage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Zf1sAAAAASUVORK5CYII=");
        server.createContext("/mcp", exchange -> handle(exchange, mapper, png));
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
            McpImageGenerationService service = new McpImageGenerationService(
                    mapper, RestClient.builder(), tempDir.toString(), url, "generate_image", "", false, 10);

            service.generateImages("Test topic", "A short scene.", 1, 42L, "cinematic", "16:9");

            Path output = tempDir.resolve("job-42-image-1.png");
            assertThat(output).exists();
            assertThat(Files.readAllBytes(output)).isEqualTo(png);
        } finally {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange, ObjectMapper mapper, byte[] png) throws IOException {
        JsonNode request = mapper.readTree(exchange.getRequestBody());
        String method = request.path("method").asText();
        if ("notifications/initialized".equals(method)) {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        String response;
        if ("initialize".equals(method)) {
            exchange.getResponseHeaders().add("Mcp-Session-Id", "test-session");
            response = "{\"jsonrpc\":\"2.0\",\"id\":" + request.path("id").asLong()
                    + ",\"result\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        } else {
            response = "{\"jsonrpc\":\"2.0\",\"id\":" + request.path("id").asLong()
                    + ",\"result\":{\"content\":[{\"type\":\"image\",\"mimeType\":\"image/png\",\"data\":\""
                    + Base64.getEncoder().encodeToString(png) + "\"}],\"isError\":false}}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
