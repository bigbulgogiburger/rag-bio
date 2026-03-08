---
name: verify-security
description: 보안 검증 (RateLimitFilter, RBAC, CORS, JWT, 시크릿 관리). 보안 관련 코드 수정, 인증/인가 변경, 필터 수정, CORS 변경, 시크릿 추가 후 사용. 보안 감사나 배포 전 최종 점검에도 사용.
---

## Purpose

백엔드 보안 설정의 일관성과 안전성을 검증합니다.

## Checks

### 1. Rate Limiting

**파일**: `infrastructure/security/RateLimitFilter.java`

- [ ] 최대 요청 수: 100/분/IP 설정 확인
- [ ] 윈도우: 60,000ms (1분)
- [ ] 응답 헤더: `X-RateLimit-Limit`, `X-RateLimit-Remaining` 포함 확인
- [ ] 제외 경로: `/h2-console`, `/actuator/health`만 제외
- [ ] 클라이언트 IP: `X-Forwarded-For` 헤더 우선 사용 확인
- [ ] 429 응답 시 JSON 에러 본문 반환 확인

```bash
grep -n "MAX_REQUESTS\|WINDOW_MS\|X-RateLimit" backend/app-api/src/main/java/com/biorad/csrag/infrastructure/security/RateLimitFilter.java
```

### 2. CORS 설정

**파일**: `infrastructure/security/SecurityConfig.java`

- [ ] 허용 오리진: 환경변수(`CORS_ALLOWED_ORIGINS`)로 관리, 하드코딩 없음
- [ ] 허용 메서드: GET, POST, PUT, PATCH, DELETE, OPTIONS
- [ ] 허용 헤더: `*` (Content-Type, Authorization 등)
- [ ] 노출 헤더: `X-Request-Id`, `Authorization`
- [ ] 자격 증명: `allowCredentials(true)`
- [ ] 프리플라이트 캐시: `maxAge(3600)`
- [ ] `*` 오리진 사용 금지 확인

```bash
grep -n "allowedOrigins\|allowedMethods\|allowCredentials" backend/app-api/src/main/java/com/biorad/csrag/infrastructure/security/SecurityConfig.java
```

### 3. JWT 인증

**파일**: `infrastructure/security/JwtAuthenticationFilter.java`

- [ ] JWT 시크릿: 32자 이상, 환경변수로 관리 (`JWT_SECRET`)
- [ ] 토큰 검증: 서명 + 만료 시간 확인
- [ ] Header fallback: `X-User-Id`, `X-User-Roles` (레거시 호환)
- [ ] 세션 정책: `STATELESS`

```bash
grep -n "JWT_SECRET\|X-User-Id\|X-User-Roles\|STATELESS" backend/app-api/src/main/java/com/biorad/csrag/infrastructure/security/
```

### 4. 필터 체인 순서

- [ ] RateLimitFilter → SecurityHeaderFilter → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter 순서 확인
- [ ] `/api/v1/**` 경로만 인증 필요 확인
- [ ] `/actuator/health`, `/h2-console/**` 공개 확인

### 5. 시크릿 관리

- [ ] `.env`가 `.gitignore`에 포함되어 있는가?
- [ ] `application.yml`에 시크릿 직접 노출 없는가? (`${ENV_VAR:default}` 패턴 사용)
- [ ] API 키가 소스코드에 하드코딩되지 않았는가?

```bash
# 시크릿 하드코딩 탐지
grep -rn "sk-\|api[_-]key\s*=\s*['\"]" backend/app-api/src/main/java/ --include="*.java" | grep -v '${' | grep -v 'test'
```

### 6. 입력 검증

- [ ] `@Valid` 어노테이션이 모든 `@RequestBody`에 적용되었는가?
- [ ] 파일 업로드 크기 제한: `max-file-size: 20MB`, `max-request-size: 25MB`
- [ ] 경로 변수 UUID 타입 검증 (PathVariable UUID)
- [ ] SQL Injection 방지: JPA/Spring Data 사용 (raw SQL 금지)

```bash
grep -rn "@RequestBody" backend/app-api/src/main/java/ --include="*.java" | grep -v "@Valid"
```

### 7. 답변 워크플로우 RBAC

- [ ] 상태 전이 제한: DRAFT → REVIEWED → APPROVED → SENT (역방향 불가)
- [ ] SENT 상태 답변 수정 차단
- [ ] autoWorkflow 5회 제한
- [ ] ApprovalAgent 4단 게이트 확인 (confidence, reviewScore, noCriticalIssues, noHighRiskFlags)

### 8. 문서 다운로드 보안

- [ ] Content-Disposition에 UTF-8 인코딩 적용
- [ ] 페이지 범위 검증 (`from >= 1`, `to >= from`)
- [ ] 문서 ID 기반 접근 (경로 탐색 불가)
- [ ] Inquiry + KB 문서 이중 조회 (접근 권한 확인)

## Exceptions

- H2 콘솔 (`/h2-console/**`): 개발 전용, 프로덕션에서 비활성화
- Actuator health: 모니터링용 공개 엔드포인트

## Related Files

| File | Purpose |
|------|---------|
| `backend/app-api/src/main/java/.../infrastructure/security/RateLimitFilter.java` | Rate limiting |
| `backend/app-api/src/main/java/.../infrastructure/security/SecurityConfig.java` | CORS, 세션, 필터 체인 |
| `backend/app-api/src/main/java/.../infrastructure/security/JwtAuthenticationFilter.java` | JWT 인증 |
| `backend/app-api/src/main/java/.../common/exception/GlobalExceptionHandler.java` | 에러 응답 |
| `backend/app-api/src/main/resources/application.yml` | 보안 설정 (JWT, CORS, 파일 크기) |
| `.gitignore` | 시크릿 파일 제외 확인 |
