# 답변 생성 중 로딩 상태 유지 (Navigation Persistence)

## 1. 문제 정의

**현재 문제**: 답변 생성 중에 다른 탭/페이지로 이동했다가 다시 "답변" 탭으로 돌아오면, 생성 중인 로딩 프로세스 화면이 보이지 않음.

**원인**: `draftGenerating`, `draftSteps` 등 로딩 상태가 `InquiryAnswerTab` 컴포넌트의 **로컬 state**에만 존재. 컴포넌트가 언마운트되면 상태 소실.

**현재 아키텍처**:
```
[답변 생성 클릭]
    ↓
setDraftGenerating(true)         ← 로컬 state
setDraftSteps([])                ← 로컬 state
draftInquiryAnswer(...)          ← Blocking REST call
    ↓ (동시에)
SSE → onDraftStep(data)          ← draftSteps에 추가 (로컬 state)
    ↓
컴포넌트 언마운트 시 → 상태 전부 소실
    ↓
재마운트 시 → draftGenerating=false, draftSteps=[] → 빈 화면
```

## 2. 목표

| 시나리오 | 기대 동작 |
|----------|----------|
| 답변 생성 중 → 다른 탭(Info/Analysis) 이동 → 답변 탭 복귀 | PipelineProgress UI + 현재 진행 단계 표시 |
| 답변 생성 중 → 다른 문의 상세로 이동 → 원래 문의로 복귀 | PipelineProgress UI + 현재 진행 단계 표시 |
| 답변 생성 중 → 문의 목록으로 이동 → 원래 문의로 복귀 | PipelineProgress UI + 현재 진행 단계 표시 |
| 답변 생성 중 → 브라우저 새로고침 | PipelineProgress UI + SSE 재연결로 진행 단계 추적 |

## 3. 설계: 서버 사이드 파이프라인 상태

### 3.1 핵심 아이디어

답변 생성 상태를 **서버(백엔드)**에서 관리하고, 프론트엔드는 진입 시 항상 서버 상태를 조회하여 복원.

```
[Backend]
  inquiry.pipeline_status = "GENERATING"
  inquiry.pipeline_steps = [
    { step: "DECOMPOSE", status: "COMPLETED" },
    { step: "RETRIEVE", status: "IN_PROGRESS" },
    ...
  ]

[Frontend - 답변 탭 마운트 시]
  1. GET /api/v1/inquiries/{id}/pipeline-status
  2. if status == "GENERATING" → PipelineProgress 표시 + SSE 연결
  3. SSE로 실시간 업데이트 수신
```

### 3.2 파이프라인 상태 API

**신규 엔드포인트:**

```
GET /api/v1/inquiries/{inquiryId}/pipeline-status

Response (생성 중):
{
  "status": "GENERATING",
  "startedAt": "2025-03-15T10:30:00Z",
  "steps": [
    { "step": "DECOMPOSE", "status": "COMPLETED", "updatedAt": "..." },
    { "step": "RETRIEVE", "status": "IN_PROGRESS", "updatedAt": "..." },
    { "step": "VERIFY", "status": "PENDING", "updatedAt": null },
    { "step": "COMPOSE", "status": "PENDING", "updatedAt": null },
    { "step": "CRITIC", "status": "PENDING", "updatedAt": null },
    { "step": "SELF_REVIEW", "status": "PENDING", "updatedAt": null }
  ]
}

Response (생성 중 아닐 때):
{
  "status": "IDLE",
  "steps": []
}

Response (실패):
{
  "status": "FAILED",
  "error": "OpenAI API timeout",
  "failedAt": "...",
  "steps": [ ... ]
}
```

### 3.3 백엔드 구현

#### DB 스키마 변경

```sql
-- V33__add_pipeline_status.sql

CREATE TABLE pipeline_executions (
    id              UUID PRIMARY KEY,
    inquiry_id      UUID NOT NULL REFERENCES inquiries(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'GENERATING',
        -- GENERATING | COMPLETED | FAILED
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE pipeline_steps (
    id              UUID PRIMARY KEY,
    execution_id    UUID NOT NULL REFERENCES pipeline_executions(id),
    step_name       VARCHAR(30) NOT NULL,
        -- DECOMPOSE | RETRIEVE | ADAPTIVE_RETRIEVE | MULTI_HOP | VERIFY | COMPOSE | CRITIC | SELF_REVIEW
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
        -- PENDING | IN_PROGRESS | COMPLETED | FAILED | RETRY | SKIPPED
    message         TEXT,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_pipeline_exec_inquiry ON pipeline_executions(inquiry_id);
CREATE INDEX idx_pipeline_steps_exec ON pipeline_steps(execution_id);
```

#### 서비스 변경

**`AnswerOrchestrationService` 수정:**

```java
// 기존 emitPipelineEvent() 메서드에 DB 저장 추가

private void emitPipelineEvent(UUID inquiryId, String step, String status, String error) {
    // 1. 기존: SSE 이벤트 전송
    sseService.send(inquiryId, "pipeline-step", Map.of(
        "step", step,
        "status", status,
        "message", error != null ? error : ""
    ));

    // 2. 신규: DB에 파이프라인 상태 업데이트
    pipelineStatusService.updateStep(inquiryId, step, status, error);
}
```

**`PipelineStatusService` (신규):**

```java
@Service
public class PipelineStatusService {

    public PipelineExecution startExecution(UUID inquiryId) {
        // 기존 GENERATING 상태 정리 (있다면)
        // 새 execution 생성 + 모든 step을 PENDING으로 초기화
    }

    public void updateStep(UUID inquiryId, String step, String status, String message) {
        // 해당 step의 상태 업데이트
    }

    public void completeExecution(UUID inquiryId) {
        // status = COMPLETED, completed_at = now()
    }

    public void failExecution(UUID inquiryId, String error) {
        // status = FAILED, error_message = error
    }

    public PipelineStatusResponse getStatus(UUID inquiryId) {
        // 가장 최근 execution의 상태 조회
    }
}
```

**`PipelineStatusController` (신규):**

```java
@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}")
public class PipelineStatusController {

    @GetMapping("/pipeline-status")
    public PipelineStatusResponse getPipelineStatus(@PathVariable UUID inquiryId) {
        return pipelineStatusService.getStatus(inquiryId);
    }
}
```

## 4. 프론트엔드 구현

### 4.1 파이프라인 상태 Hook

```typescript
// hooks/usePipelineStatus.ts

interface PipelineStatus {
  status: 'IDLE' | 'GENERATING' | 'COMPLETED' | 'FAILED'
  steps: DraftStepData[]
  startedAt?: string
  error?: string
}

export function usePipelineStatus(inquiryId: string) {
  const [pipelineStatus, setPipelineStatus] = useState<PipelineStatus | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  // 컴포넌트 마운트 시 서버 상태 조회
  useEffect(() => {
    async function fetchStatus() {
      try {
        const res = await fetch(`/api/v1/inquiries/${inquiryId}/pipeline-status`)
        const data = await res.json()
        setPipelineStatus(data)
      } catch {
        setPipelineStatus({ status: 'IDLE', steps: [] })
      } finally {
        setIsLoading(false)
      }
    }
    fetchStatus()
  }, [inquiryId])

  return { pipelineStatus, isLoading, setPipelineStatus }
}
```

### 4.2 InquiryAnswerTab 수정

```typescript
// components/inquiry/InquiryAnswerTab.tsx

export default function InquiryAnswerTab({ inquiryId }: Props) {
  // 1. 서버에서 파이프라인 상태 조회
  const { pipelineStatus, isLoading: statusLoading } = usePipelineStatus(inquiryId)

  // 2. 로컬 state (SSE 업데이트용)
  const [draftSteps, setDraftSteps] = useState<DraftStepData[]>([])
  const [draftGenerating, setDraftGenerating] = useState(false)

  // 3. 서버 상태로 초기화 (마운트 시)
  useEffect(() => {
    if (pipelineStatus?.status === 'GENERATING') {
      setDraftGenerating(true)
      setDraftSteps(pipelineStatus.steps)
    }
  }, [pipelineStatus])

  // 4. SSE 연결 (생성 중일 때만 활성화)
  const { connectionStatus } = useInquiryEvents(inquiryId, {
    enabled: draftGenerating,  // 생성 중일 때만 SSE 연결
    onDraftStep: (data) => {
      setDraftGenerating(true)
      setDraftSteps((prev) => updateOrAddStep(prev, data))
    },
    onDraftCompleted: () => {
      setDraftGenerating(false)
      // 완성된 답변 로드
      fetchLatestDraft(inquiryId).then(setAnswerDraft)
    },
    onDraftFailed: () => {
      setDraftGenerating(false)
    },
  })

  // 5. 렌더링
  if (statusLoading) {
    return <Skeleton /> // 상태 로딩 중 스켈레톤
  }

  return (
    <div>
      {/* 생성 중이면 PipelineProgress 표시 */}
      {draftGenerating && (
        <PipelineProgress steps={draftSteps} />
      )}

      {/* 생성 완료된 답변 */}
      {!draftGenerating && answerDraft && (
        <AnswerDisplay draft={answerDraft} />
      )}

      {/* 답변 없으면 생성 버튼 */}
      {!draftGenerating && !answerDraft && (
        <DraftGenerationForm onSubmit={handleDraftAnswer} />
      )}
    </div>
  )
}
```

### 4.3 상태 전이 다이어그램

```
[컴포넌트 마운트]
       ↓
  GET /pipeline-status
       ↓
  ┌────────────────────────────────────────┐
  │                                        │
  ↓                                        ↓
status=IDLE                         status=GENERATING
  ↓                                        ↓
기존 답변 있으면 표시              PipelineProgress 표시
없으면 생성 버튼 표시              + SSE 연결하여 실시간 업데이트
  ↓                                        ↓
[사용자가 생성 클릭]               SSE: onDraftStep
  ↓                                        ↓
POST /answers/draft                 steps 업데이트 + UI 반영
+ setDraftGenerating(true)                 ↓
+ POST pipeline-status(GENERATING)  SSE: onDraftCompleted
  ↓                                        ↓
→ status=GENERATING 흐름으로 →     setDraftGenerating(false)
                                   fetchLatestDraft()
                                           ↓
                                   답변 표시
```

### 4.4 엣지 케이스 처리

#### 브라우저 새로고침
```typescript
// 새로고침 후 컴포넌트 마운트 시:
// 1. GET /pipeline-status → status=GENERATING (백엔드에서 아직 처리 중)
// 2. SSE 재연결 → 남은 step 업데이트 수신
// 3. REST call은 이미 끊김 → SSE의 onDraftCompleted로 완료 감지
//    또는 폴링으로 답변 존재 여부 확인
```

#### 생성 완료 후 탭 복귀
```typescript
// 1. GET /pipeline-status → status=COMPLETED
// 2. 기존 답변 조회 API로 최신 답변 로드
// 3. PipelineProgress 미표시, 답변 바로 표시
```

#### 생성 실패 후 탭 복귀
```typescript
// 1. GET /pipeline-status → status=FAILED, error="..."
// 2. 에러 메시지 표시 + 재시도 버튼
// 3. PipelineProgress에 실패한 step 빨간색으로 표시
```

#### 타임아웃 (장기 미응답)
```typescript
// pipeline_executions.started_at 기준으로 30분 초과 시
// 백엔드에서 자동으로 FAILED 처리 (스케줄러 또는 조회 시 체크)
useEffect(() => {
  if (pipelineStatus?.status === 'GENERATING' && pipelineStatus.startedAt) {
    const elapsed = Date.now() - new Date(pipelineStatus.startedAt).getTime()
    if (elapsed > 30 * 60 * 1000) { // 30분
      // 타임아웃으로 간주
      setDraftGenerating(false)
      showError('답변 생성이 시간 초과되었습니다. 다시 시도해주세요.')
    }
  }
}, [pipelineStatus])
```

## 5. SSE 개선

### 5.1 재연결 시 상태 동기화

현재 SSE 재연결 시 이전 이벤트를 받을 수 없음. 해결 방법:

**옵션 A: 서버 상태 조회 후 SSE 연결 (권장)**
```
마운트 → GET /pipeline-status (전체 상태 복원) → SSE 연결 (이후 업데이트만)
```

**옵션 B: SSE `Last-Event-ID` 활용**
- 각 SSE 이벤트에 순차 ID 부여
- 재연결 시 `Last-Event-ID` 헤더로 놓친 이벤트 재전송
- 구현 복잡도 높음 → Phase 2에서 고려

### 5.2 SSE 연결 최적화

```typescript
// 현재: 모든 탭에서 SSE 연결
// 개선: 답변 탭 + 생성 중일 때만 연결

const shouldConnectSSE = draftGenerating || pipelineStatus?.status === 'GENERATING'

const { connectionStatus } = useInquiryEvents(inquiryId, {
  enabled: shouldConnectSSE,
  // ...
})
```

## 6. PipelineProgress 컴포넌트 개선

### 6.1 "재접속" 표시

탭 복귀 시 SSE 재연결 과정에서 잠시 표시:

```typescript
// PipelineProgress.tsx에 추가

{connectionStatus === 'CONNECTING' && (
  <div className="text-xs text-muted-foreground animate-pulse">
    진행 상태에 다시 연결하고 있어요...
  </div>
)}
```

### 6.2 경과 시간 표시

```typescript
// PipelineProgress.tsx에 추가

const [elapsed, setElapsed] = useState(0)

useEffect(() => {
  if (!startedAt) return
  const timer = setInterval(() => {
    setElapsed(Math.floor((Date.now() - new Date(startedAt).getTime()) / 1000))
  }, 1000)
  return () => clearInterval(timer)
}, [startedAt])

// 표시: "1분 23초 경과" 형태
const minutes = Math.floor(elapsed / 60)
const seconds = elapsed % 60
```

## 7. 구현 단계

### Phase 1: 서버 사이드 파이프라인 상태 (백엔드)
1. `V33__add_pipeline_status.sql` 마이그레이션
2. `PipelineExecution`, `PipelineStep` JPA 엔티티
3. `PipelineStatusService` 구현
4. `PipelineStatusController` (GET /pipeline-status)
5. `AnswerOrchestrationService` 수정 — emitPipelineEvent에서 DB 업데이트 추가
6. 30분 타임아웃 처리 (조회 시 체크)

### Phase 2: 프론트엔드 상태 복원
1. `usePipelineStatus` 훅 구현
2. `InquiryAnswerTab` 수정 — 마운트 시 서버 상태 복원
3. SSE 연결 조건부 활성화
4. 엣지 케이스 처리 (새로고침, 타임아웃, 실패)

### Phase 3: UX 개선
1. PipelineProgress에 재연결 상태 표시
2. 경과 시간 표시
3. 실패 시 재시도 버튼 + 에러 메시지
4. 스켈레톤 로딩 (상태 조회 중)

## 8. 파일 변경 목록

### 백엔드 (신규)
| 파일 | 설명 |
|------|------|
| `V33__add_pipeline_status.sql` | 파이프라인 실행/단계 테이블 |
| `PipelineExecutionJpaEntity.java` | 실행 엔티티 |
| `PipelineStepJpaEntity.java` | 단계 엔티티 |
| `PipelineStatusService.java` | 상태 관리 서비스 |
| `PipelineStatusController.java` | 상태 조회 API |
| `PipelineStatusResponse.java` | 응답 DTO |

### 백엔드 (수정)
| 파일 | 변경 |
|------|------|
| `AnswerOrchestrationService.java` | `emitPipelineEvent()`에 DB 업데이트 추가 |

### 프론트엔드 (신규)
| 파일 | 설명 |
|------|------|
| `hooks/usePipelineStatus.ts` | 파이프라인 상태 조회 훅 |
| `lib/api/client.ts` | `getPipelineStatus()` API 함수 추가 |

### 프론트엔드 (수정)
| 파일 | 변경 |
|------|------|
| `InquiryAnswerTab.tsx` | 마운트 시 서버 상태 복원 + 조건부 SSE |
| `PipelineProgress.tsx` | 재연결 표시, 경과 시간, 에러 표시 |
| `useInquiryEvents.ts` | `enabled` 옵션 활용 개선 |
