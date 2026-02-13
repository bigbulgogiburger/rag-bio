# 27. 백엔드 테스트 전략 (시니어 백엔드 엔지니어 관점)

## 목적
- 테스트를 계층별 책임에 맞춰 분리해 **빠르고 신뢰 가능한 회귀망**을 구축한다.
- 표준:
  - Controller: `@WebMvcTest`
  - Service: Mock 기반 Unit Test
  - Repository: `@DataJpaTest`

---

## 1) 테스트 아키텍처 원칙

### 1.1 테스트 피라미드
1. **Service Unit Test (다수)**
   - 비즈니스 분기/정책 검증 중심
   - 가장 빠르고 촘촘해야 함
2. **Controller WebMvcTest (중간)**
   - HTTP 계약, 유효성, 권한, 에러코드 검증
   - 서비스는 mock 처리
3. **Repository DataJpaTest (중간)**
   - JPQL/쿼리/정렬/페이징/필터 정확성 검증
4. **통합테스트(SpringBootTest)는 소수 핵심 시나리오만 유지**

### 1.2 계층 책임 분리
- Controller는 "입력/응답 계약 + 보안/권한"만 검증
- Service는 "도메인 정책/상태 전이"만 검증
- Repository는 "쿼리 의미/성능에 영향 주는 조건"만 검증

### 1.3 테스트 품질 기준
- 단일 테스트가 실패 원인을 명확히 설명해야 함
- Given-When-Then 구조 준수
- 테스트명은 정책 문장처럼 작성
- 시간/랜덤/외부 의존성은 통제

---

## 2) Controller 테스트 전략 (`@WebMvcTest`)

## 범위
- Request validation (`@Valid`, 필수값, 포맷)
- 인증/권한 헤더 처리 (`X-User-Id`, `X-User-Roles`)
- HTTP status, error body, response schema
- 경계 입력(잘못된 UUID, 빈 값, 잘못된 파라미터)

## 비범위
- 비즈니스 계산/상태전이 (Service 테스트에서 검증)
- 실제 DB 질의

## 핵심 케이스 템플릿
1. 정상 요청 → `200/201`
2. 필수값 누락/형식오류 → `400`
3. 권한 부족/식별자 누락 → `403`
4. 리소스 없음 → `404`
5. 상태 충돌 → `409`

## 프로젝트 적용 우선순위
- `AnswerControllerWebMvcTest`
  - review/approve/send 권한 매트릭스
  - audit-logs 쿼리 파라미터 검증
- `InquiryControllerWebMvcTest`
  - create/lookup 입력 유효성

---

## 3) Service 테스트 전략 (Mock 기반 Unit Test)

## 범위
- 도메인 정책/상태전이
- 분기 로직 및 예외
- 의존 객체 호출 여부/횟수 검증 (repository, sender, orchestration)

## 비범위
- HTTP serialization
- 실제 DB/트랜잭션 상세 동작

## 핵심 케이스 템플릿
1. Happy path
2. Invalid state transition
3. Duplicate/idempotency path
4. External dependency failure + fallback
5. Side-effect 검증(로그 저장, 이벤트 기록)

## 프로젝트 적용 우선순위
- `AnswerComposerServiceTest`
  - compose fallback 분기
  - review/approve/send 상태전이
  - sendRequestId idempotency
  - send attempt log outcome 기록
- `OpsMetricsService(또는 Controller 계산부 분리 후)`
  - rate 계산/반올림/0-div 처리

---

## 4) Repository 테스트 전략 (`@DataJpaTest`)

## 범위
- JPQL/Derived Query 결과 정확성
- 필터/정렬/페이징
- 경계 케이스(빈 결과, null 필터)

## 비범위
- 비즈니스 정책

## 핵심 케이스 템플릿
1. 단일 필터 정확성
2. 복합 필터 조합 정확성
3. 정렬 asc/desc
4. 페이징(page,size) 경계
5. count 쿼리 정확성

## 프로젝트 적용 우선순위
- `AnswerDraftJpaRepositoryDataJpaTest`
  - `searchAuditLogs(status/actor/from/to/page/sort)`
  - `countByStatusIn`, `countByRiskFlagsContaining`
- `SendAttemptJpaRepositoryDataJpaTest`
  - `countByOutcome`

---

## 5) 권장 패키지/네이밍 규칙

- Controller
  - `...interfaces.rest.<domain>.*WebMvcTest`
- Service
  - `...<domain>.*ServiceTest`
- Repository
  - `...infrastructure.persistence.<domain>.*DataJpaTest`

예시:
- `AnswerControllerWebMvcTest`
- `AnswerComposerServiceTest`
- `AnswerDraftJpaRepositoryDataJpaTest`

---

## 6) 도입 순서 (2주 제안)

### Week 1 (P0)
1. `AnswerComposerServiceTest` 확장
2. `AnswerControllerWebMvcTest` 신규
3. CI에서 해당 테스트 태스크 상시 실행

### Week 2 (P1)
1. `AnswerDraftJpaRepositoryDataJpaTest`
2. `SendAttemptJpaRepositoryDataJpaTest`
3. 실패 케이스 회귀 보강

---

## 7) CI/운영 정책
- PR 필수 게이트:
  - WebMvcTest + Service Unit + DataJpaTest
- 실패 시 merge 금지
- flaky test 0건 원칙
- 커버리지 도입 시 목표(권장):
  - Service line coverage 80%+
  - 핵심 도메인(branch) 70%+

---

## 8) 체크리스트
- [ ] Controller: 권한/유효성/에러코드 WebMvcTest 준비
- [ ] Service: 상태전이/예외/idempotency MockTest 준비
- [ ] Repository: 필터/정렬/페이징 DataJpaTest 준비
- [ ] 통합테스트는 핵심 시나리오만 유지
- [ ] CI에 계층별 테스트 태스크 고정

---

## 결론
이 전략은 계층 책임에 맞는 테스트를 배치해, 개발 속도와 안정성을 동시에 확보한다.
- 빠른 피드백: Service Unit
- API 계약 신뢰성: WebMvcTest
- 쿼리 정확성: DataJpaTest
