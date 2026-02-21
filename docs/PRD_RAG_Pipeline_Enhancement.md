# PRD: RAG 파이프라인 답변 품질 고도화

## 1. 배경 및 문제 정의

### 현상
라이브 환경에서 QX700 yellow flag 문의(inquiry `aff54da0`)에 대해 KB 문서(QX700 Analysis SW manual, p.39)에 **정확한 답변이 존재**하고, 검색(Retrieve)도 **정확하게 해당 chunk를 찾아왔음**에도 불구하고, 최종 답변은 **"단정하기 어렵습니다", "특정하기 어렵습니다"** 라는 hedging 답변이 생성됨.

### 근본 원인 (진단 완료)
`summarize()` 160자 절단 문제는 이미 수정 배포 완료 (chunk 전문 전달). 나머지 3개 원인이 잔존:

| # | 원인 | 위치 | 영향 |
|---|------|------|------|
| 1 | **Verdict 임계값 과도** | `AnalysisService.java:166` | avg 0.82 이상이어야 SUPPORTED → 기술문서에서 거의 불가능 |
| 2 | **LLM 프롬프트에 verdict/confidence 노출** | `OpenAiComposeStep.java:85-87` | LLM이 CONDITIONAL + 낮은 confidence 보고 자체 hedging |
| 3 | **Guardrails 면책 문구 삽입** | `DefaultComposeStep.java:102-106` | confidence < 0.75이면 "신뢰도 충분히 높지 않아..." 문구 추가 |

### 이미 해결된 항목
- `summarize()` 160자 → 전문 전달 (배포 완료, 답변 품질 대폭 개선 확인)

---

## 2. 목표

- **답변 품질**: 관련 evidence가 있을 때 "모르겠다" 대신 evidence 기반 구체적 답변 생성
- **자동 승인률 향상**: 현재 ESCALATED → 적절한 quality gate 통과 시 AUTO_APPROVED
- **비용 영향 없음**: 프롬프트 구조 변경만, 추가 API 호출 없음

---

## 3. 스코프

### In-Scope (3개 작업, 병렬 실행 가능)

#### Task A: Verdict 임계값 조정 + classifyQuestionPrior 제거
**파일**: `AnalysisService.java`

변경 내용:
1. SUPPORTED 임계값: `0.82 → 0.70`
2. CONDITIONAL 임계값: `0.64 → 0.45`
3. LOW_CONFIDENCE riskFlag: CONDITIONAL일 때 자동 추가하지 않음. avg < 0.50일 때만 추가
4. `classifyQuestionPrior()` 메서드 제거 — 키워드 기반 verdict 선판정은 오판 위험이 높음 (고객 질문에 "risk", "condition" 같은 단어가 포함되면 내용과 무관하게 CONDITIONAL로 판정)
5. polarity conflict의 spread 임계값: `0.25 → 0.35` (기술문서 검색에서 evidence 간 score 차이가 0.25 이상은 정상적)

예상 효과:
- yellow flag 문의(avg 0.568) → SUPPORTED로 판정
- 불필요한 LOW_CONFIDENCE flag 제거

#### Task B: OpenAI Compose 프롬프트 재설계
**파일**: `OpenAiComposeStep.java`

변경 내용:
1. `[분석 결과]` 섹션에서 `verdict`, `confidence`, `riskFlags` 제거
2. 대신 `[지시]` 섹션에 "참고 자료의 내용을 기반으로 가능한 한 구체적이고 실용적인 답변을 작성하라. 참고 자료에 답변에 필요한 정보가 충분히 있으면 자신 있게 안내하라. 정보가 부족한 부분에 대해서만 추가 확인을 요청하라." 추가
3. `tone`과 `channel`은 유지 (답변 형식 결정에 필요)
4. temperature: `0.2 → 0.3` (약간의 자연스러움 향상)

핵심 원칙: **LLM이 evidence 자체의 내용을 보고 판단하게 하되, 시스템이 미리 "확신 없다"는 신호를 주지 않는다.**

#### Task C: DefaultComposeStep Guardrails 완화 + 템플릿 개선
**파일**: `DefaultComposeStep.java`

변경 내용:
1. `applyGuardrails()`: confidence < 0.75 조건 제거. riskFlags에 `SAFETY_CONCERN` 또는 `REGULATORY_RISK`가 있을 때만 면책 문구 추가
2. `createDraftByTone()`: CONDITIONAL 템플릿의 "조건 의존성이 있어 단정이 어려운" → "관련 사내 자료를 바탕으로 확인한 내용을 안내드립니다"로 변경 (모든 tone)
3. `formatByChannel()`: riskFlags cautionLine은 SAFETY_CONCERN/REGULATORY_RISK일 때만 표시

### Out-of-Scope
- Chunking 전략 변경 (현재 1000자 + 2문장 overlap은 유지)
- 검색 알고리즘 변경 (hybrid search는 정상 동작 확인됨)
- ReviewAgentService / ApprovalAgentService 변경 (답변 품질이 올라가면 자연스럽게 score/confidence 게이트 통과)

---

## 4. 테스트 전략

### 단위 테스트
- `AnalysisService`: 새 임계값 기준으로 verdict 판정 테스트
- `DefaultComposeStep`: guardrails가 SAFETY_CONCERN에서만 작동하는지 확인
- `OpenAiComposeStep`: prompt에 verdict/confidence가 포함되지 않는지 확인

### 통합 테스트 (라이브)
- 배포 후 동일 문의(`aff54da0`)에 대해 답변 재생성
- yellow flag 원인/해결방안이 구체적으로 포함되는지 확인
- review → approval 워크플로우가 AUTO_APPROVED까지 가는지 확인

---

## 5. 성공 기준

| 지표 | Before | Target |
|------|--------|--------|
| yellow flag 문의 verdict | CONDITIONAL (0.586) | SUPPORTED (0.568 > 0.45) |
| 답변에 구체적 원인 포함 | X ("특정 어려움") | O (saturated artifacts / droplet < 1000) |
| 답변에 해결방안 포함 | X | O (exposure time 확인 / cartridge 청소) |
| 면책 문구 | 2개 삽입 | 0개 (SAFETY 아님) |
| approval decision | ESCALATED | AUTO_APPROVED 가능 |

---

## 6. 팀 구성 및 병렬 작업 계획

| Agent | Task | 파일 |
|-------|------|------|
| verdict-tuner | Task A: AnalysisService 임계값 + classifyQuestionPrior 제거 | AnalysisService.java |
| prompt-designer | Task B: OpenAiComposeStep 프롬프트 재설계 | OpenAiComposeStep.java |
| guardrail-fixer | Task C: DefaultComposeStep guardrails + 템플릿 | DefaultComposeStep.java |

3개 파일 모두 독립적이므로 **완전 병렬 실행 가능**.
