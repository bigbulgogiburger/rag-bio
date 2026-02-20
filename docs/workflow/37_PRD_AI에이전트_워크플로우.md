# PRD: AI Agent-Driven Workflow — "사람이 확인만, AI가 실행"

> **버전**: v1.0
> **작성일**: 2026-02-18
> **작성자**: IT Agile Planning
> **상태**: Draft
> **관련 PRD**: 36_PRD_워크플로우_간소화, 30_PRD_v2_서비스_고도화

---

## 0. Executive Summary

현재 CS Copilot의 답변 워크플로우(DRAFT → REVIEWED → APPROVED → SENT)는 **모든 단계에서 사람이 수동으로 텍스트를 입력하고 버튼을 클릭**한다. 리뷰어 이름을 직접 타이핑하고, 코멘트를 직접 작성하고, 승인자 이름을 또 타이핑하고, 발송자 이름을 또 타이핑한다.

원래 의도는 달랐다:
- **AI Review Agent**: 답변 초안을 자동 검토하고, 품질 문제를 지적하고, 개선안을 제시
- **AI Approval Agent**: 품질 기준(신뢰도, 리스크 플래그, 근거 충분성)을 자동 검증하고, 기준 충족 시 자동 승인
- **AI Send Agent**: 채널(이메일/메신저)에 맞는 포맷으로 실제 발송

본 PRD는 워크플로우의 3단계를 **AI 에이전트 기반으로 재설계**하되, **Human-in-the-Loop** 원칙을 유지하여 상담사가 최종 통제권을 갖도록 한다.

---

## 1. 배경 및 문제 분석

### 1.1 현재 워크플로우 (AS-IS)

```
답변 초안 생성 (AI)
    ↓
[수동] 리뷰: 사람이 리뷰어 이름 입력 + 코멘트 타이핑 + "리뷰" 클릭
    ↓
[수동] 승인: 사람이 승인자 이름 입력 + 코멘트 타이핑 + "승인" 클릭
    ↓
[수동] 발송: 사람이 발송자 이름 입력 + "발송" 클릭 → Mock 발송 (실제 전송 없음)
```

### 1.2 핵심 문제 6가지

| # | 문제 | 유형 | 심각도 |
|---|------|------|--------|
| **P1** | 리뷰가 형식적 — AI가 생성한 답변을 사람이 "looks fine" 수준으로 검토 | 품질 리스크 | Critical |
| **P2** | 승인 기준 없음 — 객관적 품질 게이트 없이 사람의 주관적 판단에 의존 | 품질 리스크 | Critical |
| **P3** | 실제 발송 기능 없음 — MockEmailSender/MockMessengerSender만 존재 | 기능 미완성 | Critical |
| **P4** | 관련자 이름이 자유 텍스트 — 오타, 비일관성, 직원DB 미연동 | UX 결함 | High |
| **P5** | 답변 품질 피드백 루프 없음 — 리뷰어가 문제를 발견해도 자동 수정 방법 없음 | 프로세스 결함 | High |
| **P6** | 3단계 수동 조작이 반복적 단순 노동 — 하루 수십 건 처리 시 피로도 급증 | 생산성 | Medium |

### 1.3 근본 원인

```
기술 중심 설계:
  개발 당시 워크플로우 상태 머신부터 구현 → "누가 하는지"는 나중에
  결과: 상태 전이 로직은 견고하지만, 각 단계의 "실행 주체"가 빈 껍데기

AI 활용 누락:
  RAG 파이프라인(Retrieve → Verify → Compose)은 AI를 활용하면서
  후속 워크플로우(Review → Approve → Send)는 100% 수동
  → 파이프라인의 절반만 자동화된 상태
```

### 1.4 현재 백엔드 아키텍처 강점 (재활용 가능)

| 컴포넌트 | 현재 상태 | 재활용 가치 |
|---------|----------|-----------|
| 상태 머신 (DRAFT → REVIEWED → APPROVED → SENT) | 견고하게 구현 | 상태 전이 로직 그대로 유지 |
| RBAC (X-User-Id, X-User-Roles) | 역할별 분리 완료 | AI 에이전트에도 역할 부여 가능 |
| MessageSender 전략 패턴 | Mock만 구현 | 실제 구현체만 추가하면 됨 |
| 멱등성 (sendRequestId) | 구현 완료 | 발송 중복 방지 그대로 활용 |
| 감사 로그 (SendAttemptLog) | 기본 구조 존재 | AI 에이전트 행동 로깅에 확장 |

---

## 2. 목표 (Goals)

### 2.1 비즈니스 목표

| 목표 | 측정 지표 | 현재 | 목표값 |
|------|----------|------|--------|
| 답변 품질 일관성 | AI 리뷰 통과율 (자동 수정 포함) | 측정 없음 | **95%+** |
| 워크플로우 자동화율 | 수동 개입 없이 완료된 건 비율 | 0% | **70%+** (자동 승인) |
| 답변 처리 시간 | 초안 생성 → 발송 완료 | 수동 5분+ | **30초 이내** (자동) |
| 실제 이메일 발송 | 실제로 발송된 이메일 건수 | 0건 (Mock) | **실제 발송** |
| 상담사 워크로드 감소 | 건당 수동 클릭 수 | 6+ 클릭 | **1 클릭** (확인 후 발송) |

### 2.2 설계 원칙

| 원칙 | 설명 |
|------|------|
| **Human-in-the-Loop** | AI가 실행하되, 사람이 최종 결정권을 가짐. 자동 승인도 사람이 거부 가능 |
| **Graceful Degradation** | AI 에이전트 장애 시 수동 워크플로우로 즉시 폴백 |
| **Transparency** | AI 에이전트의 판단 근거를 항상 표시 (블랙박스 X) |
| **Progressive Automation** | Phase 1은 AI 보조 + 사람 확인, Phase 2에서 자동화 비율 확대 |

### 2.3 비목표 (Non-Goals)

- 상담사 역할 완전 제거 (Human-in-the-Loop 유지)
- 기존 상태 머신 (DRAFT → REVIEWED → APPROVED → SENT) 변경
- RBAC 체계 변경 (기존 역할 구조 유지)
- 외부 CRM/티켓 시스템 연동

---

## 3. AI 에이전트 설계

### 3.1 에이전트 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Answer Draft (DRAFT)                         │
│   AI가 생성한 답변 초안 (verdict, confidence, citations, draft)       │
└──────────────────────────────┬──────────────────────────────────────┘
                               ↓
┌──────────────────────────────────────────────────────────────────────┐
│  🤖 AI Review Agent                                                  │
│                                                                      │
│  입력: draft + citations + verdict + confidence + riskFlags          │
│  처리:                                                               │
│    1. 정확성 검증 — 답변이 근거(citations)와 일치하는가?               │
│    2. 완전성 검증 — 질문의 모든 측면에 답했는가?                       │
│    3. 톤/형식 검증 — 선택한 톤(정중체/기술상세/요약)에 맞는가?         │
│    4. 리스크 평가 — 잘못된 정보, 과장, 누락 위험은?                    │
│    5. 개선안 생성 — 문제 발견 시 수정된 답변 초안 제시                  │
│                                                                      │
│  출력: ReviewResult                                                  │
│    - decision: PASS / REVISE / REJECT                                │
│    - score: 0-100 (품질 점수)                                        │
│    - issues: [{category, severity, description, suggestion}]         │
│    - revisedDraft: string | null (수정 제안 시)                       │
│    - summary: string (리뷰 요약)                                     │
│                                                                      │
│  상태 전이: DRAFT → REVIEWED                                         │
│  RBAC: X-User-Id=ai-review-agent, X-User-Roles=REVIEWER             │
└──────────────────────────────┬───────────────────────────────────────┘
                               ↓
┌──────────────────────────────────────────────────────────────────────┐
│  🤖 AI Approval Agent                                                │
│                                                                      │
│  입력: reviewResult + draft + confidence + riskFlags                 │
│  처리:                                                               │
│    1. 품질 게이트 검증:                                               │
│       - confidence ≥ 0.7                                             │
│       - reviewScore ≥ 80                                             │
│       - CRITICAL 이슈 0건                                            │
│       - riskFlags 중 HIGH 이상 0건                                   │
│    2. 자동 승인 / 사람 에스컬레이션 결정                               │
│                                                                      │
│  출력: ApprovalResult                                                │
│    - decision: AUTO_APPROVED / ESCALATED / REJECTED                  │
│    - reason: string (결정 근거)                                      │
│    - gateResults: [{gate, passed, value, threshold}]                 │
│                                                                      │
│  상태 전이:                                                          │
│    AUTO_APPROVED → APPROVED                                          │
│    ESCALATED    → REVIEWED (상담사 수동 확인 필요)                     │
│    REJECTED     → DRAFT (재생성 필요)                                 │
│                                                                      │
│  RBAC: X-User-Id=ai-approval-agent, X-User-Roles=APPROVER           │
└──────────────────────────────┬───────────────────────────────────────┘
                               ↓
┌──────────────────────────────────────────────────────────────────────┐
│  🤖 AI Send Agent                                                    │
│                                                                      │
│  입력: approvedDraft + channel + inquiry metadata                    │
│  처리:                                                               │
│    1. 채널별 포맷팅:                                                  │
│       - email: 제목 생성, 인사말/맺음말, HTML 서식, 참조자료 링크      │
│       - messenger: 간결한 메시지 포맷, 핵심 요약 우선                  │
│    2. 발송 전 최종 검증 (수신자, 내용 길이, 첨부파일 등)               │
│    3. 실제 발송 (SMTP / Messenger API)                               │
│    4. 발송 결과 추적 (messageId, deliveryStatus)                      │
│                                                                      │
│  출력: SendResult                                                    │
│    - provider: string (smtp / slack / teams)                         │
│    - messageId: string (외부 시스템 추적 ID)                          │
│    - formattedContent: string (실제 발송된 내용)                      │
│    - deliveryStatus: SENT / QUEUED / FAILED                          │
│                                                                      │
│  상태 전이: APPROVED → SENT                                          │
│  RBAC: X-User-Id=ai-send-agent, X-User-Roles=SENDER                 │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.2 워크플로우 모드

**Mode 1: Full Auto (자동)** — 품질 게이트 충족 시

```
답변 생성 → AI 리뷰 (PASS) → AI 승인 (AUTO_APPROVED) → 상담사 확인 → AI 발송
                                                         ↑
                                              상담사는 확인만 (1 클릭)
```

**Mode 2: Semi Auto (에스컬레이션)** — 품질 게이트 미충족 시

```
답변 생성 → AI 리뷰 (REVISE) → 수정안 제시 → 상담사 선택 → AI 승인 재시도
                                    ↓
                          원본 유지 / 수정안 채택 / 직접 수정
```

**Mode 3: Manual Fallback (수동)** — AI 장애 또는 사용자 선택 시

```
답변 생성 → 수동 리뷰 → 수동 승인 → 수동 발송 (기존 방식 유지)
```

---

## 4. 기능 요구사항

### 4.1 [F1] AI Review Agent

#### 4.1.1 백엔드: ReviewAgentService

```java
// 새로운 서비스
@Service
public class ReviewAgentService {

    // OpenAI를 활용한 답변 품질 리뷰
    public ReviewResult review(AnswerDraftJpaEntity draft, String question, List<EvidenceItem> evidences) {
        // 1. 프롬프트 구성: 답변 + 근거 + 질문을 LLM에 전달
        // 2. 품질 검증 (정확성, 완전성, 톤, 리스크)
        // 3. 이슈 발견 시 수정안 생성
        // 4. ReviewResult 반환
    }
}
```

**ReviewResult 스키마:**

```java
public record ReviewResult(
    String decision,              // PASS / REVISE / REJECT
    int score,                    // 0-100 품질 점수
    List<ReviewIssue> issues,     // 발견된 이슈 목록
    String revisedDraft,          // 수정 제안 (REVISE일 때만)
    String summary                // 리뷰 요약 (한국어)
)

public record ReviewIssue(
    String category,              // ACCURACY / COMPLETENESS / TONE / RISK / FORMAT
    String severity,              // CRITICAL / HIGH / MEDIUM / LOW
    String description,           // 이슈 설명
    String suggestion             // 개선 제안
)
```

#### 4.1.2 API 변경

```
POST /api/v1/inquiries/{id}/answers/{answerId}/ai-review

요청: (body 없음 — draft 내부 데이터를 자동으로 사용)
응답: 200 OK
{
    "decision": "REVISE",
    "score": 72,
    "issues": [
        {
            "category": "ACCURACY",
            "severity": "HIGH",
            "description": "답변에서 언급한 보관 온도(2-8°C)가 근거 문서의 권장 온도(4°C ± 2°C)와 표현이 다릅니다.",
            "suggestion": "근거 문서의 정확한 표현을 인용하세요: '4°C ± 2°C에서 보관'"
        }
    ],
    "revisedDraft": "수정된 답변 전문...",
    "summary": "답변의 전반적 구조는 양호하나, 온도 표기의 정확성 개선이 필요합니다.",
    "status": "REVIEWED",
    "reviewedBy": "ai-review-agent"
}
```

기존 수동 리뷰 API (`POST /{answerId}/review`)는 유지 (Manual Fallback).

#### 4.1.3 프론트엔드 변경

```
답변 탭 - 워크플로우 섹션 변경:

AS-IS:
  [리뷰어 입력] [코멘트 입력] [리뷰] 버튼

TO-BE:
  [AI 리뷰 실행] 버튼
      ↓
  리뷰 결과 카드:
    ├─ 품질 점수 배지 (72/100)
    ├─ 판정: "수정 필요" (REVISE)
    ├─ 이슈 목록 (카테고리별 접이식)
    │   ├─ 🔴 정확성 (HIGH): 온도 표기 불일치 → 제안: "4°C ± 2°C"
    │   └─ 🟡 형식 (MEDIUM): 인사말 누락 → 제안: "안녕하세요" 추가
    ├─ [수정안 보기] 토글 (diff 비교 뷰)
    └─ 액션 버튼:
        [수정안 채택] [원본 유지하고 승인 요청] [직접 수정]
```

#### 4.1.4 수용 기준

- [ ] "AI 리뷰 실행" 클릭 시 LLM 기반 품질 검증이 수행된다
- [ ] 리뷰 결과에 점수, 판정, 이슈 목록이 표시된다
- [ ] REVISE 판정 시 수정안이 diff 형태로 비교 가능하다
- [ ] 수정안 채택/원본 유지/직접 수정 중 선택할 수 있다
- [ ] 리뷰 결과가 감사 로그에 기록된다

---

### 4.2 [F2] AI Approval Agent

#### 4.2.1 백엔드: ApprovalAgentService

```java
@Service
public class ApprovalAgentService {

    // 품질 게이트 기반 자동 승인 판단
    public ApprovalResult evaluate(AnswerDraftJpaEntity draft, ReviewResult review) {
        List<GateResult> gates = List.of(
            checkGate("confidence", draft.getConfidence() >= 0.7, draft.getConfidence(), 0.7),
            checkGate("reviewScore", review.score() >= 80, review.score(), 80),
            checkGate("noCriticalIssues", review.issues().stream().noneMatch(i -> "CRITICAL".equals(i.severity())), ...),
            checkGate("noHighRiskFlags", draft.getRiskFlags().stream().noneMatch(f -> isHighRisk(f)), ...)
        );

        boolean allPassed = gates.stream().allMatch(GateResult::passed);
        // ...
    }
}
```

**ApprovalResult 스키마:**

```java
public record ApprovalResult(
    String decision,              // AUTO_APPROVED / ESCALATED / REJECTED
    String reason,                // 결정 근거 (한국어)
    List<GateResult> gateResults  // 각 게이트 통과 여부
)

public record GateResult(
    String gate,                  // confidence / reviewScore / noCriticalIssues / noHighRiskFlags
    boolean passed,               // 통과 여부
    String actualValue,           // 실제 값
    String threshold              // 기준값
)
```

#### 4.2.2 품질 게이트 정의

| 게이트 | 기준 | 미충족 시 |
|--------|------|----------|
| 신뢰도 | confidence ≥ 0.7 | ESCALATED (사람 확인) |
| 리뷰 점수 | reviewScore ≥ 80 | ESCALATED |
| 치명적 이슈 | CRITICAL 이슈 0건 | REJECTED (재생성) |
| 고위험 플래그 | HIGH_RISK 플래그 0건 | ESCALATED |

**게이트 통과 시**: AUTO_APPROVED → 상담사에게 "자동 승인됨, 확인 후 발송하시겠습니까?" 알림
**게이트 미통과 시**: ESCALATED → 상담사가 직접 검토 후 승인/거부 결정

#### 4.2.3 API

```
POST /api/v1/inquiries/{id}/answers/{answerId}/ai-approve

응답: 200 OK
{
    "decision": "AUTO_APPROVED",
    "reason": "모든 품질 게이트를 통과했습니다. (신뢰도 0.85, 리뷰 점수 92, 치명적 이슈 없음)",
    "gateResults": [
        { "gate": "confidence", "passed": true, "actualValue": "0.85", "threshold": "0.70" },
        { "gate": "reviewScore", "passed": true, "actualValue": "92", "threshold": "80" },
        { "gate": "noCriticalIssues", "passed": true, "actualValue": "0건", "threshold": "0건" },
        { "gate": "noHighRiskFlags", "passed": true, "actualValue": "0건", "threshold": "0건" }
    ],
    "status": "APPROVED",
    "approvedBy": "ai-approval-agent"
}
```

#### 4.2.4 프론트엔드 변경

```
승인 섹션:

자동 승인 성공 시:
  ┌─────────────────────────────────────────────────────┐
  │ ✅ AI 자동 승인 완료                                  │
  │                                                       │
  │ 신뢰도: 0.85 ✅  리뷰 점수: 92 ✅                     │
  │ 치명적 이슈: 없음 ✅  고위험 플래그: 없음 ✅            │
  │                                                       │
  │ [발송하기]  [승인 취소하고 직접 검토]                    │
  └─────────────────────────────────────────────────────┘

에스컬레이션 시:
  ┌─────────────────────────────────────────────────────┐
  │ ⚠️ 사람 확인 필요                                     │
  │                                                       │
  │ 신뢰도: 0.62 ❌ (기준 0.70)                           │
  │ 리뷰 점수: 85 ✅  치명적 이슈: 없음 ✅                 │
  │                                                       │
  │ 사유: 신뢰도가 기준 미달입니다. 근거가 부족할 수 있습니다. │
  │                                                       │
  │ [검토 후 승인]  [거부하고 재생성]                        │
  └─────────────────────────────────────────────────────┘
```

#### 4.2.5 수용 기준

- [ ] AI 리뷰 완료 후 품질 게이트 자동 검증이 수행된다
- [ ] 모든 게이트 통과 시 AUTO_APPROVED 상태로 전이된다
- [ ] 게이트 미통과 시 상담사에게 에스컬레이션 사유가 표시된다
- [ ] 상담사가 자동 승인을 취소하고 직접 검토할 수 있다
- [ ] 게이트 결과가 감사 로그에 기록된다

---

### 4.3 [F3] AI Send Agent — 실제 발송

#### 4.3.1 백엔드: 실제 MessageSender 구현

**이메일 발송 (SMTP):**

```java
@Component
@ConditionalOnProperty(name = "email.enabled", havingValue = "true")
public class SmtpEmailSender implements MessageSender {

    private final JavaMailSender mailSender;

    @Override
    public boolean supports(String channel) {
        return "email".equalsIgnoreCase(channel);
    }

    @Override
    public SendResult send(SendCommand command) {
        // 1. 이메일 포맷팅 (HTML 템플릿)
        // 2. 제목 자동 생성 (질문 요약)
        // 3. SMTP 발송
        // 4. messageId 반환
    }
}
```

**채널별 포맷팅 (AI 기반):**

```java
@Service
public class ChannelFormatterService {

    // LLM을 활용한 채널별 콘텐츠 포맷팅
    public FormattedContent format(String draft, String channel, String tone, InquiryDetail inquiry) {
        // email: 제목 생성 + 인사말 + 본문 + 참조자료 링크 + 맺음말 + 서명
        // messenger: 핵심 요약 + 본문 (간결) + 링크
    }
}
```

**FormattedContent 스키마:**

```java
public record FormattedContent(
    String subject,           // 이메일 제목 (email만)
    String htmlBody,          // HTML 본문 (email만)
    String plainBody,         // 텍스트 본문 (messenger)
    String greeting,          // 인사말
    String closing,           // 맺음말
    List<String> attachments  // 첨부파일 URL
)
```

#### 4.3.2 환경 설정

```yaml
# application.yml
email:
  enabled: ${EMAIL_ENABLED:false}
  smtp:
    host: ${SMTP_HOST:smtp.gmail.com}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    from: ${SMTP_FROM:cs-copilot@biorad.com}

messenger:
  enabled: ${MESSENGER_ENABLED:false}
  provider: ${MESSENGER_PROVIDER:slack}  # slack / teams
  webhook-url: ${MESSENGER_WEBHOOK_URL:}
```

#### 4.3.3 프론트엔드 변경

```
발송 섹션:

승인 완료 후:
  ┌─────────────────────────────────────────────────────┐
  │ 📧 발송 준비 완료                                     │
  │                                                       │
  │ 채널: 이메일                                           │
  │ 제목: "Re: Reagent X 야간 보관 관련 문의"              │
  │                                                       │
  │ [미리보기] ← 클릭 시 포맷팅된 이메일 미리보기            │
  │                                                       │
  │ [발송하기]  [직접 수정 후 발송]                          │
  └─────────────────────────────────────────────────────┘

발송 완료 후:
  ┌─────────────────────────────────────────────────────┐
  │ ✅ 발송 완료                                          │
  │                                                       │
  │ 채널: 이메일                                           │
  │ Message ID: email-2026-02-18-abc123                   │
  │ 발송 시각: 2026-02-18 14:32:15 KST                   │
  └─────────────────────────────────────────────────────┘
```

#### 4.3.4 수용 기준

- [ ] EMAIL_ENABLED=true 시 실제 SMTP 이메일이 발송된다
- [ ] 발송 전 포맷팅된 이메일/메시지를 미리볼 수 있다
- [ ] 발송 실패 시 에러 메시지와 재시도 버튼이 표시된다
- [ ] Mock 모드 (EMAIL_ENABLED=false)에서는 기존 MockSender로 동작한다
- [ ] 발송 결과 (messageId, 시각)가 감사 로그에 기록된다

---

### 4.4 [F4] 원클릭 자동 파이프라인

#### 4.4.1 통합 API

```
POST /api/v1/inquiries/{id}/answers/{answerId}/auto-workflow

하나의 API 호출로 Review → Approve까지 자동 실행.
(Send는 상담사 확인 후 별도 클릭)

응답: 200 OK
{
    "review": { ... ReviewResult ... },
    "approval": { ... ApprovalResult ... },
    "finalStatus": "APPROVED",        // or "REVIEWED" (에스컬레이션)
    "requiresHumanAction": false,     // true면 상담사 개입 필요
    "summary": "AI 리뷰 통과(92점) → 자동 승인 완료. 발송 준비되었습니다."
}
```

#### 4.4.2 프론트엔드: 통합 워크플로우 UX

```
답변 초안 생성 후:

  [자동 검토 + 승인 실행] 버튼
      ↓ (로딩 중: "AI가 답변을 검토하고 있습니다...")
      ↓
  결과에 따라:

  Case A: 자동 승인 완료
    "✅ AI 리뷰 통과 (92점) → 자동 승인 완료"
    [발송하기] [상세 리뷰 보기]

  Case B: 에스컬레이션
    "⚠️ AI 리뷰 결과 확인이 필요합니다"
    리뷰 결과 상세 표시 + [승인] [거부] [수정안 채택]

  Case C: 거부
    "❌ 답변 품질이 기준 미달입니다"
    이슈 목록 표시 + [수정안으로 재생성] [직접 수정]
```

#### 4.4.3 수용 기준

- [ ] 한 번의 클릭으로 Review + Approve가 자동 실행된다
- [ ] 자동 승인 시 상담사는 "발송하기" 한 번만 클릭하면 된다
- [ ] 에스컬레이션 시 상담사에게 판단 근거가 명확히 표시된다
- [ ] AI 장애 시 수동 모드로 폴백 가능하다

---

## 5. 기술 영향도 분석

### 5.1 백엔드 변경 사항

| 영역 | 변경 내용 | 영향도 |
|------|----------|--------|
| ReviewAgentService | 신규 — LLM 기반 답변 품질 리뷰 | 신규 |
| ApprovalAgentService | 신규 — 품질 게이트 자동 검증 | 신규 |
| ChannelFormatterService | 신규 — 채널별 콘텐츠 포맷팅 | 신규 |
| SmtpEmailSender | 신규 — 실제 SMTP 이메일 발송 | 신규 (MessageSender 구현) |
| AnswerController | 수정 — ai-review, ai-approve, auto-workflow 엔드포인트 추가 | 수정 |
| AnswerDraftJpaEntity | 수정 — reviewScore, reviewDecision, approvalDecision 컬럼 추가 | 수정 |
| Flyway 마이그레이션 | V20 — answer_drafts 컬럼 추가 + ai_review_results 테이블 | 신규 |
| application.yml | 수정 — email/messenger 설정 추가 | 수정 |

### 5.2 프론트엔드 변경 사항

| 영역 | 변경 내용 | 영향도 |
|------|----------|--------|
| InquiryAnswerTab.tsx | 워크플로우 액션 섹션 전면 교체 (수동 입력 → AI 에이전트 UI) | 수정 (대규모) |
| lib/api/client.ts | ai-review, ai-approve, auto-workflow API 클라이언트 추가 | 수정 |
| 리뷰 결과 컴포넌트 | 신규 — 품질 점수, 이슈 목록, diff 뷰 | 신규 |
| 승인 게이트 컴포넌트 | 신규 — 게이트 결과 카드, 에스컬레이션 UI | 신규 |
| 발송 미리보기 컴포넌트 | 신규 — 포맷팅된 이메일/메시지 미리보기 | 신규 |

### 5.3 데이터베이스 변경

```sql
-- V20__ai_workflow_columns.sql

-- 답변 초안 테이블에 AI 리뷰/승인 결과 컬럼 추가
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS review_score INT;
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS review_decision VARCHAR(32);
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS approval_decision VARCHAR(32);
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS approval_reason VARCHAR(2000);

-- AI 리뷰 상세 결과 테이블
CREATE TABLE IF NOT EXISTS ai_review_results (
    id UUID PRIMARY KEY,
    answer_id UUID NOT NULL REFERENCES answer_drafts(id),
    inquiry_id UUID NOT NULL REFERENCES inquiries(id),
    decision VARCHAR(32) NOT NULL,
    score INT NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    revised_draft TEXT,
    issues JSONB NOT NULL DEFAULT '[]',
    gate_results JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ai_review_results_answer_id ON ai_review_results(answer_id);
```

---

## 6. 실행 계획

### 6.1 스프린트 구성

#### Sprint 16: AI Review Agent + 승인 게이트 (2주)

**Week 1 — AI Review Agent**

| ID | 작업 | 레이어 | SP |
|----|------|--------|-----|
| S16-01 | ReviewAgentService 구현 (OpenAI 프롬프트 설계 + 결과 파싱) | BE | 5 |
| S16-02 | AI 리뷰 API 엔드포인트 + Flyway V20 마이그레이션 | BE | 3 |
| S16-03 | 리뷰 결과 카드 컴포넌트 (점수, 이슈 목록, diff 뷰) | FE | 5 |
| S16-04 | 답변 탭 워크플로우 섹션 리팩토링 (수동 → AI 기반) | FE | 3 |

**Week 2 — AI Approval Agent + 통합**

| ID | 작업 | 레이어 | SP |
|----|------|--------|-----|
| S16-05 | ApprovalAgentService 구현 (품질 게이트 로직) | BE | 3 |
| S16-06 | AI 승인 API + auto-workflow 통합 API | BE | 3 |
| S16-07 | 승인 게이트 결과 UI + 에스컬레이션 UI | FE | 3 |
| S16-08 | 수동 폴백 모드 유지 (기존 버튼 조건부 표시) | FE | 2 |
| S16-09 | 통합 테스트 (자동 승인 / 에스컬레이션 / 거부 시나리오) | QA | 3 |

**Sprint 16 Velocity**: 30 SP

---

#### Sprint 17: AI Send Agent + 실제 발송 (2주)

**Week 1 — 실제 발송 구현**

| ID | 작업 | 레이어 | SP |
|----|------|--------|-----|
| S17-01 | SmtpEmailSender 구현 (JavaMailSender + HTML 템플릿) | BE | 5 |
| S17-02 | ChannelFormatterService 구현 (LLM 기반 포맷팅) | BE | 3 |
| S17-03 | Messenger 발송 구현 (Slack Webhook) | BE | 3 |
| S17-04 | 발송 미리보기 API + 컴포넌트 | FE | 3 |

**Week 2 — 발송 안정화 + 전체 통합**

| ID | 작업 | 레이어 | SP |
|----|------|--------|-----|
| S17-05 | 발송 실패 재시도 로직 (exponential backoff, 최대 3회) | BE | 3 |
| S17-06 | 발송 상태 추적 (QUEUED → SENT → DELIVERED / FAILED) | BE | 3 |
| S17-07 | 전체 E2E 워크플로우 테스트 (생성 → 리뷰 → 승인 → 발송) | QA | 5 |
| S17-08 | 환경별 설정 문서화 (.env.example 업데이트) | Docs | 1 |

**Sprint 17 Velocity**: 26 SP

---

## 7. 리스크 및 대응

| 리스크 | 영향 | 확률 | 대응 |
|--------|------|------|------|
| LLM 리뷰 결과의 일관성 부족 | 동일 답변에 매번 다른 점수 | 중 | Temperature 0.1 설정 + 구조화된 JSON 출력 강제 + 프롬프트 버전 관리 |
| 자동 승인으로 인한 품질 사고 | 잘못된 답변이 고객에게 발송 | 높 | 발송 전 상담사 최종 확인 필수 (완전 자동 발송 X) + 품질 게이트 보수적 설정 |
| SMTP 발송 실패 (네트워크, 인증) | 이메일 미발송 | 중 | 재시도 로직 + 발송 실패 대시보드 + Mock 폴백 |
| AI 에이전트 응답 지연 (OpenAI API) | 워크플로우 대기 시간 증가 | 중 | 타임아웃 30초 + 지연 시 수동 모드 폴백 안내 |
| 자동 승인 남용 | 상담사가 검토 없이 발송 | 저 | 발송 전 "답변 내용을 확인하셨습니까?" 확인 다이얼로그 |

---

## 8. 성공 지표 (KPI)

| 지표 | 현재 | 목표 | 측정 방법 |
|------|------|------|----------|
| 자동 승인율 | 0% | 70%+ | approval_decision = AUTO_APPROVED 비율 |
| AI 리뷰 수정 채택율 | N/A | 60%+ | revisedDraft 채택 건수 / REVISE 판정 건수 |
| 워크플로우 완료 시간 | 수동 5분+ | 자동 30초 이내 | 초안 생성 → 승인 완료 시간 |
| 실제 이메일 발송 성공률 | 0% (Mock) | 99%+ | SENT / 발송 시도 건수 |
| 상담사 건당 클릭 수 | 6+ | 1-2 | 프론트엔드 이벤트 로그 |

---

## 9. 의사결정 필요 사항

| # | 항목 | 선택지 | 권장 |
|---|------|--------|------|
| D1 | AI 리뷰 시 수정안 자동 적용 vs 상담사 선택 | (A) 상담사가 선택 (B) 자동 적용 후 상담사가 되돌리기 | **✅ (A) 확정** — Human-in-the-Loop 원칙 준수 |
| D2 | 자동 승인 후 발송까지 자동화 여부 | (A) 발송은 항상 상담사 클릭 (B) 품질 95점+ 이면 자동 발송 | **✅ (A) 확정** — Phase 1에서는 발송 전 사람 확인 필수 |
| D3 | AI 리뷰 LLM 모델 선택 | (A) GPT-4o (B) GPT-4o-mini (C) Claude (D) GPT-5.2 | **✅ (D) 확정** — 최신 최상위 모델 GPT-5.2 사용 |
| D4 | 이메일 발송 서비스 | (A) SMTP 직접 (B) SendGrid API (C) AWS SES | **✅ (A) 확정** — 외부 의존성 최소화, 향후 필요 시 변경 |

---

## 10. 부록: 전후 비교

### AS-IS vs TO-BE 워크플로우

```
AS-IS (수동):
  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐
  │ 초안    │──→│ 리뷰    │──→│ 승인    │──→│ 발송    │
  │ (AI)   │   │ (사람)  │   │ (사람)  │   │ (Mock)  │
  │        │   │ 이름입력 │   │ 이름입력 │   │ 이름입력 │
  │        │   │ 코멘트  │   │ 코멘트  │   │         │
  └─────────┘   └─────────┘   └─────────┘   └─────────┘
  1 클릭        2 입력+1클릭  2 입력+1클릭   1 입력+1클릭
                                             실제 발송 X

TO-BE (AI Agent):
  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────┐
  │ 초안    │──→│ AI 리뷰 │──→│ AI 승인 │──→│ AI 발송 │
  │ (AI)   │   │ (자동)  │   │ (자동)  │   │ (실제)  │
  │        │   │ 점수/이슈│   │ 게이트  │   │ SMTP    │
  │        │   │ 수정안  │   │ 자동판정 │   │ 미리보기 │
  └─────────┘   └─────────┘   └─────────┘   └─────────┘
  1 클릭        자동           자동/에스컬    1 클릭(확인)
                (상담사 확인)  레이션         실제 발송 ✅
```

---

> **다음 단계**: 본 PRD 리뷰 → 의사결정 사항 확정 (D1~D4) → Sprint 16 실행 백로그 작성
