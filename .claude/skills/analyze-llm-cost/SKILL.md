---
name: analyze-llm-cost
description: LLM 모델별 비용 분석 및 티어 변경 시뮬레이션. 비용 분석, 모델 비용, OpenAI 비용, 토큰 비용, cost analysis 요청 시 사용. 모델 변경 검토나 비용 최적화가 필요할 때 사용.
---

## Purpose

RAG 파이프라인의 각 단계별 LLM 모델 사용량과 비용을 분석하고,
모델 변경 시 비용/품질 영향을 시뮬레이션합니다.

## Analysis Workflow

### Step 1: 현재 모델 티어링 확인

`application.yml`에서 현재 설정을 읽습니다:

```yaml
# backend/app-api/src/main/resources/application.yml
openai:
  model:
    chat-heavy: ${OPENAI_CHAT_MODEL_HEAVY:gpt-5-mini}
    chat-medium: ${OPENAI_CHAT_MODEL_MEDIUM:gpt-4.1-mini}
    chat-light: ${OPENAI_CHAT_MODEL_LIGHT:gpt-5-nano}
    embedding: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-large}
```

### Step 2: 서비스별 모델 매핑 확인

`@Value("${openai.model.chat-*}")` 어노테이션을 검색하여 각 서비스가 어떤 티어를 사용하는지 파악합니다:

```bash
grep -rn 'openai.model.chat-' backend/app-api/src/main/java/
```

### Step 3: 단계별 토큰 추정

각 서비스의 프롬프트와 입출력 크기를 분석합니다:
- 프롬프트 파일: `backend/app-api/src/main/resources/prompts/`
- 컨텍스트 크기: EVIDENCE_CHAR_BUDGET (12,000자), 청크 크기 등
- 호출 횟수: 리랭킹은 후보 수만큼 (최대 50회), 적응형 검색은 최대 3회

### Step 4: 비용 계산

모델별 가격표 ($/1M tokens, 2026.03 기준 추정):

| 모델 | Input | Output |
|------|-------|--------|
| gpt-5-mini | ~$1.10 | ~$4.40 |
| gpt-4.1-mini | ~$0.40 | ~$1.60 |
| gpt-4o-mini | ~$0.15 | ~$0.60 |
| gpt-5-nano | ~$0.10 | ~$0.40 |

가격은 변동될 수 있으므로 최신 OpenAI pricing 페이지를 확인하세요.

### Step 5: 시뮬레이션

다양한 모델 조합의 비용을 비교합니다:
- 현재 구성 (3-tier)
- 단일 모델 통일
- 부분 변경 (Heavy만 유지, 나머지 변경 등)

## Output Format

분석 결과를 다음 형태의 테이블로 제공합니다:

| 서비스 | 현재 모델 | 입력 토큰 | 출력 토큰 | 현재 비용 | 변경 비용 |
|--------|----------|----------|----------|----------|----------|
| ComposeStep | gpt-5-mini | ~14K | ~3K | $0.029 | ... |

## Quality Risk Assessment

모델 다운그레이드 시 품질 리스크를 서비스 복잡도에 따라 평가합니다:

| 복잡도 | 서비스 | 다운그레이드 위험 |
|--------|--------|----------------|
| 높음 | ComposeStep (장문 답변 + 인용) | 인용 누락, 톤 불일치 위험 |
| 높음 | CriticAgent (할루시네이션 검출) | 오탐/미탐 증가 위험 |
| 중간 | VerifyStep (사실 검증) | 판정 정확도 소폭 하락 |
| 낮음 | Reranking (점수 매기기) | 거의 영향 없음 |
| 낮음 | HyDE/QueryTranslation (변환) | 거의 영향 없음 |

## Related Files

| File | Purpose |
|------|---------|
| `backend/app-api/src/main/resources/application.yml` | 모델 티어 설정 |
| `backend/app-api/src/main/resources/prompts/` | 프롬프트 파일 (토큰 추정용) |
| `backend/app-api/src/main/java/.../interfaces/rest/` | OpenAI 서비스 클래스들 |
