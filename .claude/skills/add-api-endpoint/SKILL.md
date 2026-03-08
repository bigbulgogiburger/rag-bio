---
name: add-api-endpoint
description: REST API 엔드포인트 스캐폴딩 (Controller → UseCase → Service → JPA, DDD 패턴). 새 API 추가, 엔드포인트 생성, CRUD 구현 요청 시 사용. 백엔드에 새 기능을 추가할 때 DDD 레이어 구조를 자동 생성.
---

## Purpose

DDD 레이어 구조에 맞춰 REST API 엔드포인트를 스캐폴딩합니다.
Controller → UseCase → Service → Repository 각 레이어의 코드를 생성합니다.

## Architecture Layers

```
interfaces/rest/         ← Controller (Request/Response DTO)
    ↓ calls
application/usecase/     ← UseCase Interface + Command/Result DTOs
    ↓ implements
application/service/     ← Service (@Transactional, 비즈니스 로직)
    ↓ uses
domain/repository/       ← Repository Interface (순수 도메인)
    ↓ implements
infrastructure/persistence/ ← JPA Entity + Repository Adapter
```

## Step 1: Controller

경로: `backend/app-api/src/main/java/com/biorad/csrag/interfaces/rest/{domain}/`

```java
@RestController
@RequestMapping("/api/v1/{domain}")
@RequiredArgsConstructor
public class XxxController {
    private final XxxUseCase xxxUseCase;

    @PostMapping
    public ResponseEntity<XxxResponse> create(@Valid @RequestBody CreateXxxRequest request) {
        XxxCommand cmd = new XxxCommand(request.field1(), request.field2());
        XxxResult result = xxxUseCase.execute(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(XxxResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<XxxResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(XxxResponse.from(xxxUseCase.getById(id)));
    }
}
```

Request/Response는 record로 정의:
```java
public record CreateXxxRequest(
    @NotBlank String field1,
    @Size(max = 500) String field2
) {}

public record XxxResponse(UUID id, String field1, String status, OffsetDateTime createdAt) {
    public static XxxResponse from(XxxResult result) { ... }
}
```

## Step 2: UseCase + Command/Result

```java
// UseCase 인터페이스
public interface XxxUseCase {
    XxxResult execute(XxxCommand command);
    XxxResult getById(UUID id);
}

// Command (입력 DTO)
public record XxxCommand(String field1, String field2) {}

// Result (출력 DTO)
public record XxxResult(UUID id, String field1, String status, OffsetDateTime createdAt) {}
```

## Step 3: Service

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class XxxService implements XxxUseCase {
    private final XxxRepository xxxRepository;

    @Override
    @Transactional
    public XxxResult execute(XxxCommand command) {
        Xxx entity = Xxx.create(command.field1(), command.field2());
        Xxx saved = xxxRepository.save(entity);
        return toResult(saved);
    }

    @Override
    public XxxResult getById(UUID id) {
        Xxx entity = xxxRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Xxx", id));
        return toResult(entity);
    }
}
```

## Step 4: Domain Entity

팩토리 메서드 패턴 (퍼블릭 생성자 없음):

```java
public class Xxx {
    private UUID id;
    private String field1;
    private String status;
    private OffsetDateTime createdAt;

    private Xxx() {} // JPA용

    // 신규 생성
    public static Xxx create(String field1, String field2) {
        Xxx xxx = new Xxx();
        xxx.id = UUID.randomUUID();
        xxx.field1 = field1;
        xxx.status = "ACTIVE";
        xxx.createdAt = OffsetDateTime.now();
        return xxx;
    }

    // DB에서 복원
    public static Xxx reconstitute(UUID id, String field1, String status, OffsetDateTime createdAt) {
        Xxx xxx = new Xxx();
        xxx.id = id;
        xxx.field1 = field1;
        xxx.status = status;
        xxx.createdAt = createdAt;
        return xxx;
    }
}
```

## Step 5: JPA Entity + Repository Adapter

```java
@Entity
@Table(name = "xxx")
public class XxxJpaEntity {
    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    // fromDomain / toDomain 변환 메서드 필수
    public static XxxJpaEntity fromDomain(Xxx domain) { ... }
    public Xxx toDomain() { ... }
}

@Repository
public class XxxRepositoryAdapter implements XxxRepository {
    private final SpringDataXxxJpaRepository jpaRepository;
    // 도메인 ↔ JPA 변환 처리
}
```

## Step 6: Flyway 마이그레이션

`/add-flyway-migration` 스킬을 사용하여 테이블을 생성합니다.

## Error Handling

GlobalExceptionHandler가 공통 예외를 처리합니다:

```java
// 이미 정의된 예외 클래스 사용
throw new NotFoundException("Xxx", id);           // 404
throw new ConflictException("Xxx already exists"); // 409
throw new BadRequestException("Invalid input");    // 400
```

에러 응답 형식:
```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Xxx not found: {id}",
    "status": 404,
    "requestId": "...",
    "timestamp": "..."
  }
}
```

## Checklist

- [ ] Controller: `@RestController`, `@Valid`, `ResponseEntity` 사용
- [ ] Request/Response: record 타입, `from()` 변환 메서드
- [ ] UseCase: 인터페이스 + 구현 분리
- [ ] Service: `@Transactional(readOnly = true)` 기본, 쓰기는 `@Transactional`
- [ ] Domain: 팩토리 메서드 (create, reconstitute), 퍼블릭 생성자 없음
- [ ] JPA: fromDomain/toDomain 변환
- [ ] Flyway: 마이그레이션 파일 생성됨
- [ ] 에러: GlobalExceptionHandler의 기존 예외 사용

## Related Files

| File | Purpose |
|------|---------|
| `backend/contexts/inquiry-context/` | 가장 완성된 DDD 컨텍스트 참고 |
| `backend/app-api/src/main/java/.../interfaces/rest/` | 기존 Controller 참고 |
| `backend/app-api/src/main/java/.../common/exception/` | 공통 예외 클래스 |
