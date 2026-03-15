# G1: Streaming 답변 생성 (Token-Level Streaming)

> 작성일: 2026-03-15 | 우선순위: 🔴 HIGH | 예상 소요: 2주
> 현재 상태: COMPOSE 단계 블로킹 호출 (10-30초 대기) → 목표: 토큰 단위 실시간 스트리밍

---

## 1. 문제 정의

### 현재 흐름 (As-Is)
```
[사용자] → "답변 초안 생성" 클릭
    ↓
[Frontend] POST /answers/draft (blocking)
    ↓
[Backend] DECOMPOSE → RETRIEVE → VERIFY → COMPOSE(10~25초 블로킹) → CRITIC → SELF_REVIEW
    ↓                                      ↑
[SSE] pipeline-step 이벤트                  전체 답변 완성 후 한 번에 반환
    ↓
[Frontend] "답변 작성" 단계에서 긴 대기 → 완료 후 전체 답변 표시
```

### 핵심 문제
1. **COMPOSE 단계에서 10-25초 동안 아무 피드백 없음** — 사용자가 "멈춘 건 아닌지" 불안
2. **체감 속도 저하** — 첫 토큰이 2-3초 만에 나올 수 있는데, 전체 완성까지 25초 대기
3. **OpenAI API는 이미 스트리밍 지원** — `stream: true` 옵션만 추가하면 토큰 단위 수신 가능

### 목표 흐름 (To-Be)
```
[사용자] → "답변 초안 생성" 클릭
    ↓
[Frontend] POST /answers/draft
    ↓
[Backend] DECOMPOSE → RETRIEVE → VERIFY → COMPOSE(스트리밍 시작)
                                              ↓
[SSE] compose-token: "안녕"                    ↓
[SSE] compose-token: "하세요"                  ↓
[SSE] compose-token: ". Bio-Rad"              ↓
...토큰이 하나씩 도착...                        ↓
[SSE] compose-done: (전체 답변)               CRITIC → SELF_REVIEW
    ↓
[Frontend] 에디터에 토큰이 실시간으로 타이핑되듯 표시
```

### 기대 효과
| 지표 | 현재 | 목표 |
|------|------|------|
| 첫 토큰 표시 | ~25초 (COMPOSE 완료 후) | **~2초** (COMPOSE 시작 직후) |
| 체감 응답 시간 | 25-40초 | **2-3초** |
| 사용자 이탈률 | 높음 (긴 대기) | 낮음 (실시간 피드백) |

---

## 2. 아키텍처 설계

### 2.1 전체 흐름

```
                                    ┌─────────────────────────────────┐
                                    │  OpenAI API (stream: true)      │
                                    │  POST /chat/completions         │
                                    │                                 │
                                    │  data: {"choices":[{            │
                                    │    "delta":{"content":"안녕"}   │
                                    │  }]}                            │
                                    │  data: {"choices":[{            │
                                    │    "delta":{"content":"하세요"} │
                                    │  }]}                            │
                                    │  data: [DONE]                   │
                                    └──────────┬──────────────────────┘
                                               │ SSE stream
                                               ▼
┌──────────────┐    SSE     ┌─────────────────────────────────────────┐
│  Frontend    │◄───────────│  Backend                                │
│              │            │                                         │
│  useInquiry  │            │  OpenAiComposeStep.executeStreaming()   │
│  Events.ts   │            │    ↓                                    │
│              │            │  RestClient.exchangeToStream()          │
│  onCompose   │            │    ↓                                    │
│  Token()     │            │  BufferedReader.readLine()              │
│    ↓         │            │    ↓                                    │
│  AnswerEditor│            │  SseService.send("compose-token", chunk)│
│  실시간 삽입 │            │    ↓                                    │
│              │            │  StringBuilder.append(chunk) — 전체 축적│
│              │            │    ↓                                    │
│              │            │  stream 종료 → fullDraft 반환           │
└──────────────┘            └─────────────────────────────────────────┘
```

### 2.2 SSE 이벤트 설계

#### 기존 이벤트 (유지)
```
event: pipeline-step
data: {"step":"COMPOSE","status":"STARTED","error":""}

event: pipeline-step
data: {"step":"COMPOSE","status":"COMPLETED","error":""}
```

#### 신규 이벤트
```
# 토큰 청크 (COMPOSE 진행 중 반복 발생)
event: compose-token
data: {"chunk":"안녕하세요","index":0}

event: compose-token
data: {"chunk":". Bio-Rad ","index":1}

...

# 스트리밍 완료 (전체 답변 확정)
event: compose-done
data: {"draft":"안녕하세요. Bio-Rad...전체답변...","tokenCount":1523}
```

**설계 원칙:**
- `compose-token`은 가벼운 청크 전송 (평균 3-8 토큰/이벤트)
- `compose-done`은 전체 답변 확정본 (프론트엔드 상태 동기화)
- 기존 `pipeline-step` 이벤트는 그대로 유지 (하위 호환)
- CRITIC/SELF_REVIEW는 `compose-done` 이후 실행

---

## 3. 백엔드 구현 상세

### 3.1 OpenAI Streaming API 호출

**현재 코드** (`OpenAiComposeStep.callLlm()`):
```java
// ❌ 블로킹 — 전체 응답 대기
String response = restClient.post()
    .uri("/chat/completions")
    .body(OpenAiRequestUtils.chatBody(chatModel, messages, 4096, 0.3))
    .retrieve()
    .body(String.class);
```

**변경 코드** — Streaming 방식:
```java
// ✅ 스트리밍 — 토큰 단위 수신
private String callLlmStreaming(String systemPrompt, String userPrompt,
                                 Consumer<String> onToken) {
    // 1. stream: true 추가
    Map<String, Object> body = OpenAiRequestUtils.chatBody(
        chatModel, messages, 4096, 0.3
    );
    body.put("stream", true);
    body.put("stream_options", Map.of("include_usage", true));

    StringBuilder fullResponse = new StringBuilder();

    // 2. RestClient streaming exchange
    restClient.post()
        .uri("/chat/completions")
        .body(body)
        .exchange((request, response) -> {
            try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    String payload = line.substring(6).trim();
                    if ("[DONE]".equals(payload)) break;

                    // 3. delta.content 추출
                    JsonNode node = objectMapper.readTree(payload);
                    JsonNode delta = node.at("/choices/0/delta/content");
                    if (delta != null && !delta.isNull()) {
                        String chunk = delta.asText();
                        fullResponse.append(chunk);
                        onToken.accept(chunk);  // 콜백으로 토큰 전달
                    }

                    // 4. usage 추출 (마지막 chunk)
                    JsonNode usage = node.get("usage");
                    if (usage != null) {
                        recordTokenUsage(usage);
                    }
                }
            }
            return fullResponse.toString();
        });

    return fullResponse.toString();
}
```

### 3.2 ComposeStep 인터페이스 확장

```java
// ComposeStep.java — 기존 인터페이스에 스트리밍 메서드 추가
public interface ComposeStep {

    // 기존 (하위 호환)
    ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel);

    // 신규 — 스트리밍 콜백 지원
    default ComposeStepResult executeStreaming(
            AnalyzeResponse analysis,
            String tone,
            String channel,
            String additionalInstructions,
            String previousAnswerDraft,
            Consumer<String> onToken   // 토큰 콜백
    ) {
        // 기본 구현: 기존 블로킹 방식 (Mock용 fallback)
        return execute(analysis, tone, channel, additionalInstructions, previousAnswerDraft);
    }
}
```

### 3.3 AnswerOrchestrationService 수정

```java
// COMPOSE 단계 (현재: 블로킹)
emitPipelineEvent(inquiryId, "COMPOSE", "STARTED", null);

// ✅ 스트리밍 방식으로 변경
final AtomicInteger tokenIndex = new AtomicInteger(0);
ComposeStep.ComposeStepResult composed = executeWithRunLog(inquiryId, "COMPOSE",
    () -> composeStep.executeStreaming(
        finalAnalysis, tone, channel, mergedInstructions, previousAnswerDraft,
        chunk -> {
            // 토큰 청크를 SSE로 실시간 전송
            sseService.send(inquiryId, "compose-token", Map.of(
                "chunk", chunk,
                "index", tokenIndex.getAndIncrement()
            ));
        }
    )
);

// 스트리밍 완료 후 전체 답변 전송
sseService.send(inquiryId, "compose-done", Map.of(
    "draft", composed.draft(),
    "tokenCount", tokenIndex.get()
));

emitPipelineEvent(inquiryId, "COMPOSE", "COMPLETED", null);
```

### 3.4 SseService 최적화 — 청크 배치 전송

토큰 하나하나 SSE로 보내면 오버헤드가 크므로, **50ms 윈도우로 버퍼링** 후 배치 전송:

```java
// SseService.java 추가
@Service
public class SseService {

    // 기존 send() 유지 + 청크 전용 메서드 추가
    public void sendChunk(UUID inquiryId, String chunk, int index) {
        // 바로 전송 (버퍼링은 프론트엔드에서 처리)
        send(inquiryId, "compose-token", Map.of(
            "chunk", chunk,
            "index", index
        ));
    }
}
```

> **참고**: 서버 사이드 버퍼링보다 프론트엔드 `requestAnimationFrame` 기반 렌더링이 더 효율적.
> OpenAI API 자체가 ~50ms 간격으로 청크를 보내므로 서버 버퍼링은 불필요.

### 3.5 Mock 구현 (DefaultComposeStep)

```java
// DefaultComposeStep.java — 개발/테스트용 스트리밍 시뮬레이션
@Override
public ComposeStepResult executeStreaming(
        AnalyzeResponse analysis, String tone, String channel,
        String additionalInstructions, String previousAnswerDraft,
        Consumer<String> onToken) {

    String mockDraft = "안녕하세요. Bio-Rad 기술지원팀입니다.\n\n"
        + "문의하신 내용에 대해 아래와 같이 안내드립니다...";

    // 20ms 간격으로 토큰 시뮬레이션
    for (String word : mockDraft.split("(?<=\\G.{3})")) {
        onToken.accept(word);
        Thread.sleep(20);
    }

    return new ComposeStepResult(mockDraft, List.of());
}
```

---

## 4. 프론트엔드 구현 상세

### 4.1 useInquiryEvents.ts 확장

```typescript
// 신규 콜백 타입
export interface ComposeTokenData {
  chunk: string;
  index: number;
}

export interface ComposeCompleteData {
  draft: string;
  tokenCount: number;
}

// useInquiryEvents 옵션에 추가
interface UseInquiryEventsOptions {
  // ... 기존 ...
  onComposeToken?: (data: ComposeTokenData) => void;  // 신규
  onComposeDone?: (data: ComposeCompleteData) => void; // 신규
}
```

**EventSource 리스너 추가:**
```typescript
// useInquiryEvents.ts 내부
es.addEventListener("compose-token", (e: MessageEvent) => {
  const data = JSON.parse(e.data) as ComposeTokenData;
  onComposeTokenRef.current?.(data);
});

es.addEventListener("compose-done", (e: MessageEvent) => {
  const data = JSON.parse(e.data) as ComposeCompleteData;
  onComposeDoneRef.current?.(data);
});
```

### 4.2 InquiryAnswerTab.tsx — 스트리밍 상태 관리

```typescript
// 스트리밍 답변 상태
const [streamingDraft, setStreamingDraft] = useState<string>("");
const [isStreaming, setIsStreaming] = useState(false);

// SSE 핸들러
const { connectionStatus } = useInquiryEvents(inquiryId, {
  enabled: shouldConnectSSE,
  onDraftStep: (data) => {
    setDraftGenerating(true);
    setDraftSteps((prev) => updateOrAddStep(prev, data));

    // COMPOSE STARTED → 스트리밍 모드 진입
    if (data.step === "COMPOSE" && data.status === "IN_PROGRESS") {
      setIsStreaming(true);
      setStreamingDraft("");
    }
  },

  // ⭐ 토큰 스트리밍 핸들러
  onComposeToken: (data) => {
    setStreamingDraft(prev => prev + data.chunk);
  },

  // ⭐ 스트리밍 완료 핸들러
  onComposeDone: (data) => {
    setIsStreaming(false);
    setStreamingDraft(data.draft); // 전체 답변으로 교체 (정합성)
  },

  onDraftCompleted: () => {
    setDraftGenerating(false);
    setIsStreaming(false);
    // 최종 답변 로드
    getLatestAnswerDraft(inquiryId).then(setAnswerDraft);
  },
});
```

### 4.3 스트리밍 UI 렌더링

```typescript
{/* COMPOSE 스트리밍 중 — 토큰이 하나씩 나타남 */}
{isStreaming && (
  <div className="space-y-4">
    <div className="flex items-center gap-2">
      <h4 className="text-base font-semibold">답변 작성 중...</h4>
      <div className="animate-pulse text-xs text-muted-foreground">
        실시간 생성
      </div>
    </div>
    <div className="rounded-lg border bg-muted/10 p-4 text-sm leading-relaxed whitespace-pre-wrap">
      {streamingDraft}
      <span className="animate-pulse">▍</span>  {/* 커서 깜빡임 */}
    </div>
  </div>
)}
```

**고급 옵션: TipTap 에디터에 스트리밍 삽입**
```typescript
// AnswerEditor에 streamingContent prop 추가
{isStreaming && (
  <AnswerEditor
    content={streamingDraft}
    draftFormat="TEXT"
    inquiryId={inquiryId}
    editable={false}
    streaming={true}  // 커서 애니메이션 + 자동 스크롤
  />
)}
```

### 4.4 커서 애니메이션 + 자동 스크롤

```css
/* 타이핑 커서 효과 */
@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.streaming-cursor::after {
  content: '▍';
  animation: blink 0.8s infinite;
  color: hsl(var(--primary));
}
```

```typescript
// 자동 스크롤: 새 토큰이 추가될 때 뷰포트 하단으로
useEffect(() => {
  if (isStreaming && streamingRef.current) {
    streamingRef.current.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }
}, [streamingDraft, isStreaming]);
```

---

## 5. CRITIC/SELF_REVIEW 후처리

스트리밍 완료 후 CRITIC/SELF_REVIEW가 실행되어 답변이 **수정될 수 있음**:

```
[COMPOSE streaming 완료]
  ↓ compose-done (초안 v1)
  ↓
[CRITIC 실행] → faithfulness < 0.7 → 재작성 (초안 v2)
  ↓
[SELF_REVIEW] → 팬텀 제품명 발견 → 경고 추가
  ↓
[DRAFT_COMPLETED] → 최종 답변 (v2 + 경고)
```

**프론트엔드 처리:**
```typescript
// 1. compose-done: 스트리밍 초안 표시 (빠른 피드백)
// 2. CRITIC/SELF_REVIEW 진행 중: "답변 검증 중..." 표시
// 3. draft-completed: 최종 답변으로 교체 (수정된 경우)

{isStreaming && <StreamingPreview draft={streamingDraft} />}
{!isStreaming && draftGenerating && streamingDraft && (
  <div>
    <Badge>검증 중</Badge>
    <StreamingPreview draft={streamingDraft} dimmed />
  </div>
)}
{answerDraft && !draftGenerating && (
  <AnswerEditor content={answerDraft.draft} editable={false} />
)}
```

---

## 6. 에러 처리

### 6.1 OpenAI 스트리밍 에러

| 시나리오 | 처리 |
|----------|------|
| 스트리밍 중 네트워크 끊김 | `compose-error` 이벤트 전송 → 프론트엔드 "연결 끊김, 재시도?" 표시 |
| OpenAI 429 (Rate Limit) | 지수 백오프 재시도 (최대 3회) → 실패 시 fallback draft |
| OpenAI 500 | 즉시 fallback draft 생성 |
| 스트리밍 중 timeout (30초) | 축적된 partial draft + "[생성 중단]" 접미사로 저장 |

### 6.2 SSE 연결 끊김 중 스트리밍

```typescript
// 프론트엔드: SSE 재연결 시 compose-token 누락 대응
// → compose-done에서 전체 답변을 받으므로 누락 토큰 자동 복구
onComposeDone: (data) => {
  setStreamingDraft(data.draft); // 전체 교체 → 누락 복구
  setIsStreaming(false);
}
```

### 6.3 Fallback (스트리밍 불가 시)

```java
// OpenAiComposeStep — 스트리밍 실패 시 블로킹 fallback
try {
    return callLlmStreaming(systemPrompt, userPrompt, onToken);
} catch (Exception e) {
    log.warn("Streaming failed, falling back to blocking call: {}", e.getMessage());
    return callLlm(systemPrompt, userPrompt); // 기존 블로킹 방식
}
```

---

## 7. 성능 최적화

### 7.1 토큰 배치 (프론트엔드)

```typescript
// requestAnimationFrame으로 렌더링 최적화
const pendingChunks = useRef<string[]>([]);

const onComposeToken = useCallback((data: ComposeTokenData) => {
  pendingChunks.current.push(data.chunk);

  // 다음 프레임에서 배치 업데이트 (16ms 주기)
  if (pendingChunks.current.length === 1) {
    requestAnimationFrame(() => {
      const batch = pendingChunks.current.join('');
      pendingChunks.current = [];
      setStreamingDraft(prev => prev + batch);
    });
  }
}, []);
```

### 7.2 SSE 이벤트 크기 최적화

```
# 최소 이벤트 (불필요 필드 제거)
event: compose-token
data: {"c":"안녕"}         ← 23 bytes (chunk만)

# vs 현재 pipeline-step
event: pipeline-step
data: {"step":"COMPOSE","status":"COMPLETED","error":""}   ← 54 bytes
```

### 7.3 토큰 추적 (stream_options)

```json
// OpenAI API 요청에 추가
{
  "stream": true,
  "stream_options": { "include_usage": true }
}
// → 마지막 chunk에 usage 정보 포함
// → PipelineTraceContext에 정확한 토큰 수 기록
```

---

## 8. 구현 단계

### Phase 1: 백엔드 스트리밍 인프라 (3일)

| 태스크 | 파일 | 설명 |
|--------|------|------|
| 1-1 | `ComposeStep.java` | `executeStreaming()` 메서드 추가 (default 구현) |
| 1-2 | `OpenAiComposeStep.java` | `callLlmStreaming()` 구현 (RestClient.exchange) |
| 1-3 | `DefaultComposeStep.java` | Mock 스트리밍 시뮬레이션 |
| 1-4 | `SseService.java` | `sendChunk()` 메서드 추가 |
| 1-5 | `AnswerOrchestrationService.java` | COMPOSE 단계에서 스트리밍 호출 + SSE 토큰 전송 |

### Phase 2: 프론트엔드 스트리밍 UI (3일)

| 태스크 | 파일 | 설명 |
|--------|------|------|
| 2-1 | `useInquiryEvents.ts` | `compose-token`, `compose-done` 리스너 추가 |
| 2-2 | `InquiryAnswerTab.tsx` | 스트리밍 상태 관리 + 렌더링 |
| 2-3 | `PipelineProgress.tsx` | COMPOSE 단계에서 "실시간 생성 중" 표시 |
| 2-4 | `AnswerEditor.tsx` | `streaming` prop 추가 (커서 애니메이션) |

### Phase 3: 안정화 + 최적화 (2일)

| 태스크 | 파일 | 설명 |
|--------|------|------|
| 3-1 | `OpenAiComposeStep.java` | 에러 처리 + fallback + retry |
| 3-2 | `InquiryAnswerTab.tsx` | SSE 재연결 시 토큰 복구 |
| 3-3 | — | E2E 테스트 (스트리밍 시나리오) |
| 3-4 | — | 성능 측정 (TTFT, 전체 지연) |

### Phase 4: CRITIC 재작성 스트리밍 (2일, 선택)

| 태스크 | 설명 |
|--------|------|
| 4-1 | CRITIC에서 재작성 시에도 스트리밍 적용 |
| 4-2 | 프론트엔드: "답변 수정 중..." 스트리밍 UI |

---

## 9. 파일 변경 목록

### 백엔드 수정
| 파일 | 변경 |
|------|------|
| `ComposeStep.java` | `executeStreaming()` default method 추가 |
| `OpenAiComposeStep.java` | `callLlmStreaming()` 구현, `stream: true` 요청 |
| `DefaultComposeStep.java` | Mock 스트리밍 시뮬레이션 |
| `AnswerOrchestrationService.java` | COMPOSE 호출을 `executeStreaming()`으로 변경 |
| `SseService.java` | `sendChunk()` 편의 메서드 |
| `OpenAiRequestUtils.java` | `chatBodyStreaming()` 헬퍼 |

### 프론트엔드 수정
| 파일 | 변경 |
|------|------|
| `useInquiryEvents.ts` | `compose-token`, `compose-done` 핸들러 |
| `InquiryAnswerTab.tsx` | 스트리밍 상태 + UI 렌더링 |
| `PipelineProgress.tsx` | COMPOSE 단계 실시간 표시 |
| `AnswerEditor.tsx` | `streaming` prop, 커서 애니메이션 |

### 신규 파일 없음
기존 파일만 수정. 새로운 클래스/컴포넌트 생성 불필요.

---

## 10. 리스크 및 대응

| 리스크 | 확률 | 대응 |
|--------|------|------|
| RestClient 스트리밍 불안정 | 낮음 | WebClient(Reactor) 대안, 또는 OkHttp 직접 사용 |
| SSE 이벤트 순서 보장 | 낮음 | `index` 필드로 순서 보장, compose-done으로 최종 동기화 |
| 토큰 추적 정확도 | 중간 | `stream_options.include_usage` 사용 |
| CRITIC 재작성과 충돌 | 중간 | compose-done 후 CRITIC 결과를 별도 이벤트로 전송 |
| 메모리 (대량 동시 스트리밍) | 낮음 | SseEmitter 타임아웃 + connection 풀 관리 |

---

## 11. 측정 지표

| 지표 | 현재 | Phase 1 목표 | 측정 방법 |
|------|------|-------------|----------|
| TTFT (Time-to-First-Token) | ~25초 | **< 3초** | 프론트엔드 타임스탬프 |
| 전체 답변 시간 | 25-40초 | 25-40초 (동일) | 백엔드 로그 |
| 사용자 체감 속도 | 느림 | **빠름** (실시간 피드백) | UX 설문 |
| SSE 이벤트 손실률 | 0% | < 1% | compose-done 대비 토큰 수 비교 |

---

## 12. 요약

**G1 Streaming 답변 생성**은 기존 COMPOSE 단계의 블로킹 OpenAI 호출을 `stream: true` 스트리밍으로 전환하고, SSE `compose-token` 이벤트로 토큰 단위 실시간 전송하는 것입니다.

**핵심 변경 3가지:**
1. `OpenAiComposeStep.callLlmStreaming()` — `RestClient.exchange()` + 스트리밍 파싱
2. `AnswerOrchestrationService` — 토큰 콜백 → `SseService.send("compose-token")`
3. `InquiryAnswerTab` — `onComposeToken` 핸들러 → 실시간 UI 렌더링

**기존 코드 영향 최소:** ComposeStep 인터페이스에 default method 추가, Mock은 기존 방식 유지.
