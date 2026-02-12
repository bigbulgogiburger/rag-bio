# AGENTS.md — rag-bio 작업 가이드

이 문서는 Codex/에이전트가 이 저장소에서 일관되게 작업하기 위한 운영 규칙입니다.

## 1) 프로젝트 개요

- 목적: Bio-Rad CS용 RAG 기반 멀티 에이전트 서비스
- 스택:
  - Frontend: React + Next.js (App Router)
  - Backend: Spring Boot 3.3.8 + JPA + QueryDSL
  - 아키텍처: DDD
  - 개발 방식: TDD

## 2) 저장소 구조

- `frontend/`: Next.js 앱
- `backend/`: Spring 멀티모듈
  - `app-api/`: API 엔트리포인트
  - `contexts/*`: 바운디드 컨텍스트
- `docs/`: 아키텍처/API/테스트 전략 문서
- `infra/`: docker-compose, k8s/terraform placeholder
- `PRD_biorad_cs_rag_agent.md`: 제품 요구사항 원문

## 3) 실행 명령

### Frontend

```bash
cd frontend
npm install
npm run dev
npm run build
```

### Backend

```bash
cd backend
./gradlew build
./gradlew :app-api:bootRun
```

## 4) 개발 원칙 (반드시 준수)

1. DDD 경계 침범 금지
   - 컨텍스트 간 직접 내부 의존 최소화
2. TDD 우선
   - 도메인 규칙/유스케이스 테스트 먼저 작성
3. 근거 기반 응답
   - RAG 결과는 출처(문서/페이지/chunk) 추적 가능해야 함
4. Human-in-the-loop
   - 외부 발송(이메일/메시지)은 승인 플로우 기본
5. 보안
   - 시크릿 커밋 금지 (`.env` 커밋 금지)

## 5) 코드 변경 규칙

- 작은 단위로 변경하고 자주 커밋
- 커밋 메시지 예시:
  - `feat(inquiry): add ask-question usecase`
  - `test(verification): add conditional verdict cases`
  - `chore(infra): add local postgres compose`
- 변경 후 최소 확인:
  - frontend: `npm run build`
  - backend: `./gradlew build`

## 6) 우선순위 백로그 (MVP)

1. 문의 + 파일 업로드 API/UI
2. OCR/청킹(1000)/임베딩/벡터스토어 연동
3. Retriever → Verifier → Answer 에이전트 플로우
4. 판정(맞음/틀림/조건부) + 근거/출처 표시
5. 이메일/고객메시지 초안 + 승인 후 발송

## 7) 금지 사항

- 임의의 fake secret 추가 금지
- PRD에 없는 과도한 범위 확장 금지
- 빌드 실패 상태 커밋 금지

## 8) 문서 동기화

아래 변경이 생기면 문서를 함께 업데이트:
- 아키텍처 변경 → `docs/architecture/overview.md`
- API 변경 → `docs/api/openapi.yaml`
- 테스트 전략 변경 → `docs/testing/strategy.md`

---

필요 시 이 문서를 지속 업데이트하여 에이전트 품질을 높입니다.
