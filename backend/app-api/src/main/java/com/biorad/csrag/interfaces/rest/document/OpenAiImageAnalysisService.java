package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiImageAnalysisService implements ImageAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiImageAnalysisService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final MockImageAnalysisService fallback;
    private final PromptRegistry promptRegistry;

    public OpenAiImageAnalysisService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-medium:gpt-4.1}") String chatModel,
            ObjectMapper objectMapper,
            MockImageAnalysisService fallback,
            PromptRegistry promptRegistry
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.fallback = fallback;
        this.promptRegistry = promptRegistry;
    }

    @Override
    public ImageAnalysisResult analyze(Path imagePath) {
        try {
            byte[] imageBytes = Files.readAllBytes(imagePath);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = detectMimeType(imagePath);

            Map<String, Object> requestBody = Map.of(
                    "model", chatModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", promptRegistry.get("image-analysis")),
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "image_url", "image_url",
                                            Map.of("url", "data:" + mimeType + ";base64," + base64Image))
                            ))
                    ),
                    "max_tokens", 2000
            );

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String content = root.path("choices").path(0).path("message").path("content").asText("");

            // Strip markdown code fences if present
            String jsonContent = content.strip();
            if (jsonContent.startsWith("```")) {
                int firstNewline = jsonContent.indexOf('\n');
                int lastFence = jsonContent.lastIndexOf("```");
                if (firstNewline > 0 && lastFence > firstNewline) {
                    jsonContent = jsonContent.substring(firstNewline + 1, lastFence).strip();
                }
            }

            JsonNode parsed = objectMapper.readTree(jsonContent);
            return new ImageAnalysisResult(
                    parsed.path("imageType").asText("PHOTO"),
                    parsed.path("extractedText").asText(""),
                    parsed.path("visualDescription").asText(""),
                    parsed.path("technicalContext").asText(""),
                    parsed.path("suggestedQuery").asText(""),
                    parsed.path("confidence").asDouble(0.5)
            );
        } catch (Exception ex) {
            log.warn("openai.image-analysis.failed -> fallback to mock: {}", ex.getMessage());
            return fallback.analyze(imagePath);
        }
    }

    private String detectMimeType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
