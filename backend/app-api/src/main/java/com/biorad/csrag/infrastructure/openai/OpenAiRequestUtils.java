package com.biorad.csrag.infrastructure.openai;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.biorad.csrag.infrastructure.openai.OpenAiResponseParser.TokenUsageInfo;

/**
 * OpenAI Chat Completions API 요청 빌더 유틸리티.
 * .env에서 주입된 모델명을 기반으로 파라미터를 자동 선택:
 *
 * <ul>
 *   <li>토큰: reasoning 모델 + GPT-4.1/4o → {@code max_completion_tokens}, legacy → {@code max_tokens}</li>
 *   <li>temperature: reasoning 모델(gpt-5-*, o1-*, o3-*, o4-*)은 커스텀 temperature 미지원 → 생략</li>
 * </ul>
 */
public final class OpenAiRequestUtils {

    private OpenAiRequestUtils() {}

    /**
     * reasoning 모델 여부 판단.
     * gpt-5-*, o1-*, o3-*, o4-* 등은 temperature/top_p 커스텀을 지원하지 않음.
     */
    public static boolean isReasoningModel(String model) {
        if (model == null) return false;
        String lower = model.toLowerCase();
        return lower.startsWith("o1") ||
                lower.startsWith("o3") ||
                lower.startsWith("o4") ||
                lower.startsWith("gpt-5");
    }

    /**
     * 모델명에 따라 올바른 max token 파라미터 이름을 반환.
     * Reasoning + GPT-4.1/4o → max_completion_tokens, Legacy(GPT-4, GPT-3.5) → max_tokens
     */
    public static String tokenParamName(String model) {
        if (model == null) return "max_completion_tokens";
        String lower = model.toLowerCase();
        // Legacy: gpt-3.5-*, gpt-4-turbo, gpt-4-0613 등 (gpt-4o, gpt-4.1 제외)
        if (lower.startsWith("gpt-3") ||
                (lower.startsWith("gpt-4-") && !lower.startsWith("gpt-4o") && !lower.startsWith("gpt-4.1"))) {
            return "max_tokens";
        }
        return "max_completion_tokens";
    }

    /**
     * Chat completions 요청 body 빌드 (토큰 제한 포함).
     */
    public static Map<String, Object> chatBody(String model, Object messages, int maxTokens) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("messages", messages);
        body.put(tokenParamName(model), maxTokens);
        return body;
    }

    /**
     * Chat completions 요청 body 빌드 (토큰 제한 + temperature).
     * reasoning 모델이면 temperature를 자동으로 생략.
     */
    public static Map<String, Object> chatBody(String model, Object messages, int maxTokens, double temperature) {
        var body = chatBody(model, messages, maxTokens);
        if (!isReasoningModel(model)) {
            body.put("temperature", temperature);
        }
        return body;
    }

    /**
     * JSON Mode 활성화된 Chat completions 요청 body 빌드 (토큰 제한).
     * {@code response_format: {"type": "json_object"}}를 포함하여
     * OpenAI가 반드시 유효한 JSON만 반환하도록 강제한다.
     */
    public static Map<String, Object> chatBodyWithJsonMode(String model, Object messages, int maxTokens) {
        var body = chatBody(model, messages, maxTokens);
        body.put("response_format", Map.of("type", "json_object"));
        return body;
    }

    /**
     * JSON Mode 활성화된 Chat completions 요청 body 빌드 (토큰 제한 + temperature).
     * reasoning 모델이면 temperature를 자동으로 생략.
     */
    public static Map<String, Object> chatBodyWithJsonMode(String model, Object messages, int maxTokens, double temperature) {
        var body = chatBody(model, messages, maxTokens, temperature);
        body.put("response_format", Map.of("type", "json_object"));
        return body;
    }

    /**
     * OpenAI API 응답 body(Map)에서 토큰 사용량을 추출한다.
     * {@link OpenAiResponseParser#extractTokenUsage(Map)}에 위임.
     *
     * @param responseBody 이미 파싱된 OpenAI API 응답 Map
     * @return 추출된 토큰 사용량 정보
     */
    /**
     * 스트리밍 Chat completions 요청 body 빌드 (stream: true + stream_options).
     * 토큰 사용량 추적을 위해 include_usage: true 옵션 포함.
     */
    public static Map<String, Object> chatBodyStreaming(String model, Object messages, int maxTokens, double temperature) {
        var body = chatBody(model, messages, maxTokens, temperature);
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        return body;
    }

    public static TokenUsageInfo extractTokenUsage(Map<String, Object> responseBody) {
        return OpenAiResponseParser.extractTokenUsage(responseBody);
    }
}
