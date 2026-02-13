# 23. Sprint6 실행백로그

## 목표
- 토큰 없이 가능한 운영 완성도를 먼저 끌어올리고,
- OpenAI 토큰 제공 이후 실모델 연동을 빠르게 붙일 수 있는 구조를 만든다.

---

## 운영 원칙
1. **No-cost first**: mock/규칙 기반으로 검증 가능한 기능 우선
2. **실서비스 대비**: 권한/감사/지표/테스트를 우선 강화
3. **토큰 도착 즉시 확장 가능**: 인터페이스/플래그 기반 설계 유지

---

## Sprint6-A (토큰 없이 진행)

### S6-A1 RBAC 실전화 (P0)
- [x] `X-Role` mock에서 사용자-권한 모델 기반으로 전환 (`X-User-Id`, `X-User-Roles`)
- [x] 역할 정의: `reviewer`, `approver`, `sender`, `admin`
- [x] 엔드포인트별 권한 매핑 정책 문서화 (`24_Sprint6_RBAC_정책.md`)
- [x] 권한 실패 응답 표준화(403 + 에러 코드: `AUTH_USER_ID_REQUIRED`, `AUTH_ROLE_FORBIDDEN`)

### S6-A2 감사로그 고도화 (P0)
- [x] `GET /answers/audit-logs` 필터 추가
  - [x] 기간(from/to)
  - [x] 상태(status)
  - [x] 행위자(actor)
- [x] 페이지네이션/정렬 추가
- [x] 운영 조회 시나리오 샘플 쿼리 문서화 (`25_Sprint6_감사로그_조회_예시.md`)

### S6-A3 운영 대시보드 지표 확장 (P1)
- [x] send success rate
- [x] duplicate block rate
- [x] fallback draft rate
- [x] 최근 실패 사유 top-N

### S6-A4 E2E/회귀 테스트 강화 (P0)
- [x] 등록→조회→분석→초안→리뷰→승인→발송 E2E 자동화 (`AnswerWorkflowIntegrationTest`)
- [x] RBAC 실패 경로 E2E 추가 (`AnswerRbacIntegrationTest`)
- [x] 중복발송(idempotency) 회귀 테스트 고정 (`AnswerWorkflowIntegrationTest`)

### S6-A5 UI/UX 폴리싱 마감 (P1)
- [x] 상태 배지/토스트 문구 표준화
- [x] 빈 상태/오류 상태 컴포넌트 정리
- [x] 접근성(aria/키보드 포커스) 체크리스트 완료

---

## Sprint6-B (OpenAI 토큰 제공 후)

### S6-B1 LLM 답변 생성 실연동 (P0)
- [ ] LLM provider 인터페이스 구현체 추가(OpenAI)
- [ ] 환경변수 기반 on/off 토글
- [ ] fallback 전략(실패 시 규칙 기반 초안) 유지

### S6-B2 임베딩/검색 실연동 (P0)
- [ ] mock embedding/vector store 대체 경로 추가
- [ ] top-k/threshold 튜닝 파라미터 정리
- [ ] 검색 품질 비교 리포트(기존 vs 실연동)

### S6-B3 품질평가 고도화 (P1)
- [ ] 정확도/출처/형식/가드레일 + LLM 응답 안정성 지표
- [ ] 주기 실행 스크립트 + 리포트 템플릿 업데이트

---

## 산출물
- 코드: RBAC/감사/지표/E2E/LLM연동/임베딩연동
- 문서:
  - Sprint6 실행백로그(본 문서)
  - 권한 정책 문서
  - 운영 지표 정의서
  - LLM 연동 가이드(.env 예시 포함)

---

## 수용 기준 (Acceptance)
1. 토큰 없이도 운영 흐름(등록~발송) 안정 동작
2. 권한/감사/지표 조회가 운영자 관점에서 사용 가능
3. 토큰 제공 후 플래그 전환만으로 LLM 경로 활성화 가능
4. 핵심 E2E + 회귀 테스트가 CI에서 통과

---

## 실행 순서 제안
1) S6-A1 RBAC 실전화
2) S6-A2 감사로그 고도화
3) S6-A4 E2E 강화
4) S6-A3 지표 확장
5) S6-A5 UI 폴리싱
6) 토큰 수령 후 S6-B1/B2/B3 순차 적용
