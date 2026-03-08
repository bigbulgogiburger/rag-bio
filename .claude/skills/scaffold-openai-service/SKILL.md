---
name: scaffold-openai-service
description: OpenAI/Mock 이중 구현 서비스 스캐폴딩 (@ConditionalOnProperty 패턴). 새로운 LLM 연동 서비스 추가, OpenAI 서비스 생성, AI 기능 추가 요청 시 사용. Mock/Real 구현이 필요한 모든 AI 서비스에 사용.
---

## Purpose

이 프로젝트의 핵심 패턴인 OpenAI/Mock 이중 구현을 자동으로 스캐폴딩합니다.
`openai.enabled=true`일 때 실제 OpenAI API를 호출하고, `false`일 때 Mock 구현으로 fallback합니다.

## Architecture Pattern

```
                    ┌─────────────┐
                    │  Interface   │  (서비스 계약)
                    │  XxxService  │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │                         │
    ┌─────────┴──────────┐   ┌─────────┴──────────┐
    │   MockXxxService   │   │  OpenAiXxxService   │
    │  (항상 로드)        │   │  (@Primary)          │
    │  (fallback 제공)    │   │  (openai.enabled=true│
    │                    │   │   일 때만 로드)        │
    └────────────────────┘   └──────────────────────┘
```

## Step 1: 인터페이스 정의

```java
// 위치: interfaces/rest/{domain}/ 또는 application/{domain}/
public interface XxxService {
    XxxResult execute(XxxInput input);
}
```

## Step 2: Mock 구현 (항상 로드)

```java
@Component
public class MockXxxService implements XxxService {
    private static final Logger log = LoggerFactory.getLogger(MockXxxService.class);

    @Override
    public XxxResult execute(XxxInput input) {
        log.info("[Mock] XxxService called with: {}", input);
        return new XxxResult(/* 합리적인 기본값 */);
    }
}
```

## Step 3: OpenAI 구현 (@Primary + @ConditionalOnProperty)

```java
@Component
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiXxxService implements XxxService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiXxxService.class);

    private final RestClient restClient;
    private final String chatModel;
    private final PromptRegistry promptRegistry;
    private final MockXxxService fallback;

    public OpenAiXxxService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-medium:gpt-4.1-mini}") String chatModel,
            PromptRegistry promptRegistry,
            MockXxxService fallback) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.chatModel = chatModel;
        this.promptRegistry = promptRegistry;
        this.fallback = fallback;
    }

    @Override
    public XxxResult execute(XxxInput input) {
        try {
            String systemPrompt = promptRegistry.get("xxx-system");
            // ... OpenAI API 호출 로직
        } catch (Exception e) {
            log.warn("OpenAi Xxx failed, falling back to mock: {}", e.getMessage());
            return fallback.execute(input);
        }
    }
}
```

## Step 4: 프롬프트 외부화

프롬프트 파일: `backend/app-api/src/main/resources/prompts/xxx-system.txt`

```text
당신은 Bio-Rad 기술지원 전문가입니다.
... 프롬프트 내용 ...
```

사용법:
```java
// 기본
String prompt = promptRegistry.get("xxx-system");

// 변수 치환
String prompt = promptRegistry.get("xxx-system", Map.of("question", question));
```

## Step 5: 모델 티어 선택

| 티어 | 모델 | 용도 | @Value |
|------|------|------|--------|
| Heavy | gpt-5-mini | 복잡한 추론 (답변 작성, 비평) | `${openai.model.chat-heavy:gpt-5-mini}` |
| Medium | gpt-4.1-mini | 중간 복잡도 (검증, 검색, 리랭킹) | `${openai.model.chat-medium:gpt-4.1-mini}` |
| Light | gpt-5-nano | 경량 작업 (변환, 보강, 번역) | `${openai.model.chat-light:gpt-5-nano}` |

선택 기준: 출력 복잡도와 추론 깊이에 따라 결정. 단순 변환/분류는 Light, 판단/평가는 Medium, 긴 텍스트 생성은 Heavy.

## Checklist

- [ ] 인터페이스 정의됨
- [ ] MockXxxService 구현됨 (합리적 기본값)
- [ ] OpenAiXxxService에 `@Primary` + `@ConditionalOnProperty` 있음
- [ ] fallback으로 MockXxxService 주입됨
- [ ] 프롬프트가 `prompts/` 디렉토리에 외부화됨
- [ ] PromptRegistry.get() 사용 (인라인 프롬프트 금지)
- [ ] 적절한 모델 티어 선택됨
- [ ] try-catch에서 fallback 호출

## Related Files

| File | Purpose |
|------|---------|
| `backend/app-api/src/main/resources/application.yml` | openai.enabled, 모델 티어 설정 |
| `backend/app-api/src/main/resources/prompts/` | 외부화된 프롬프트 디렉토리 |
| `backend/app-api/src/main/java/.../interfaces/rest/search/` | 기존 OpenAI 서비스 참고 예시 |
