# PRD v3 — Bio-Rad CS Copilot 답변 품질 고도화

> **문서 버전:** 3.0
> **작성일:** 2026-02-21
> **작성자:** AI Agile Planning
> **상태:** Draft → Review 대기

---

## 0. 문서 목적

Sprint 7-9를 통해 지식 기반 관리, 한국어화, 문의 목록 페이징, UI/UX 현대화가 완료되었다.

본 PRD는 **실제 운영 피드백에서 도출된 3가지 답변 품질 핵심 문제**를 정의하고, 이를 해결하기 위한 기능 요구사항과 실행 계획을 기술한다.

---

## 1. 현황 분석 — "지금 뭐가 문제인가?"

### 1.1 문제 정의

| # | 문제 | 심각도 | 현재 상태 |
|---|------|--------|----------|
| P5 | **이력 탭 PDF 미리보기 미지원** | Medium | 답변 탭에서는 근거 클릭 시 PDF 미리보기가 동작하지만, 이력(History) 탭에서 과거 버전의 PDF 인용을 클릭하면 미리보기가 불가능. CS 담당자가 과거 답변 검토 시 매번 별도 다운로드 필요 |
| P6 | **답변 보완 요청(추가 입력) 기능 부재** | High | 답변 초안 생성 후 특정 부분에 대한 보완을 요청할 방법이 없음. 예: "보관조건은 나왔는데 '어떻게' 보관하는지가 빠져있다" → 전체 재생성만 가능하여 기존 좋은 내용까지 변경될 위험 |
| P7 | **RAG 파이프라인 답변 신뢰도 문제** | Critical | 실 운영 피드백: ① 이미 알려준 조건을 반복 서술 ② 1번 문단의 시약과 3번 문단의 시약이 달라 혼동 유발 ③ 구체적 절차("어떻게")가 누락되는 경향 |

### 1.2 근본 원인 분석

```
P5 → InquiryHistoryTab.tsx에 PdfViewer import은 있으나,
     과거 버전 답변의 citation 클릭 이벤트가 PDF 미리보기 패널과 연결되지 않음.
     InquiryAnswerTab.tsx의 미리보기 로직이 히스토리 탭에 공유되지 않는 구조.

P6 → POST /answers/draft API가 question + tone + channel만 받음.
     "추가 지시사항(additional instructions)" 파라미터가 없음.
     ComposeStep이 이전 답변 컨텍스트를 참조하지 않음.

P7 → DefaultComposeStep의 템플릿이 정적 문자열 기반.
     근거(evidence) 간 중복/충돌 검증 로직 없음.
     절차적 정보(HOW) vs 조건적 정보(WHAT) 구분 없이 일괄 삽입.
     인용 삽입이 단순 문자열 치환으로 문맥 무시.
```

### 1.3 사용자 피드백 (실제 클라이언트)

> "이미 알려준 조건에 대해서 또 알려달라는 답변을 한 것"
> — 중복 서술 문제

> "1번 문단에서 소개한 시약과 3번문단에서 소개하는 시약이 다름으로 인해 문의자가 혼동을 줄 수 있을 거 같아"
> — 일관성 문제

---

## 2. 목표 (Goals)

### 2.1 비즈니스 목표

| 목표 | 측정 지표 | 목표값 |
|------|----------|--------|
| 답변 초안 **1회 생성으로 승인** 비율 향상 | 재생성 없이 승인된 답변 비율 | 60% → 85% |
| CS 담당자 **답변 검토 시간** 단축 | 초안 생성 → 발송까지 평균 시간 | 15분 → 8분 |
| 과거 답변 **참조 효율** 향상 | 이력 탭에서 PDF 직접 확인 비율 | 0% → 90% |
| 답변 **신뢰도** 향상 | 사용자 수정 없이 발송 비율 | 40% → 70% |

### 2.2 비목표 (Non-Goals)

- LLM 모델 변경 (GPT-4 → Claude 등)
- 실시간 채팅 기반 답변 생성
- 외부 검색 엔진(Google Scholar 등) 연동
- 자동 발송 (human-in-the-loop 유지)

---

## 3. 기능 요구사항

---

### 3.1 [P5] 이력 탭 PDF 미리보기

#### 3.1.1 문제 상세

답변 탭(InquiryAnswerTab)에서는 근거 항목 클릭 시 우측 패널에 PDF가 해당 페이지로 열리고, 페이지 범위 다운로드도 가능하다. 그러나 이력 탭(InquiryHistoryTab)에서 과거 버전 답변의 인용 클릭 시 동일한 미리보기가 동작하지 않는다.

#### 3.1.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| P5-1 | 이력 탭에서 과거 답변의 인용(citation) 클릭 시 PDF 미리보기 패널 표시 | Must |
| P5-2 | PDF 미리보기 패널에 페이지 네비게이션(이전/다음) 지원 | Must |
| P5-3 | 확대보기(PdfExpandModal) 지원 | Should |
| P5-4 | 근거 페이지만 다운로드 기능 | Should |

#### 3.1.3 기술 설계

```
변경 파일:
- frontend/src/components/inquiry/InquiryHistoryTab.tsx
  → 답변 본문 내 citation 클릭 이벤트 핸들러 추가
  → PdfViewer 패널 연결 (기존 dynamic import 활용)
  → selectedEvidence 상태 관리

참고 구현: InquiryAnswerTab.tsx의 renderDraftWithCitations() 패턴 재사용
```

#### 3.1.4 수용 기준

- [ ] 이력 탭에서 과거 답변의 "(파일명.pdf, p.X-Y)" 클릭 시 우측 PDF 미리보기 패널 표시
- [ ] 페이지 범위가 정확히 해당 페이지로 이동
- [ ] 확대보기 모달 정상 동작
- [ ] 근거 페이지 범위 다운로드 가능

---

### 3.2 [P6] 답변 보완 요청 (추가 입력) 기능

#### 3.2.1 문제 상세

현재 답변 생성 후 불만족 시 전체 재생성만 가능하다. CS 담당자는 "보관조건은 잘 나왔는데 '어떻게 보관하는지' 절차가 빠져있다"와 같이 **특정 부분만 보완**하고 싶은 경우가 빈번하다.

#### 3.2.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| P6-1 | 답변 초안 하단에 "보완 요청" 입력 필드 표시 | Must |
| P6-2 | 보완 요청 시 기존 답변 + 추가 지시사항을 함께 전송하여 보완된 답변 생성 | Must |
| P6-3 | 보완 요청 이력을 답변 히스토리에 기록 | Must |
| P6-4 | 보완 요청 시 기존 근거(evidence)를 유지하면서 추가 검색 수행 | Should |
| P6-5 | 보완 요청 횟수 제한 (최대 5회) 및 카운터 표시 | Should |

#### 3.2.3 기술 설계

**백엔드 변경:**

```
1. API 확장
   POST /api/v1/inquiries/{id}/answers/draft
   기존: { question, tone, channel }
   변경: { question, tone, channel, additionalInstructions?, previousAnswerId? }

2. ComposeStep 확장
   - previousAnswer 컨텍스트 참조
   - additionalInstructions를 프롬프트에 주입
   - 기존 근거 유지 + 추가 검색 결과 병합

변경 파일:
- backend/app-api/.../answer/AnswerController.java → 요청 DTO 확장
- backend/app-api/.../answer/orchestration/DefaultComposeStep.java → 보완 로직
- backend/app-api/.../answer/orchestration/AnswerOrchestrationService.java → 이전 답변 참조
```

**프론트엔드 변경:**

```
변경 파일:
- frontend/src/components/inquiry/InquiryAnswerTab.tsx
  → 보완 요청 입력 필드 UI 추가 (답변 초안 하단)
  → 보완 요청 API 호출 로직
  → 보완 횟수 카운터 표시
- frontend/src/lib/api/client.ts
  → draftInquiryAnswer() 파라미터 확장
```

#### 3.2.4 UX 흐름

```
[답변 초안 생성 완료]
     ↓
[답변 본문 표시]
     ↓
[하단: "보완 요청" 섹션]
  ┌─────────────────────────────────────────────┐
  │ 추가 요청사항을 입력하세요                       │
  │ ┌─────────────────────────────────────────┐ │
  │ │ ex) 보관 절차에 대한 구체적인 방법을          │ │
  │ │     추가해주세요                            │ │
  │ └─────────────────────────────────────────┘ │
  │                          [보완 답변 생성] (2/5) │
  └─────────────────────────────────────────────┘
```

#### 3.2.5 수용 기준

- [ ] 답변 초안 생성 후 "보완 요청" 입력 필드가 표시됨
- [ ] 보완 요청 입력 후 생성 클릭 시 기존 답변 맥락을 유지하며 보완된 답변 생성
- [ ] 보완된 답변이 새로운 버전으로 히스토리에 기록됨
- [ ] 보완 요청 횟수가 카운터로 표시됨 (최대 5회)
- [ ] 빈 입력 시 버튼 비활성화

---

### 3.3 [P7] RAG 파이프라인 답변 신뢰도 향상

#### 3.3.1 문제 상세

실제 운영에서 다음 3가지 품질 문제가 반복적으로 발생:

1. **중복 서술**: 이미 언급한 조건/정보를 다른 문단에서 반복
2. **시약/제품명 불일치**: 1번 문단의 시약과 3번 문단의 시약이 다른 제품으로 인용
3. **절차 누락**: "어떤" 조건인지는 나오지만 "어떻게" 하는지 절차가 빠짐

#### 3.3.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| P7-1 | **중복 제거 검증**: Compose 단계에서 근거 간 중복 내용 감지 및 통합 | Must |
| P7-2 | **일관성 검증**: 답변 내 제품명/시약명/수치 일관성 확인 | Must |
| P7-3 | **절차 완전성 검증**: 조건(WHAT) 대비 절차(HOW) 포함 여부 확인 | Must |
| P7-4 | **근거 클러스터링**: 동일 주제 근거를 그룹화하여 일관된 서술 | Should |
| P7-5 | **답변 구조화**: 조건 → 절차 → 주의사항 순서로 자동 구조화 | Should |
| P7-6 | **Self-Review 단계**: Compose 후 자동 품질 검증 스텝 추가 | Must |

#### 3.3.3 기술 설계

**파이프라인 확장:**

```
기존: RETRIEVE → VERIFY → COMPOSE
변경: RETRIEVE → VERIFY → COMPOSE → SELF_REVIEW → (필요시 RE_COMPOSE)

SELF_REVIEW 단계 체크리스트:
  ✓ 중복 서술 없음 (동일 정보가 2회 이상 등장하지 않음)
  ✓ 제품명/시약명 일관성 (답변 전체에서 동일 제품은 동일 명칭 사용)
  ✓ 절차 완전성 (조건 언급 시 해당 절차도 포함)
  ✓ 근거 출처 정확성 (인용된 문서와 실제 내용 일치)
  ✓ 문단 간 논리적 흐름 (주제별 그룹화)
```

**변경 파일:**

```
백엔드:
- backend/app-api/.../orchestration/DefaultComposeStep.java
  → 중복 제거 로직 추가
  → 제품명 일관성 검증
  → 절차 완전성 확인
  → 근거 클러스터링 (동일 문서/주제 그룹화)

- backend/app-api/.../orchestration/SelfReviewStep.java (신규)
  → Compose 결과 자동 품질 검증
  → 검증 실패 시 피드백과 함께 재작성 요청

- backend/app-api/.../orchestration/AnswerOrchestrationService.java
  → SELF_REVIEW 스텝 체인 추가
  → SSE 이벤트에 SELF_REVIEW 단계 추가
```

**ComposeStep 프롬프트 개선:**

```
기존 방식: 정적 템플릿 + 단순 인용 삽입
개선 방식:
  1. 근거를 주제별로 클러스터링
  2. 클러스터별 핵심 정보 추출 (조건/절차/주의사항)
  3. 구조화된 답변 생성:
     - 확인 결과 요약
     - 조건 및 절차 (근거별)
     - 주의사항
     - 추가 확인 안내
  4. Self-Review 체크리스트 적용
```

#### 3.3.4 Self-Review 로직

```java
public class SelfReviewStep {
    public SelfReviewResult review(String draft, List<EvidenceItem> evidences) {
        List<QualityIssue> issues = new ArrayList<>();

        // 1. 중복 검증 - 동일 정보 2회 이상 등장 감지
        issues.addAll(checkDuplication(draft));

        // 2. 일관성 검증 - 제품명/시약명/수치 불일치 감지
        issues.addAll(checkConsistency(draft, evidences));

        // 3. 절차 완전성 - 조건(~해야 한다) 대비 절차(~하는 방법) 확인
        issues.addAll(checkProcedureCompleteness(draft, evidences));

        // 4. 인용 정확성 - 인용된 페이지의 실제 내용과 답변 내용 대조
        issues.addAll(checkCitationAccuracy(draft, evidences));

        boolean passed = issues.stream()
            .noneMatch(i -> i.severity() == Severity.CRITICAL);

        return new SelfReviewResult(passed, issues);
    }
}
```

#### 3.3.5 수용 기준

- [ ] 동일 정보가 답변 내 2회 이상 반복되지 않음
- [ ] 답변 전체에서 동일 제품/시약은 동일 명칭으로 일관되게 사용
- [ ] 조건이 언급될 때 해당 절차(방법)도 함께 서술됨
- [ ] Self-Review 결과가 SSE 이벤트로 프론트엔드에 전달됨
- [ ] Self-Review 실패 시 자동 재작성 (최대 2회)
- [ ] 프론트엔드에서 SELF_REVIEW 진행 상태 표시

---

## 4. 비기능 요구사항

| 항목 | 요구사항 |
|------|---------|
| 성능 | 보완 답변 생성 ≤ 30초 (기존 답변 생성과 동일 수준) |
| 성능 | Self-Review 단계 추가로 인한 전체 파이프라인 지연 ≤ 10초 |
| 호환성 | 기존 답변 워크플로우(DRAFT→REVIEWED→APPROVED→SENT) 유지 |
| 호환성 | 기존 API 하위 호환 (additionalInstructions는 optional) |
| 접근성 | 보완 요청 입력 필드에 ARIA 레이블 적용 |

---

## 5. 실행 계획

### 5.1 Sprint 10 (2주)

| 주차 | 작업 | 담당 |
|------|------|------|
| W1-Day1~2 | P5: 이력 탭 PDF 미리보기 | 프론트엔드 |
| W1-Day1~3 | P6-백엔드: API 확장 + ComposeStep 보완 로직 | 백엔드 |
| W1-Day3~5 | P6-프론트엔드: 보완 요청 UI + API 연동 | 프론트엔드 |
| W2-Day1~3 | P7: SelfReviewStep 구현 + ComposeStep 개선 | 백엔드 |
| W2-Day3~4 | P7: 파이프라인 SSE 이벤트 확장 + 프론트엔드 표시 | 풀스택 |
| W2-Day5 | 통합 테스트 + 운영 피드백 반영 | 전체 |

### 5.2 의존 관계

```
P5 (이력 PDF 미리보기) ── 독립, 즉시 착수 가능
P6 (보완 요청) ── 백엔드 API 확장 → 프론트엔드 UI (순차)
P7 (RAG 품질) ── P6 완료 후 보완 로직과 통합 가능하나, 독립 착수 가능
```

---

## 6. 리스크 & 완화

| 리스크 | 영향 | 완화 방안 |
|--------|------|----------|
| Self-Review로 인한 응답 지연 | 사용자 체감 속도 저하 | SSE로 단계별 진행률 표시, Self-Review 타임아웃 5초 |
| 보완 답변이 원본보다 품질 저하 | 사용자 혼란 | 이전 답변 컨텍스트 전체 전달, 변경 부분 하이라이트 |
| 근거 클러스터링 오류 | 잘못된 그룹화로 답변 왜곡 | 클러스터링 결과를 검증 단계에서 확인, 폴백 로직 |

---

## 7. 성공 지표

| 지표 | 현재 | 목표 | 측정 방법 |
|------|------|------|----------|
| 답변 1회 승인율 | ~40% | 85% | approved_count / total_draft_count |
| 평균 보완 요청 횟수 | N/A (재생성만 가능) | ≤ 1.5회 | refinement_count / answer_count |
| 이력 탭 PDF 확인율 | 0% | 90% | history_pdf_view / history_tab_view |
| CS 담당자 만족도 | 미측정 | 4.0/5.0 | 분기별 설문 |
