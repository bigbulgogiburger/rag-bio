# PRD v3 — Bio-Rad CS Copilot 프로덕트 고도화 종합

> **Version**: 3.0
> **Date**: 2026-02-18
> **Author**: Product & Engineering
> **Status**: Draft — Review Required
> **Scope**: Sprint 18 ~ Sprint 32 (15 Sprints, 약 4개월)

---

## 1. Executive Summary

Bio-Rad CS Copilot은 RAG 기반 고객 서비스 자동 응답 시스템으로, MVP(Sprint 1-6)와 서비스 고도화(Sprint 7-17)를 거쳐 핵심 파이프라인이 구축되었다. 본 PRD는 **프로덕션 배포 및 엔터프라이즈급 서비스 전환**을 목표로, 현재 상태(AS-IS) 분석을 기반으로 누락된 기능과 품질 속성을 체계적으로 정의한다.

### 핵심 목표

| # | 목표 | 측정 지표 |
|---|------|----------|
| G1 | 프로덕션 배포 가능한 보안 수준 확보 | 인증/인가 구현, API 키 노출 Zero |
| G2 | 서비스 안정성 및 운영 가시성 확보 | 업타임 99.5%, 평균 응답 < 500ms |
| G3 | 사용자 경험 완성도 향상 | 주요 워크플로우 완료율 95%+ |
| G4 | 팀 생산성 및 코드 품질 보장 | 테스트 커버리지 80%+, E2E 통과율 100% |
| G5 | 확장 가능한 아키텍처 기반 마련 | 수평 확장 지원, 메시지 큐 도입 |

---

## 2. AS-IS 분석 요약

### 2.1 강점 (Keep)

- DDD 기반 도메인 모델 (Inquiry Context 완성도 높음)
- RAG 파이프라인 (Retrieve → Verify → Compose) 정상 동작
- 답변 워크플로우 상태 머신 (DRAFT → REVIEWED → APPROVED → SENT)
- 비동기 인덱싱 (ThreadPool + REQUIRES_NEW 트랜잭션)
- 한국어 UI + 다크 모드 + WCAG AA 수준 접근성
- Flyway 마이그레이션 21개 버전 안정 운영
- shadcn/ui + Tailwind CSS 디자인 시스템

### 2.2 치명적 Gap (Must Fix)

| 영역 | 현재 상태 | 위험도 |
|------|----------|--------|
| **인증/인가** | X-User-Id 헤더 수동 주입, Spring Security 없음 | CRITICAL |
| **시크릿 관리** | .env에 실제 API Key 커밋됨 | CRITICAL |
| **백업/복구** | 자동 백업 없음, DR 계획 부재 | CRITICAL |
| **글로벌 에러 처리** | @ControllerAdvice 없음, 에러 포맷 비일관 | HIGH |
| **프론트엔드 테스트** | 테스트 파일 0개, 테스트 인프라 부재 | HIGH |
| **이메일 발송** | MockEmailSender만 존재, 실제 SMTP 없음 | HIGH |
| **모니터링** | Actuator/Prometheus/Sentry 없음 | HIGH |
| **API 문서화** | Swagger/OpenAPI 없음 | MEDIUM |

### 2.3 미구현 Context 모듈 현황

| 모듈 | 상태 | 비고 |
|------|------|------|
| inquiry-context | 완전 구현 | DDD 전 레이어 |
| ingestion-context | Marker만 존재 | 인덱싱 로직이 app-api에 산재 |
| knowledge-retrieval-context | Marker만 존재 | 검색 로직이 app-api에 산재 |
| verification-context | Marker만 존재 | 검증 로직이 app-api에 산재 |
| response-composition-context | Marker만 존재 | 답변 작성이 app-api에 산재 |
| communication-context | Marker만 존재 | Mock Sender만 존재 |
| audit-context | Marker만 존재 | 감사 로그가 app-api에 산재 |

---

## 3. TO-BE 로드맵

### Phase 1: 프로덕션 기반 확보 (Sprint 18-21, 4주)
### Phase 2: 사용자 경험 완성 (Sprint 22-25, 4주)
### Phase 3: 운영 고도화 (Sprint 26-28, 3주)
### Phase 4: 확장성 & 고급 기능 (Sprint 29-32, 4주)

---

## 4. Phase 1 — 프로덕션 기반 확보

> **목표**: 보안, 안정성, 품질 관점에서 프로덕션 배포 최소 요건 충족

---

### 4.1 [P1-CRITICAL] 인증/인가 시스템 구축

**Sprint**: 18-19 (2주)
**담당**: Backend
**의존성**: 없음 (최우선)

#### 4.1.1 현재 문제

```java
// 현재: 헤더에서 직접 읽는 방식 — 위변조 가능
@RequestHeader(name = "X-User-Id", required = false) String userId
@RequestHeader(name = "X-User-Roles", required = false) String userRoles
```

#### 4.1.2 목표 구조

```
[Browser] → [Frontend] → [Backend API]
              │                │
              └─ JWT Token ────┘
                     │
              [Spring Security]
              ├─ JWT Filter
              ├─ UserDetails
              └─ Role-based Access
```

#### 4.1.3 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| AUTH-01 | Spring Security + JWT 기반 인증 필터 구현 | MUST |
| AUTH-02 | 사용자 엔티티 + 비밀번호 해싱(BCrypt) | MUST |
| AUTH-03 | 로그인 API (`POST /api/v1/auth/login`) → JWT 발급 | MUST |
| AUTH-04 | JWT Refresh Token 메커니즘 (Access 15분 / Refresh 7일) | MUST |
| AUTH-05 | Role 기반 접근 제어: `CS_AGENT`, `REVIEWER`, `APPROVER`, `ADMIN` | MUST |
| AUTH-06 | 기존 X-User-Id/X-User-Roles 헤더 방식 → JWT claim으로 마이그레이션 | MUST |
| AUTH-07 | 프론트엔드 로그인 페이지 + AuthContext + ProtectedRoute | MUST |
| AUTH-08 | 토큰 만료 시 자동 갱신 (Silent Refresh) | SHOULD |
| AUTH-09 | 비밀번호 변경 API | SHOULD |
| AUTH-10 | 로그아웃 시 Refresh Token 무효화 (DB 또는 Redis 블랙리스트) | SHOULD |

#### 4.1.4 기술 스택

```gradle
// backend/app-api/build.gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'io.jsonwebtoken:jjwt-api:0.12.5'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.5'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.5'
```

#### 4.1.5 DB 마이그레이션

```sql
-- V22__users_and_roles.sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'CS_AGENT',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    token VARCHAR(500) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 초기 관리자 계정 (비밀번호는 애플리케이션 시작 시 환경변수에서 읽어 해싱)
INSERT INTO users (username, email, password_hash, display_name, role)
VALUES ('admin', 'admin@biorad.com', '', 'System Admin', 'ADMIN');
```

#### 4.1.6 API 명세

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | /api/v1/auth/login | 로그인, JWT 발급 | Public |
| POST | /api/v1/auth/refresh | Access Token 갱신 | Refresh Token |
| POST | /api/v1/auth/logout | 로그아웃, Refresh Token 무효화 | Bearer Token |
| GET | /api/v1/auth/me | 현재 사용자 정보 | Bearer Token |
| PUT | /api/v1/auth/password | 비밀번호 변경 | Bearer Token |

#### 4.1.7 프론트엔드 변경

```
src/
├─ app/login/page.tsx              # 로그인 페이지 (NEW)
├─ components/auth/
│  ├─ AuthProvider.tsx             # JWT 관리 Context (NEW)
│  ├─ ProtectedRoute.tsx           # 인증 필수 래퍼 (NEW)
│  └─ LoginForm.tsx                # 로그인 폼 (NEW)
├─ lib/api/client.ts               # Authorization 헤더 자동 주입 (MODIFY)
└─ middleware.ts                    # Next.js 미들웨어 — 미인증 리다이렉트 (NEW)
```

#### 4.1.8 인수 조건

- [ ] 유효하지 않은 JWT로 API 호출 시 401 응답
- [ ] 권한 없는 역할로 approve 호출 시 403 응답
- [ ] Access Token 만료 후 Refresh Token으로 자동 갱신
- [ ] 로그아웃 후 Refresh Token 재사용 불가
- [ ] 기존 모든 API 엔드포인트가 JWT 인증 적용

---

### 4.2 [P1-CRITICAL] 시크릿 관리 & 보안 강화

**Sprint**: 18 (1주, 인증과 병행)
**담당**: Backend + DevOps
**의존성**: 없음

#### 4.2.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| SEC-01 | .env 파일 git에서 제거 + .gitignore 추가 | MUST |
| SEC-02 | 노출된 OpenAI / Qdrant API 키 즉시 로테이션 | MUST |
| SEC-03 | git 히스토리에서 시크릿 포함 커밋 감사 | MUST |
| SEC-04 | CORS 허용 오리진에 production 도메인만 추가 | MUST |
| SEC-05 | 보안 헤더 추가 (X-Content-Type-Options, X-Frame-Options, CSP) | MUST |
| SEC-06 | API Rate Limiting (Bucket4j 기반, IP당 100req/분) | SHOULD |
| SEC-07 | 멀티파트 업로드 파일 내용 검증 (매직 바이트 체크) | SHOULD |
| SEC-08 | SQL Injection 방지 검증 (모든 동적 쿼리 파라미터화 확인) | MUST |

#### 4.2.2 보안 헤더 필터

```java
@Component
public class SecurityHeaderFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Content-Security-Policy", "default-src 'self'");
        filterChain.doFilter(request, response);
    }
}
```

#### 4.2.3 인수 조건

- [ ] git 저장소에 시크릿 패턴(sk-proj-, eyJhbG) 검색 결과 0건
- [ ] 보안 헤더가 모든 응답에 포함됨
- [ ] Rate Limit 초과 시 429 응답 + Retry-After 헤더
- [ ] 악성 파일 업로드(exe, sh) 시 400 거부

---

### 4.3 [P1-HIGH] 글로벌 에러 처리 표준화

**Sprint**: 19 (1주)
**담당**: Backend
**의존성**: AUTH 완료 후

#### 4.3.1 목표 에러 응답 포맷

```json
{
  "error": {
    "code": "INQUIRY_NOT_FOUND",
    "message": "문의를 찾을 수 없습니다.",
    "status": 404,
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2026-02-18T09:30:00Z",
    "details": [
      { "field": "inquiryId", "reason": "해당 ID의 문의가 존재하지 않습니다." }
    ]
  }
}
```

#### 4.3.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| ERR-01 | @ControllerAdvice 기반 글로벌 예외 핸들러 구현 | MUST |
| ERR-02 | 비즈니스 예외 체계: NotFoundException, ForbiddenException, ConflictException 등 | MUST |
| ERR-03 | JSR-380 유효성 검증 실패 시 구조화된 필드별 에러 응답 | MUST |
| ERR-04 | 예상치 못한 예외 시 500 + 일반 메시지 (스택트레이스 노출 방지) | MUST |
| ERR-05 | 모든 에러 응답에 requestId 포함 | MUST |
| ERR-06 | 프론트엔드 API 클라이언트에서 에러 코드 기반 한국어 메시지 표시 | SHOULD |

#### 4.3.3 예외 클래스 구조

```
com.biorad.csrag.common.exception/
├─ BusinessException.java          # 추상 기본 클래스
├─ NotFoundException.java           # 404
├─ ForbiddenException.java          # 403
├─ ConflictException.java           # 409 (상태 전이 충돌)
├─ ValidationException.java         # 400 (입력 검증)
├─ ExternalServiceException.java    # 502 (OpenAI, Vector DB 장애)
└─ GlobalExceptionHandler.java      # @ControllerAdvice
```

#### 4.3.4 인수 조건

- [ ] 기존 ResponseStatusException 모두 커스텀 예외로 교체
- [ ] 모든 4xx/5xx 응답이 통일된 JSON 포맷 준수
- [ ] 500 에러 시 stacktrace가 클라이언트에 노출되지 않음
- [ ] requestId가 에러 로그 + 응답에 일치

---

### 4.4 [P1-HIGH] 테스트 인프라 구축 & 커버리지 확보

**Sprint**: 20-21 (2주)
**담당**: Full Stack
**의존성**: ERR 완료 후

#### 4.4.1 백엔드 테스트 목표

| 카테고리 | 현재 | 목표 | 도구 |
|---------|------|------|------|
| 단위 테스트 | 5개 | 30개+ | JUnit 5, Mockito |
| 통합 테스트 | 8개 | 20개+ | @SpringBootTest, Testcontainers |
| WebMvc 테스트 | 4개 | 12개+ | @WebMvcTest, MockMvc |
| 커버리지 | ~22% | 80%+ | JaCoCo (CI 에서 threshold 강제) |

#### 4.4.2 프론트엔드 테스트 구축

| 카테고리 | 현재 | 목표 | 도구 |
|---------|------|------|------|
| 단위 테스트 | 0개 | 40개+ | Vitest + React Testing Library |
| 컴포넌트 테스트 | 0개 | 20개+ | Vitest + RTL |
| E2E 테스트 | 0개 | 10 시나리오 | Playwright |
| 커버리지 | 0% | 70%+ | v8 coverage |

#### 4.4.3 프론트엔드 테스트 환경 설정

```json
// package.json devDependencies 추가
{
  "@testing-library/react": "^16.0.0",
  "@testing-library/user-event": "^14.5.0",
  "@testing-library/jest-dom": "^6.4.0",
  "vitest": "^2.0.0",
  "@vitejs/plugin-react": "^4.3.0",
  "jsdom": "^24.0.0",
  "playwright": "^1.45.0",
  "@playwright/test": "^1.45.0"
}
```

#### 4.4.4 핵심 E2E 시나리오

| # | 시나리오 | 커버리지 |
|---|---------|---------|
| E2E-01 | 로그인 → 대시보드 확인 | 인증 플로우 |
| E2E-02 | 문의 생성 → 문서 업로드 → 인덱싱 완료 대기 | 수집 파이프라인 |
| E2E-03 | 답변 생성 → 리뷰 → 승인 → 발송 | 전체 워크플로우 |
| E2E-04 | KB 문서 업로드 → 인덱싱 → 검색 확인 | 지식 기반 |
| E2E-05 | 문의 목록 필터링 + 페이징 + 상세 이동 | 리스트 UX |

#### 4.4.5 CI 통합

```yaml
# .github/workflows/ci.yml 추가
- name: Backend Coverage Check
  run: |
    COVERAGE=$(./gradlew jacocoTestReport | grep -oP 'Line Coverage: \K[0-9.]+')
    if (( $(echo "$COVERAGE < 80" | bc -l) )); then
      echo "Coverage $COVERAGE% is below 80% threshold"
      exit 1
    fi

- name: Frontend Tests
  run: |
    cd frontend
    npm run test -- --coverage --reporter=json
    npm run test:e2e
```

#### 4.4.6 인수 조건

- [ ] `./gradlew build` 시 JaCoCo 80% 미만이면 빌드 실패
- [ ] `npm run test` 명령어로 프론트엔드 테스트 실행 가능
- [ ] GitHub Actions에서 테스트 결과 + 커버리지 리포트 확인 가능
- [ ] E2E 5개 시나리오 모두 Green

---

### 4.5 [P1-HIGH] API 문서화 (OpenAPI/Swagger)

**Sprint**: 20 (테스트와 병행)
**담당**: Backend
**의존성**: ERR 완료 후

#### 4.5.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| DOC-01 | springdoc-openapi 3.0 통합 | MUST |
| DOC-02 | Swagger UI (`/swagger-ui.html`) 접근 가능 | MUST |
| DOC-03 | 모든 API 엔드포인트에 @Operation, @ApiResponse 어노테이션 | MUST |
| DOC-04 | Request/Response 스키마 예시 포함 | SHOULD |
| DOC-05 | JWT Bearer 인증 스키마 등록 | MUST |
| DOC-06 | API 버전별 문서 분리 (v1) | SHOULD |

#### 4.5.2 설정

```gradle
// backend/app-api/build.gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
```

```yaml
# application.yml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
  default-produces-media-type: application/json
```

#### 4.5.3 인수 조건

- [ ] `/swagger-ui.html` 접근 시 전체 API 목록 렌더링
- [ ] Try It Out 기능으로 JWT 인증 후 API 테스트 가능
- [ ] CI에서 OpenAPI spec JSON 파일 자동 생성

---

## 5. Phase 2 — 사용자 경험 완성

> **목표**: 일상 업무에서 불편함 없는 UX 완성도

---

### 5.1 [P2-HIGH] 실시간 상태 업데이트 (SSE)

**Sprint**: 22 (1주)
**담당**: Full Stack
**의존성**: Phase 1 완료

#### 5.1.1 현재 문제

```typescript
// 현재: 5초마다 폴링 — 불필요한 네트워크 비용, 지연된 피드백
const interval = setInterval(async () => {
  const status = await getInquiryIndexingStatus(inquiryId);
}, 5000);
```

#### 5.1.2 목표 구조

```
[Backend]                          [Frontend]
   │                                   │
   ├─ SseEmitter ──────────────────── EventSource
   │   ├─ indexing.progress            ├─ onmessage → 프로그레스 바 업데이트
   │   ├─ indexing.complete            ├─ 인덱싱 완료 알림
   │   ├─ draft.generating             ├─ 답변 생성 중 스피너
   │   ├─ draft.complete               ├─ 답변 생성 완료 알림
   │   └─ workflow.status-changed      └─ 워크플로우 상태 뱃지 업데이트
```

#### 5.1.3 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| SSE-01 | SseEmitter 기반 서버 → 클라이언트 단방향 이벤트 스트림 | MUST |
| SSE-02 | `GET /api/v1/inquiries/{id}/events` SSE 엔드포인트 | MUST |
| SSE-03 | 인덱싱 진행률 이벤트 (document별 % + 전체 %) | MUST |
| SSE-04 | 답변 생성 파이프라인 단계별 이벤트 (Retrieve/Verify/Compose) | MUST |
| SSE-05 | 연결 끊김 시 자동 재연결 (EventSource 기본 동작) | MUST |
| SSE-06 | 프론트엔드 기존 setInterval 폴링 제거 | MUST |
| SSE-07 | SSE 연결 30분 타임아웃 + 하트비트 (30초 간격) | SHOULD |

#### 5.1.4 백엔드 구현

```java
@GetMapping(value = "/{inquiryId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamEvents(@PathVariable String inquiryId) {
    SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30분
    sseService.register(inquiryId, emitter);
    return emitter;
}
```

#### 5.1.5 프론트엔드 커스텀 훅

```typescript
// src/hooks/useInquiryEvents.ts
export function useInquiryEvents(inquiryId: string) {
  const [indexingProgress, setIndexingProgress] = useState(0);
  const [pipelineStep, setPipelineStep] = useState<string | null>(null);

  useEffect(() => {
    const es = new EventSource(`/api/v1/inquiries/${inquiryId}/events`);
    es.addEventListener("indexing.progress", (e) => { ... });
    es.addEventListener("draft.generating", (e) => { ... });
    return () => es.close();
  }, [inquiryId]);

  return { indexingProgress, pipelineStep };
}
```

#### 5.1.6 인수 조건

- [ ] 문서 인덱싱 시작 → 완료까지 프로그레스 바가 실시간 반영
- [ ] 답변 생성 시 "검색 중 → 검증 중 → 작성 중" 단계 표시
- [ ] setInterval 기반 폴링 코드 완전 제거
- [ ] 브라우저 탭 전환 후 돌아와도 최신 상태 표시

---

### 5.2 [P2-HIGH] 프론트엔드 상태 관리 현대화

**Sprint**: 22-23 (2주)
**담당**: Frontend
**의존성**: SSE 구현과 병행

#### 5.2.1 현재 문제

- useState 86개 산재 — 컴포넌트 간 상태 공유 불가
- API 응답 캐싱 없음 — 페이지 이동마다 재요청
- 요청 중복 방지 없음
- 낙관적 업데이트 없음

#### 5.2.2 도입 기술: TanStack Query (React Query v5)

```json
// package.json
{
  "@tanstack/react-query": "^5.50.0",
  "@tanstack/react-query-devtools": "^5.50.0"
}
```

#### 5.2.3 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| STATE-01 | TanStack Query 기반 서버 상태 관리 전환 | MUST |
| STATE-02 | API 클라이언트를 Query/Mutation 커스텀 훅으로 래핑 | MUST |
| STATE-03 | 자동 캐싱 + staleTime 설정 (문의 목록 30초, 상세 1분) | MUST |
| STATE-04 | 요청 중복 제거 (같은 쿼리 키 동시 요청 시 1회만 실행) | MUST |
| STATE-05 | 낙관적 업데이트 (워크플로우 상태 전이 즉시 반영) | SHOULD |
| STATE-06 | 에러 시 자동 재시도 (최대 3회, 지수 백오프) | SHOULD |
| STATE-07 | React Query DevTools 개발 모드에서 활성화 | SHOULD |

#### 5.2.4 커스텀 훅 구조

```typescript
// src/hooks/queries/
├─ useInquiries.ts          // 문의 목록 (paginated, filtered)
├─ useInquiry.ts            // 문의 상세
├─ useInquiryDocuments.ts   // 문서 목록 + 인덱싱 상태
├─ useAnswerDraft.ts        // 최신 답변 초안
├─ useAnswerHistory.ts      // 답변 이력
├─ useKnowledgeBase.ts      // KB 문서 목록
├─ useDashboardMetrics.ts   // 대시보드 메트릭
└─ mutations/
   ├─ useCreateInquiry.ts   // 문의 생성
   ├─ useGenerateDraft.ts   // 답변 생성
   ├─ useReviewDraft.ts     // 리뷰
   ├─ useApproveDraft.ts    // 승인
   └─ useSendDraft.ts       // 발송
```

#### 5.2.5 인수 조건

- [ ] 문의 목록 → 상세 → 뒤로가기 시 목록이 캐시에서 즉시 렌더링
- [ ] 같은 페이지를 여러 탭에서 열어도 API 요청은 1회
- [ ] 워크플로우 상태 변경 시 UI가 즉시 반영 (서버 확인 후 롤백 가능)
- [ ] 네트워크 오류 시 자동 재시도 + 사용자 알림

---

### 5.3 [P2-HIGH] 글로벌 에러 바운더리 & 토스트 시스템

**Sprint**: 23 (1주)
**담당**: Frontend
**의존성**: STATE 완료 후

#### 5.3.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FE-ERR-01 | React Error Boundary로 페이지 크래시 방지 + 폴백 UI | MUST |
| FE-ERR-02 | API 에러 코드 → 한국어 메시지 매핑 (labels.ts 확장) | MUST |
| FE-ERR-03 | 전역 Toast/Notification 시스템 (Context 기반) | MUST |
| FE-ERR-04 | alert() 호출 전량 제거 → Toast로 교체 | MUST |
| FE-ERR-05 | 네트워크 오프라인 감지 + 배너 표시 | SHOULD |
| FE-ERR-06 | 에러 로깅 서비스 연동 (Sentry) 준비 | SHOULD |

#### 5.3.2 에러 바운더리 계층

```
<AppErrorBoundary>           ← 최상위: 앱 크래시 방지
  <Layout>
    <PageErrorBoundary>      ← 페이지 수준: 개별 페이지 격리
      <InquiryDetailPage>
        <TabErrorBoundary>   ← 탭 수준: 탭 간 격리
          <InquiryAnswerTab />
        </TabErrorBoundary>
      </InquiryDetailPage>
    </PageErrorBoundary>
  </Layout>
</AppErrorBoundary>
```

#### 5.3.3 인수 조건

- [ ] 컴포넌트 런타임 에러 시 전체 앱 크래시 방지, 해당 탭만 에러 표시
- [ ] "다시 시도" 버튼으로 에러 복구 가능
- [ ] `alert()` 호출이 코드베이스에서 0건
- [ ] 네트워크 오프라인 시 상단 배너 표시

---

### 5.4 [P2-MEDIUM] 실제 이메일 발송 구현

**Sprint**: 24 (1주)
**담당**: Backend
**의존성**: AUTH + ERR 완료

#### 5.4.1 현재 문제

```java
// MockEmailSender.java — 실제 발송 없음
public SendResult send(SendCommand command) {
    return new SendResult("mock-email", "email-" + UUID.randomUUID());
}
```

#### 5.4.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| MAIL-01 | JavaMailSender 기반 실제 SMTP 이메일 발송 | MUST |
| MAIL-02 | HTML 이메일 템플릿 (Thymeleaf) — 답변 본문 + 인용 포맷 | MUST |
| MAIL-03 | 첨부파일 지원 (인용 문서 발췌 PDF) | SHOULD |
| MAIL-04 | 발송 실패 시 최대 3회 재시도 (지수 백오프) | MUST |
| MAIL-05 | 발송 결과 SendAttempt 테이블에 기록 (성공/실패/에러 상세) | MUST |
| MAIL-06 | 이메일 미리보기 기능 (발송 전 HTML 렌더링 확인) | SHOULD |
| MAIL-07 | SMTP 연결 실패 시 Graceful Degradation (오류 알림만) | MUST |

#### 5.4.3 설정

```yaml
# application.yml
spring:
  mail:
    host: ${SMTP_HOST:smtp.gmail.com}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.connectiontimeout: 5000
      mail.smtp.timeout: 5000
```

#### 5.4.4 이메일 템플릿 구조

```
resources/templates/email/
├─ answer-draft.html       # 답변 발송 메인 템플릿
├─ answer-draft.txt        # 텍스트 폴백
└─ fragments/
   ├─ header.html          # Bio-Rad 로고 + 제목
   ├─ body.html            # 답변 본문
   ├─ citations.html       # 인용 출처 목록
   └─ footer.html          # 면책 + 연락처
```

#### 5.4.5 인수 조건

- [ ] 승인된 답변의 "발송" 클릭 시 실제 이메일 수신 확인
- [ ] 이메일 HTML이 주요 클라이언트(Gmail, Outlook)에서 정상 렌더링
- [ ] SMTP 장애 시 UI에 "발송 실패" 메시지 + 재시도 버튼
- [ ] SendAttempt 테이블에 발송 이력 기록

---

### 5.5 [P2-MEDIUM] 대시보드 분석 강화

**Sprint**: 25 (1주)
**담당**: Full Stack
**의존성**: STATE 완료

#### 5.5.1 현재 문제

- 단순 카운트 메트릭 3개만 표시
- 시계열 추이 없음
- 필터링/기간 선택 불가

#### 5.5.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| DASH-01 | 기간 선택 필터 (오늘/7일/30일/커스텀) | MUST |
| DASH-02 | 문의 처리 현황 차트 (일별 접수/완료 추이, 라인 차트) | MUST |
| DASH-03 | 평균 처리 시간 메트릭 (접수 → 발송 소요 시간) | MUST |
| DASH-04 | 상태별 분포 파이 차트 (DRAFT/REVIEWED/APPROVED/SENT) | SHOULD |
| DASH-05 | AI 리뷰 품질 점수 추이 (평균 점수 / 자동 승인율) | SHOULD |
| DASH-06 | KB 활용 통계 (KB 인용 비율, 가장 많이 인용된 문서 Top 5) | SHOULD |
| DASH-07 | 대시보드 데이터 CSV 내보내기 | SHOULD |

#### 5.5.3 차트 라이브러리

```json
{
  "recharts": "^2.12.0"
}
```

#### 5.5.4 신규 API

| Method | Path | 설명 |
|--------|------|------|
| GET | /api/v1/ops/metrics/timeline | 일별 문의 접수/완료 시계열 |
| GET | /api/v1/ops/metrics/processing-time | 평균 처리 시간 통계 |
| GET | /api/v1/ops/metrics/kb-usage | KB 인용 통계 |

#### 5.5.5 인수 조건

- [ ] 30일 기간 선택 시 일별 추이 차트 렌더링
- [ ] 평균 처리 시간이 시간 단위로 표시
- [ ] CSV 내보내기 버튼으로 현재 메트릭 다운로드 가능

---

## 6. Phase 3 — 운영 고도화

> **목표**: 프로덕션 환경에서의 안정적 운영 및 장애 대응 역량

---

### 6.1 [P3-HIGH] 모니터링 & 옵저버빌리티

**Sprint**: 26 (1주)
**담당**: Backend + DevOps
**의존성**: Phase 2 완료

#### 6.1.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| MON-01 | Spring Boot Actuator 활성화 (health, metrics, info) | MUST |
| MON-02 | Micrometer + Prometheus 메트릭 수집 | MUST |
| MON-03 | 커스텀 메트릭: API 응답시간, 인덱싱 처리량, 답변 생성 소요시간 | MUST |
| MON-04 | Grafana 대시보드 템플릿 (JVM, API, 비즈니스 메트릭) | SHOULD |
| MON-05 | 알림 규칙: 응답시간 > 2초, 에러율 > 5%, 디스크 > 80% | SHOULD |
| MON-06 | 구조화된 JSON 로깅 (Logstash 포맷) | MUST |
| MON-07 | Sentry 에러 트래킹 (Backend + Frontend) | SHOULD |

#### 6.1.2 Docker Compose 확장

```yaml
# infra/docker-compose.monitoring.yml
services:
  prometheus:
    image: prom/prometheus:v2.50.0
    ports: ["9090:9090"]
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:10.4.0
    ports: ["3000:3000"]
    volumes:
      - ./grafana/dashboards:/var/lib/grafana/dashboards
```

#### 6.1.3 인수 조건

- [ ] `/actuator/health` 접근 시 UP + DB/디스크 상태 표시
- [ ] `/actuator/prometheus` 에서 Prometheus 포맷 메트릭 노출
- [ ] Grafana에서 API 응답시간, JVM 메모리, 비즈니스 메트릭 확인 가능
- [ ] 에러 발생 시 Sentry에 이벤트 기록

---

### 6.2 [P3-HIGH] 데이터베이스 운영 강화

**Sprint**: 27 (1주)
**담당**: Backend + DevOps
**의존성**: MON 완료 후

#### 6.2.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| DB-01 | HikariCP 커넥션 풀 세부 설정 (max 20, min 5, timeout 20s) | MUST |
| DB-02 | 슬로우 쿼리 로깅 (> 500ms) | MUST |
| DB-03 | 자동 백업 스크립트 (일 1회 pg_dump, 30일 보존) | MUST |
| DB-04 | 백업 복원 절차 문서화 + 복원 테스트 | MUST |
| DB-05 | 인덱스 최적화 리뷰 (EXPLAIN ANALYZE 기반) | SHOULD |
| DB-06 | 데이터 보존 정책 (6개월 이상 인덱싱 데이터 아카이브) | SHOULD |

#### 6.2.2 HikariCP 설정

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
      leak-detection-threshold: 60000
```

#### 6.2.3 백업 자동화

```bash
#!/bin/bash
# scripts/backup-db.sh
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups/postgres"
RETENTION_DAYS=30

pg_dump -U $POSTGRES_USER -h $POSTGRES_HOST $POSTGRES_DB \
  | gzip > "$BACKUP_DIR/csrag_${TIMESTAMP}.sql.gz"

# 보존 기간 초과 파일 삭제
find "$BACKUP_DIR" -name "*.sql.gz" -mtime +$RETENTION_DAYS -delete
```

#### 6.2.4 인수 조건

- [ ] 커넥션 풀 설정이 적용되어 Actuator에서 확인 가능
- [ ] 500ms 초과 쿼리가 WARN 레벨로 로깅
- [ ] 백업 스크립트 cron 등록 + 복원 테스트 1회 이상 수행
- [ ] 백업 파일이 30일 후 자동 삭제

---

### 6.3 [P3-MEDIUM] CI/CD 파이프라인 강화

**Sprint**: 28 (1주)
**담당**: DevOps
**의존성**: Phase 2 완료

#### 6.3.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| CICD-01 | Docker 이미지 빌드 + 태깅 (git SHA + latest) | MUST |
| CICD-02 | Container Registry 푸시 (GitHub Container Registry) | MUST |
| CICD-03 | 보안 스캔: Trivy (컨테이너), CodeQL (SAST) | MUST |
| CICD-04 | 의존성 취약점 스캔 (Dependabot / Snyk) | SHOULD |
| CICD-05 | 스테이징 환경 자동 배포 (main 머지 시) | SHOULD |
| CICD-06 | 프로덕션 수동 승인 게이트 | SHOULD |
| CICD-07 | E2E 테스트를 CI에서 docker-compose로 실행 | SHOULD |

#### 6.3.2 GitHub Actions 워크플로우 확장

```yaml
# .github/workflows/cd.yml
name: CD Pipeline
on:
  push:
    branches: [main]

jobs:
  build-images:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build & Push Backend Image
        uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: true
          tags: ghcr.io/${{ github.repository }}/backend:${{ github.sha }}

      - name: Build & Push Frontend Image
        uses: docker/build-push-action@v5
        with:
          context: ./frontend
          push: true
          tags: ghcr.io/${{ github.repository }}/frontend:${{ github.sha }}

  security-scan:
    runs-on: ubuntu-latest
    steps:
      - name: Trivy Container Scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ghcr.io/${{ github.repository }}/backend:${{ github.sha }}
          severity: CRITICAL,HIGH

  deploy-staging:
    needs: [build-images, security-scan]
    runs-on: ubuntu-latest
    environment: staging
    # ... deploy to staging
```

#### 6.3.3 인수 조건

- [ ] main 브랜치 머지 시 Docker 이미지 자동 빌드 + GHCR 푸시
- [ ] CRITICAL 보안 취약점 발견 시 파이프라인 실패
- [ ] 스테이징 환경에서 최신 이미지 자동 배포 확인

---

## 7. Phase 4 — 확장성 & 고급 기능

> **목표**: 서비스 성장에 대비한 아키텍처 확장 및 차별화 기능

---

### 7.1 [P4-MEDIUM] 캐싱 레이어 도입 (Redis)

**Sprint**: 29 (1주)
**담당**: Backend
**의존성**: Phase 3 완료

#### 7.1.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| CACHE-01 | Redis 인프라 추가 (docker-compose) | MUST |
| CACHE-02 | Spring Cache + Redis 통합 | MUST |
| CACHE-03 | 벡터 검색 결과 캐싱 (질문 해시 키, TTL 1시간) | MUST |
| CACHE-04 | KB 통계 캐싱 (TTL 5분) | SHOULD |
| CACHE-05 | 대시보드 메트릭 캐싱 (TTL 1분) | SHOULD |
| CACHE-06 | 캐시 무효화 전략 (문서 인덱싱 완료 시 관련 캐시 삭제) | MUST |

#### 7.1.2 Docker Compose

```yaml
services:
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru
```

#### 7.1.3 캐싱 적용 대상

| 대상 | 캐시 키 | TTL | 무효화 조건 |
|------|---------|-----|-----------|
| 벡터 검색 결과 | `vector:{queryHash}` | 1시간 | 문서 인덱싱 완료 시 |
| KB 통계 | `kb:stats` | 5분 | KB 문서 추가/삭제 시 |
| 대시보드 메트릭 | `ops:metrics:{period}` | 1분 | 답변 상태 변경 시 |
| 사용자 정보 | `user:{userId}` | 15분 | 사용자 정보 변경 시 |

#### 7.1.4 인수 조건

- [ ] 동일 질문 2회 검색 시 2번째는 캐시에서 < 50ms 응답
- [ ] 문서 인덱싱 후 관련 벡터 검색 캐시 자동 무효화
- [ ] Redis 장애 시 캐시 무시하고 정상 동작 (Graceful Degradation)

---

### 7.2 [P4-MEDIUM] 사용자 관리 & 팀 기능

**Sprint**: 29-30 (2주)
**담당**: Full Stack
**의존성**: AUTH 완료

#### 7.2.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| USER-01 | 사용자 목록 관리 페이지 (ADMIN 전용) | MUST |
| USER-02 | 사용자 생성/수정/비활성화 CRUD | MUST |
| USER-03 | 역할 변경 (CS_AGENT, REVIEWER, APPROVER, ADMIN) | MUST |
| USER-04 | 문의 담당자 배정 기능 | SHOULD |
| USER-05 | 내 프로필 페이지 (이름, 이메일, 비밀번호 변경) | SHOULD |
| USER-06 | 활동 이력 (내가 처리한 문의 목록) | SHOULD |

#### 7.2.2 신규 API

| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| GET | /api/v1/users | 사용자 목록 | ADMIN |
| POST | /api/v1/users | 사용자 생성 | ADMIN |
| PUT | /api/v1/users/{id} | 사용자 수정 | ADMIN |
| PATCH | /api/v1/users/{id}/role | 역할 변경 | ADMIN |
| PATCH | /api/v1/users/{id}/active | 활성/비활성 토글 | ADMIN |
| GET | /api/v1/users/me | 내 정보 | Authenticated |
| PUT | /api/v1/users/me/password | 비밀번호 변경 | Authenticated |

#### 7.2.3 프론트엔드 구조

```
src/app/
├─ settings/
│  ├─ page.tsx              # 설정 메인 (프로필)
│  └─ users/page.tsx        # 사용자 관리 (ADMIN)
```

#### 7.2.4 인수 조건

- [ ] ADMIN 역할만 사용자 관리 메뉴 접근 가능
- [ ] CS_AGENT 역할 사용자가 /settings/users 접근 시 403 페이지
- [ ] 사용자 비활성화 시 해당 계정으로 로그인 불가

---

### 7.3 [P4-MEDIUM] 알림 센터

**Sprint**: 30-31 (2주)
**담당**: Full Stack
**의존성**: SSE + USER 완료

#### 7.3.1 현재 문제

- Toast 알림만 존재 (3-5초 후 소멸)
- 알림 이력 확인 불가
- 담당자에게 알림 전달 수단 없음

#### 7.3.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| NOTI-01 | 인앱 알림 센터 (헤더 벨 아이콘 + 드롭다운) | MUST |
| NOTI-02 | 알림 유형: 문의 배정, 리뷰 요청, 승인 완료, 발송 결과 | MUST |
| NOTI-03 | 읽음/안읽음 상태 관리 | MUST |
| NOTI-04 | 알림 클릭 시 해당 문의 상세로 이동 | MUST |
| NOTI-05 | SSE를 통한 실시간 알림 수신 | MUST |
| NOTI-06 | 알림 목록 페이지 (전체 이력, 필터, 페이징) | SHOULD |
| NOTI-07 | 이메일 알림 (선택적: 리뷰 요청 시 담당자에게) | SHOULD |

#### 7.3.3 DB 마이그레이션

```sql
-- V23__notifications.sql
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,  -- INQUIRY_ASSIGNED, REVIEW_REQUESTED, APPROVED, SENT, SEND_FAILED
    title VARCHAR(200) NOT NULL,
    message TEXT,
    inquiry_id UUID REFERENCES inquiries(id),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read) WHERE is_read = FALSE;
```

#### 7.3.4 인수 조건

- [ ] 리뷰 요청 시 REVIEWER 역할 사용자에게 실시간 알림 표시
- [ ] 벨 아이콘에 읽지 않은 알림 수 뱃지 표시
- [ ] 알림 클릭 시 해당 문의 답변 탭으로 이동
- [ ] 전체 읽음 처리 기능 동작

---

### 7.4 [P4-LOW] 문의 내보내기 & 보고서

**Sprint**: 31 (1주)
**담당**: Full Stack
**의존성**: DASH 완료

#### 7.4.1 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| EXPORT-01 | 문의 목록 CSV 내보내기 (현재 필터 적용) | MUST |
| EXPORT-02 | 개별 문의 상세 PDF 내보내기 (질문 + 답변 + 인용) | SHOULD |
| EXPORT-03 | 기간별 처리 보고서 (월간/주간 자동 생성) | SHOULD |
| EXPORT-04 | 내보내기 이력 관리 (최근 10건) | SHOULD |

#### 7.4.2 API

| Method | Path | 설명 |
|--------|------|------|
| GET | /api/v1/inquiries/export/csv | CSV 다운로드 (동일 필터 파라미터) |
| GET | /api/v1/inquiries/{id}/export/pdf | 개별 문의 PDF |
| GET | /api/v1/reports/summary | 기간별 요약 보고서 |

#### 7.4.3 인수 조건

- [ ] CSV 내보내기 시 현재 필터 조건이 반영된 데이터 다운로드
- [ ] PDF에 질문, 답변 전문, 인용 출처, 처리 이력 포함
- [ ] 1000건 이상 데이터 내보내기 시 타임아웃 없이 처리 (스트리밍)

---

### 7.5 [P4-LOW] DDD Context 모듈 리팩토링

**Sprint**: 32 (1주)
**담당**: Backend
**의존성**: 전체 기능 안정화 후

#### 7.5.1 현재 문제

app-api에 대부분의 비즈니스 로직이 집중되어 있고, 6개의 Context 모듈은 Marker 클래스만 존재.

#### 7.5.2 리팩토링 대상

| 현재 위치 (app-api) | 이동 대상 Context | 주요 클래스 |
|--------------------|-------------------|-----------|
| ChunkingService, DocumentTextExtractor | ingestion-context | 문서 파싱/청킹 |
| AnalysisService, VectorStore | knowledge-retrieval-context | 벡터 검색/분석 |
| VerificationStep | verification-context | 근거 검증 |
| AnswerComposerService, ComposeStep | response-composition-context | 답변 작성 |
| MessageSender, EmailSender | communication-context | 발송 |
| OrchestrationRun 관련 | audit-context | 감사 로그 |

#### 7.5.3 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| DDD-01 | ingestion-context로 문서 파싱/청킹 로직 이전 | SHOULD |
| DDD-02 | communication-context로 발송 로직 이전 | SHOULD |
| DDD-03 | 모듈 간 의존성은 인터페이스(Port)를 통해서만 허용 | MUST |
| DDD-04 | 리팩토링 후 기존 테스트 100% 통과 | MUST |
| DDD-05 | 각 Context에 최소 1개 이상의 단위 테스트 추가 | SHOULD |

#### 7.5.4 인수 조건

- [ ] app-api의 비즈니스 로직 클래스 수 50% 감소
- [ ] 각 Context 모듈에 domain + application 레이어 존재
- [ ] 모듈 간 순환 의존성 Zero
- [ ] `./gradlew build` 전체 통과

---

## 8. 전체 스프린트 타임라인

```
Phase 1: 프로덕션 기반 (Sprint 18-21, 4주)
╔═════════════╦══════════════╦═══════════════╦═══════════════════╗
║ Sprint 18   ║ Sprint 19    ║ Sprint 20     ║ Sprint 21         ║
║             ║              ║               ║                   ║
║ 인증/인가   ║ 인증 완성    ║ API 문서화    ║ FE 테스트 구축    ║
║ 시크릿 관리 ║ 에러 처리    ║ BE 테스트 강화║ E2E 시나리오      ║
╚═════════════╩══════════════╩═══════════════╩═══════════════════╝

Phase 2: UX 완성 (Sprint 22-25, 4주)
╔═════════════╦══════════════╦═══════════════╦═══════════════════╗
║ Sprint 22   ║ Sprint 23    ║ Sprint 24     ║ Sprint 25         ║
║             ║              ║               ║                   ║
║ SSE 실시간  ║ 에러 바운더리║ 이메일 발송   ║ 대시보드 강화     ║
║ React Query ║ 토스트 개선  ║ 구현          ║ 차트/내보내기     ║
╚═════════════╩══════════════╩═══════════════╩═══════════════════╝

Phase 3: 운영 고도화 (Sprint 26-28, 3주)
╔═════════════╦══════════════╦═══════════════╗
║ Sprint 26   ║ Sprint 27    ║ Sprint 28     ║
║             ║              ║               ║
║ 모니터링    ║ DB 운영 강화 ║ CI/CD 강화    ║
║ Prometheus  ║ 백업 자동화  ║ 보안 스캔     ║
╚═════════════╩══════════════╩═══════════════╝

Phase 4: 확장 & 고급 (Sprint 29-32, 4주)
╔═════════════╦══════════════╦═══════════════╦═══════════════════╗
║ Sprint 29   ║ Sprint 30    ║ Sprint 31     ║ Sprint 32         ║
║             ║              ║               ║                   ║
║ Redis 캐싱  ║ 사용자 관리  ║ 알림 센터     ║ DDD 리팩토링      ║
║             ║              ║ 내보내기      ║                   ║
╚═════════════╩══════════════╩═══════════════╩═══════════════════╝
```

---

## 9. 우선순위 매트릭스 (Impact vs Effort)

```
           HIGH IMPACT
               │
   ┌───────────┼───────────┐
   │ 인증/인가 │ 테스트    │
   │ 시크릿    │ 인프라    │
   │ 에러 처리 │           │  LOW EFFORT
   │           │ SSE       │ ─────────────
   │ 이메일    │ State Mgmt│
   │ 모니터링  │           │
   ├───────────┼───────────┤  HIGH EFFORT
   │ CI/CD     │ DDD       │
   │ 대시보드  │ 리팩토링  │
   │ DB 강화   │           │
   │ 캐싱      │ 사용자    │
   │           │ 알림 센터 │
   └───────────┼───────────┘
               │
           LOW IMPACT
```

---

## 10. 리스크 & 완화 전략

| # | 리스크 | 영향 | 확률 | 완화 전략 |
|---|--------|------|------|----------|
| R1 | JWT 인증 도입 시 기존 API 호환성 깨짐 | HIGH | HIGH | 2주 병행 운영 기간, Feature Flag로 점진적 전환 |
| R2 | Redis 장애 시 서비스 중단 | HIGH | LOW | Cache-aside 패턴, Redis 장애 시 DB 직접 조회 폴백 |
| R3 | SMTP 장애로 이메일 발송 실패 | MEDIUM | MEDIUM | 재시도 큐 + Dead Letter, 발송 상태 UI 표시 |
| R4 | SSE 연결 수 폭증 (동시 접속) | MEDIUM | LOW | 연결 수 제한 (사용자당 1개), 30분 타임아웃 |
| R5 | 테스트 구축 기간 부족 | MEDIUM | MEDIUM | 핵심 경로(Happy Path)만 우선, 점진적 커버리지 확대 |
| R6 | DDD 리팩토링 시 기존 기능 회귀 | HIGH | MEDIUM | 리팩토링 전 통합 테스트 커버리지 확보 필수 |
| R7 | 프론트엔드 React Query 마이그레이션 규모 | MEDIUM | HIGH | 페이지 단위 점진적 전환, 기존 useState와 공존 허용 |

---

## 11. 성공 지표 (KPI)

| 지표 | 현재 | Phase 1 목표 | Phase 4 목표 |
|------|------|-------------|-------------|
| 프로덕션 배포 가능 | NO | YES | YES |
| 테스트 커버리지 (BE) | ~22% | 80%+ | 85%+ |
| 테스트 커버리지 (FE) | 0% | 70%+ | 75%+ |
| API 응답시간 (p95) | 측정 불가 | < 500ms | < 300ms |
| 서비스 가용성 | 측정 불가 | 99.5% | 99.9% |
| 보안 취약점 (Critical) | 2+ | 0 | 0 |
| E2E 테스트 통과율 | N/A | 100% | 100% |
| 인증 적용 API | 0% | 100% | 100% |
| 실시간 업데이트 | Polling | SSE | SSE |

---

## 12. 부록

### A. 기술 스택 변경 요약

| 영역 | 추가 기술 |
|------|----------|
| Backend 보안 | Spring Security, JJWT, Bucket4j |
| Backend 모니터링 | Actuator, Micrometer, Prometheus |
| Backend 캐싱 | Spring Cache, Redis |
| Backend 메일 | Spring Mail, Thymeleaf |
| Backend 문서화 | springdoc-openapi |
| Frontend 상태 | TanStack Query v5 |
| Frontend 테스트 | Vitest, RTL, Playwright |
| Frontend 차트 | Recharts |
| Infra 모니터링 | Prometheus, Grafana |
| Infra 보안 | Trivy, CodeQL |

### B. 영향받는 파일 예상

| Phase | 신규 파일 | 수정 파일 | 삭제 파일 |
|-------|----------|----------|----------|
| Phase 1 | ~25 | ~30 | ~5 |
| Phase 2 | ~20 | ~25 | ~10 |
| Phase 3 | ~10 | ~15 | 0 |
| Phase 4 | ~30 | ~20 | ~15 |
| **합계** | **~85** | **~90** | **~30** |

### C. 용어 정의

| 용어 | 설명 |
|------|------|
| SSE | Server-Sent Events — 서버→클라이언트 단방향 실시간 스트림 |
| RBAC | Role-Based Access Control — 역할 기반 접근 제어 |
| TanStack Query | 서버 상태 관리 라이브러리 (구 React Query) |
| Graceful Degradation | 부분 장애 시 서비스 중단 없이 제한된 기능 유지 |
| Cache-aside | 캐시 미스 시 DB 조회 → 캐시 저장 → 응답 패턴 |
| Dead Letter Queue | 재시도 실패한 메시지를 격리하는 큐 |

---

> **다음 단계**: Phase 1 Sprint 18 실행 백로그 작성 및 팀 리뷰 진행
