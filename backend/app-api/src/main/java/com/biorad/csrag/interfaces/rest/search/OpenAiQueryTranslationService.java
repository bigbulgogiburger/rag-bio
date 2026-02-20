package com.biorad.csrag.interfaces.rest.search;

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

import java.util.List;
import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiQueryTranslationService implements QueryTranslationService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiQueryTranslationService.class);

    private static final String SYSTEM_PROMPT =
            "You are a translation assistant. Translate the following text from Korean to English. "
                    + "If the text is already in English, return it as-is. Only output the translation, nothing else.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiQueryTranslationService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public TranslatedQuery translate(String question) {
        if (question == null || question.isBlank()) {
            return new TranslatedQuery(question, question, false);
        }

        if (isLikelyEnglish(question)) {
            log.info("openai.query.translation: input appears to be English, skipping translation");
            return new TranslatedQuery(question, question, false);
        }

        try {
            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(Map.of(
                            "model", "gpt-4o-mini",
                            "messages", List.of(
                                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                                    Map.of("role", "user", "content", question)
                            ),
                            "temperature", 0.0
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String translated = root.path("choices").path(0).path("message").path("content").asText("");

            if (translated.isBlank()) {
                log.warn("openai.query.translation: empty response, returning original");
                return new TranslatedQuery(question, question, false);
            }

            log.info("openai.query.translation: '{}' -> '{}'", question, translated.trim());
            return new TranslatedQuery(question, translated.trim(), true);
        } catch (Exception ex) {
            log.warn("openai.query.translation.failed -> returning original: {}", ex.getMessage());
            return new TranslatedQuery(question, question, false);
        }
    }

    private boolean isLikelyEnglish(String text) {
        for (char c : text.toCharArray()) {
            if (c > 127 && !Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }
}
