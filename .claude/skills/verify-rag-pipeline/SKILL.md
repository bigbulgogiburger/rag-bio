---
name: verify-rag-pipeline
description: RAG 파이프라인 (답변 작성 + 분석 + 다운로드) 검증. 답변 작성/분석/다운로드 관련 코드 수정 후 사용.
---

## Purpose

1. **격식체 프롬프트 규칙** — OpenAiComposeStep 시스템 프롬프트에 8개 규칙 (격식체, 마크다운 금지, 번호 인용 금지 등) 존재 확인
2. **DefaultComposeStep 톤 템플릿** — 3 tone × 3 verdict 조합이 격식체이고 자연어 인용 사용 확인
3. **Citation 형식 일관성** — `chunk=UUID score=0.xxx documentId=UUID fileName=... pageStart=... pageEnd=...` 형식 확인
4. **EvidenceItem 필드 완전성** — record 필드 (chunkId, documentId, score, excerpt, sourceType, fileName, pageStart, pageEnd) 확인
5. **N+1 방지 배치 조인** — AnalysisService에서 findAllById 배치 조회 + sourceId fallback 조회 사용 확인
6. **다운로드 API 안전성** — DocumentDownloadController 엔드포인트 경로, Content-Disposition, 페이지 범위 검증 확인

## When to Run

- ComposeStep (OpenAi/Default) 프롬프트 또는 템플릿 수정 후
- AnswerComposerService citations 형식 변경 후
- EvidenceItem record 필드 추가/변경 후
- AnalysisService retrieve/verify 로직 수정 후
- DocumentDownloadController 엔드포인트 수정 후

## Related Files

| File | Purpose |
|------|---------|
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/OpenAiComposeStep.java` | AI 답변 생성 (시스템 프롬프트 + buildPrompt) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultComposeStep.java` | 폴백 답변 템플릿 (격식체) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerComposerService.java` | 답변 오케스트레이션 + citations 조립 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/EvidenceItem.java` | 근거 데이터 record |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalysisService.java` | 근거 검색 + 판정 + 배치 조인 |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/vector/QdrantVectorStore.java` | Qdrant 벡터 스토어 구현 (검색 + upsert + delete) |
| `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentDownloadController.java` | 문서 다운로드 + PDF 페이지 추출 |

## Workflow

### Step 1: OpenAiComposeStep 시스템 프롬프트 8대 규칙 확인

**파일:** `OpenAiComposeStep.java`

**검사:** 시스템 프롬프트에 격식체, 마크다운 금지, 번호 인용 금지 등 핵심 규칙이 포함되어 있는지 확인.

```bash
grep -n "격식체\|마크다운.*금지\|번호 인용 금지\|이모지.*금지" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/OpenAiComposeStep.java
```

**PASS:** 격식체 존댓말, 마크다운 서식 금지, [1],[2] 번호 인용 금지, 이모지 금지 규칙 모두 존재
**FAIL:** 핵심 규칙 누락 또는 마크다운 허용 문구 존재

### Step 2: OpenAiComposeStep 프롬프트 섹션 형식 확인

**파일:** `OpenAiComposeStep.java`

**검사:** buildPrompt()에서 `[분석 결과]`, `[참고 자료]`, `[요구사항]` 평문 섹션을 사용하는지 확인. `##`, `**` 마크다운 미사용 확인.

```bash
grep -n "\[분석 결과\]\|\[참고 자료\]\|\[요구사항\]\|##\|\\*\\*" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/OpenAiComposeStep.java
```

**PASS:** `[분석 결과]`, `[참고 자료]`, `[요구사항]` 존재하고 `##`/`**` 마크다운 없음
**FAIL:** 마크다운 헤더(`##`) 또는 볼드(`**`) 사용

### Step 3: DefaultComposeStep 톤 템플릿 격식체 확인

**파일:** `DefaultComposeStep.java`

**검사:** 모든 톤 템플릿이 격식체 존댓말("드립니다", "바랍니다", "겠습니다")을 사용하는지 확인.

```bash
grep -n "드립니다\|바랍니다\|겠습니다\|주시기" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultComposeStep.java
```

**PASS:** 격식체 어미 다수 존재
**FAIL:** 반말 또는 비격식체 어미 사용

### Step 4: DefaultComposeStep 채널 포맷 확인

**파일:** `DefaultComposeStep.java`

**검사:** email 채널에 "안녕하세요"/"감사합니다", messenger 채널에 "[요약]" 태그 사용 확인.

```bash
grep -n "안녕하세요\|감사합니다\|\[요약\]" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/orchestration/DefaultComposeStep.java
```

**PASS:** email에 인사/마무리, messenger에 [요약] 태그 존재
**FAIL:** 채널별 포맷 규칙 미준수

### Step 5: AnswerComposerService Citations 형식 확인

**파일:** `AnswerComposerService.java`

**검사:** citations 조립 시 `chunk=`, `score=`, `documentId=`, `fileName=`, `pageStart=`, `pageEnd=` 키를 포함하는지 확인.

```bash
grep -n "chunk=\|score=\|documentId=\|fileName=\|pageStart=\|pageEnd=" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/answer/AnswerComposerService.java
```

**PASS:** 6개 키 모두 존재 (fileName, pageStart, pageEnd는 조건부)
**FAIL:** 필수 키 (chunk, score, documentId) 누락

### Step 6: EvidenceItem record 필드 확인

**파일:** `EvidenceItem.java`

**검사:** EvidenceItem record에 8개 필드가 모두 존재하는지 확인.

```bash
grep -n "chunkId\|documentId\|score\|excerpt\|sourceType\|fileName\|pageStart\|pageEnd" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/EvidenceItem.java
```

**PASS:** chunkId, documentId, score, excerpt, sourceType, fileName, pageStart, pageEnd 모두 존재
**FAIL:** 필드 누락

### Step 7: AnalysisService N+1 방지 배치 조인 확인

**파일:** `AnalysisService.java`

**검사:** chunk/document 조회 시 `findAllById` 배치 쿼리를 사용하는지 확인. 반복문 내 개별 조회(N+1)가 없는지 확인.

```bash
grep -n "findAllById\|findById\|allLookupIds\|sourceId" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/analysis/AnalysisService.java
```

**PASS:** `findAllById` 사용 (chunkIds, docIds 배치) + sourceId fallback으로 allLookupIds 확장 조회
**FAIL:** 반복문 내 `findById` 개별 호출 존재 또는 sourceId fallback 누락

### Step 8: DocumentDownloadController 엔드포인트 확인

**파일:** `DocumentDownloadController.java`

**검사:** `/download`과 `/pages` 엔드포인트가 존재하고, Content-Disposition + UTF-8 인코딩을 사용하는지 확인.

```bash
grep -n "download\|pages\|Content-Disposition\|UTF-8" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentDownloadController.java
```

**PASS:** 두 엔드포인트 존재, Content-Disposition 헤더에 UTF-8 파일명 인코딩
**FAIL:** 엔드포인트 누락 또는 Content-Disposition 미설정

### Step 9: DocumentDownloadController 페이지 범위 검증

**파일:** `DocumentDownloadController.java`

**검사:** PDF 페이지 추출 시 from/to 범위 검증이 있는지 확인.

```bash
grep -n "from < 1\|to < from\|BAD_REQUEST\|NOT_FOUND" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentDownloadController.java
```

**PASS:** from >= 1, to >= from 검증 + 적절한 HTTP 상태 코드 (400, 404)
**FAIL:** 범위 검증 없이 PDF 페이지 추출 시도

### Step 10: DocumentDownloadController 이중 문서 조회

**파일:** `DocumentDownloadController.java`

**검사:** 문서 조회 시 Inquiry 문서와 KB 문서 양쪽을 모두 확인하는 이중 경로가 있는지 확인.

```bash
grep -n "documentRepository\|kbDocRepository\|resolveDocument" backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/document/DocumentDownloadController.java
```

**PASS:** `documentRepository.findById()` → fallback `kbDocRepository.findById()` 이중 조회
**FAIL:** 한쪽 리포지토리만 조회

## Output Format

| # | 검사 항목 | 결과 | 상세 |
|---|----------|------|------|
| 1 | 시스템 프롬프트 8대 규칙 | PASS/FAIL | 누락 규칙 목록 |
| 2 | 프롬프트 평문 섹션 | PASS/FAIL | 마크다운 사용 여부 |
| 3 | 톤 템플릿 격식체 | PASS/FAIL | 비격식체 위치 |
| 4 | 채널 포맷 | PASS/FAIL | email/messenger 규칙 |
| 5 | Citations 형식 | PASS/FAIL | 누락 키 목록 |
| 6 | EvidenceItem 필드 | PASS/FAIL | 누락 필드 목록 |
| 7 | N+1 방지 배치 조인 | PASS/FAIL | 개별 쿼리 위치 |
| 8 | 다운로드 엔드포인트 | PASS/FAIL | 누락 항목 |
| 9 | 페이지 범위 검증 | PASS/FAIL | 검증 누락 항목 |
| 10 | 이중 문서 조회 | PASS/FAIL | 단일 경로 위치 |

## Exceptions

다음은 **위반이 아닙니다**:

1. **테스트 코드의 간략화** — 테스트에서 citations 형식을 단순화하거나 mock 값 사용은 허용
2. **OpenAI 비활성화 시 DefaultComposeStep 사용** — `OPENAI_ENABLED=false`에서 DefaultComposeStep이 primary로 동작하는 것은 정상
3. **비-PDF 파일의 페이지 정보 null** — Word/TXT 파일에서 pageStart/pageEnd가 null인 것은 정상 동작
