# PRD v4 — Bio-Rad CS Copilot GAP 분석 & 프로덕트 진화 로드맵

> **Version**: 4.0
> **Date**: 2026-02-21
> **Author**: Product Management (IT PM)
> **Status**: Draft — Stakeholder Review 대기
> **Scope**: Sprint 18+ (기존 PRD v3 이후, 신규 식별 영역)

---

## 1. Executive Summary

Bio-Rad CS Copilot은 MVP(Sprint 1-6) → 서비스 고도화(Sprint 7-17)를 거쳐 RAG 파이프라인 핵심 기능이 구축되었다. 기존 PRD v2(KB, 한국어화, 문의목록)와 PRD v3(인증, 모니터링, 테스트 등)에서 **인프라/보안/운영** 관점의 과제를 정의했으나, **실무 현장에서 CS 업무 효율을 혁신할 비즈니스 기능**은 아직 다루지 못했다.

본 PRD는 Zendesk, Freshdesk, Salesforce Service Cloud, ServiceNow, Intercom, Glean, Guru 등 **업계 선두 CS 플랫폼을 벤치마킹**하고, Bio-Rad CS 현장의 실제 업무 흐름을 분석하여, **기존 PRD에서 누락된 핵심 비즈니스 Gap 12개**를 식별하고 구체적 해결 방안을 제시한다.

### 핵심 발견사항

| # | 영역 | 현재 상태 | 경쟁 수준과의 Gap |
|---|------|----------|-----------------|
| 1 | 고객 관리 | 고객 엔티티 자체가 없음 | CRITICAL — 모든 CS 플랫폼의 기본 기능 |
| 2 | SLA 추적 | 처리 기한 개념 없음 | CRITICAL — B2B 서비스의 핵심 차별화 |
| 3 | 유사 문의 추천 | 과거 Q&A 활용 불가 | HIGH — RAG 시스템의 최대 강점을 미활용 |
| 4 | 제품 카탈로그 | 비구조화된 문자열 | HIGH — 제품-문서 매칭의 근본 해결 |
| 5 | 팀 협업 (내부 노트) | 협업 수단 없음 | HIGH — 복잡 문의 대응 필수 |
| 6 | 답변 템플릿 | 매번 처음부터 생성 | HIGH — CS 효율화의 핵심 |
| 7 | 고객 만족도 추적 | 발송 후 피드백 없음 | MEDIUM — 서비스 품질 폐루프 |
| 8 | 멀티채널 인바운드 | 수동 접수만 가능 | MEDIUM — 자동화 핵심 |
| 9 | AI 성능 대시보드 | RAG 품질 추적 없음 | MEDIUM — AI 시스템 고유 |
| 10 | 실제 OCR 연동 | Mock만 존재 | HIGH — 이미지 PDF 처리 불가 |
| 11 | FAQ 자동 생성 | 축적된 Q&A 미활용 | MEDIUM — 지식 자산화 |
| 12 | 문서 버전 관리 | 덮어쓰기만 가능 | MEDIUM — 매뉴얼 업데이트 추적 |

---

## 2. 벤치마킹 분석

### 2.1 업계 표준 CS 플랫폼 vs Bio-Rad CS Copilot

```
기능 매트릭스 (● = 구현됨, ○ = 미구현, △ = 부분 구현)

                          Zendesk  Freshdesk  ServiceNow  Bio-Rad
─────────────────────────────────────────────────────────────────
티켓 관리 (문의 CRUD)       ●        ●          ●          ●
고객 프로필 DB              ●        ●          ●          ○  ← GAP
SLA 추적 & 에스컬레이션     ●        ●          ●          ○  ← GAP
팀 배정 & 라우팅            ●        ●          ●          ○  ← GAP
내부 노트/코멘트            ●        ●          ●          ○  ← GAP
답변 템플릿(매크로)         ●        ●          ●          ○  ← GAP
고객 만족도(CSAT)           ●        ●          ●          ○  ← GAP
Knowledge Base              ●        ●          ●          ●
AI 답변 생성                △        △          △          ●  ← 강점
RAG 파이프라인              ○        ○          ○          ●  ← 핵심 차별화
유사 티켓 추천              ●        ●          ●          ○  ← GAP
멀티채널 인바운드            ●        ●          ●          ○  ← GAP
보고서 & 분석               ●        ●          ●          △
OCR 문서 처리               △        ○          ●          ○  ← GAP
FAQ 자동 생성               ○        ○          △          ○  ← GAP
제품 카탈로그                ○        ○          ●          ○  ← GAP
```

### 2.2 AI-Native CS 도구 벤치마킹

| 도구 | 핵심 AI 기능 | Bio-Rad에 적용 가능한 인사이트 |
|------|------------|---------------------------|
| **Glean** | 기업 지식 통합 검색, 유사 질문 자동 추천 | 과거 Q&A 기반 유사 문의 추천 적용 가능 |
| **Guru** | 답변 카드 + 검증 워크플로우, 전문가 네트워크 | 답변 템플릿 + 검증 흐름과 유사, 전문가 배정 기능 참고 |
| **Forethought** | 의도 분류 → 자동 라우팅, CSAT 예측 | 제품별 자동 분류 + 담당자 라우팅에 적용 |
| **Coveo** | 관련성 피드백 루프, A/B 테스트 | RAG 답변 품질 피드백 → 검색 정확도 개선 순환 |
| **Ada** | 다국어 자동 응답, 채널 통합 | 영어 문의 자동 번역 답변, 이메일 인바운드 연동 |

### 2.3 B2B 기술 지원 특화 기능 (Bio-Rad 도메인)

| 기능 | 근거 | 우선순위 |
|------|------|---------|
| **제품 카탈로그 관리** | Bio-Rad는 수천 개 제품 보유. 제품-문서 매핑이 정확해야 답변 품질 보장 | CRITICAL |
| **시리얼 번호 기반 추적** | 장비별 이력 관리 (구매일, 펌웨어 버전, 유지보수 이력) | HIGH |
| **에스컬레이션 체계** | 1차 CS → 2차 기술 전문가 → R&D 에스컬레이션 | HIGH |
| **RMA/반품 연동** | 기술 문의 → 교체/수리 연계 | MEDIUM |

---

## 3. Gap 상세 분석 & 요구사항

---

### 3.1 [GAP-1] 고객 관리 시스템 (Customer Management)

**심각도**: CRITICAL
**벤치마크**: 모든 CS 플랫폼의 기본 기능
**기존 PRD 커버리지**: 없음

#### 3.1.1 현재 문제

```java
// AnswerController.java — 발송 시 수신자 정보
String actor = headers.get("X-User-Id"); // "cs-agent" 같은 문자열
// → 실제 고객 이메일 주소가 어디에도 저장되어 있지 않음
```

- `inquiries` 테이블에 고객 연락처 필드 없음
- 이메일 발송 시 수신자 이메일을 직접 입력하는 UI도 없음
- 동일 고객의 과거 문의 이력 추적 불가
- 고객별 제품 보유 현황 파악 불가

#### 3.1.2 목표 구조

```
                    ┌────────────────┐
                    │   Customer     │
                    │ ─────────────  │
                    │ name           │
                    │ email          │
                    │ company        │
                    │ phone          │
                    │ products[]     │──→ 보유 제품 목록
                    │ notes          │
                    └───────┬────────┘
                            │ 1:N
                    ┌───────┴────────┐
                    │   Inquiry      │
                    │ ─────────────  │
                    │ customerId  ←──│──→ 고객 연결
                    │ recipientEmail │──→ 답변 발송 주소
                    │ ...            │
                    └────────────────┘
```

#### 3.1.3 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| CUST-01 | Customer 엔티티 생성 (이름, 이메일, 회사, 전화, 메모) | MUST |
| CUST-02 | 문의 생성 시 고객 선택 또는 신규 등록 | MUST |
| CUST-03 | 고객 상세 페이지 (기본정보 + 문의 이력 타임라인) | MUST |
| CUST-04 | 고객 검색 (이름, 이메일, 회사명) | MUST |
| CUST-05 | 문의 목록에서 고객명 표시 + 클릭 시 고객 상세 이동 | SHOULD |
| CUST-06 | 고객별 보유 제품 등록 (제품 카탈로그와 연결) | SHOULD |
| CUST-07 | 이메일 발송 시 고객 이메일 자동 입력 | MUST |
| CUST-08 | 고객 목록 CSV 가져오기/내보내기 | SHOULD |

#### 3.1.4 DB 마이그레이션

```sql
-- V27__customers.sql
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    email VARCHAR(255),
    company VARCHAR(300),
    phone VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_company ON customers(company);

-- inquiries 테이블에 고객 FK 추가
ALTER TABLE inquiries ADD COLUMN customer_id UUID REFERENCES customers(id);
ALTER TABLE inquiries ADD COLUMN recipient_email VARCHAR(255);
CREATE INDEX idx_inquiries_customer ON inquiries(customer_id);
```

#### 3.1.5 API

| Method | Path | 설명 |
|--------|------|------|
| POST | /api/v1/customers | 고객 등록 |
| GET | /api/v1/customers | 고객 목록 (검색, 페이징) |
| GET | /api/v1/customers/{id} | 고객 상세 |
| PUT | /api/v1/customers/{id} | 고객 수정 |
| GET | /api/v1/customers/{id}/inquiries | 해당 고객의 문의 이력 |
| POST | /api/v1/customers/import | CSV 일괄 가져오기 |
| GET | /api/v1/customers/export | CSV 내보내기 |

#### 3.1.6 프론트엔드

```
src/app/
├─ customers/
│  ├─ page.tsx              # 고객 목록 (검색, 페이징)
│  └─ [id]/page.tsx         # 고객 상세 (정보 + 문의 이력 타임라인)

src/components/inquiry/
├─ CustomerSelector.tsx     # 문의 생성 시 고객 선택/신규등록 모달
```

#### 3.1.7 인수 조건

- [ ] 문의 생성 시 고객을 선택하거나 새로 등록할 수 있다
- [ ] 고객 상세 페이지에서 해당 고객의 전체 문의 이력을 볼 수 있다
- [ ] 이메일 발송 시 고객의 이메일이 수신자로 자동 입력된다
- [ ] 고객 검색 시 이메일/회사명으로 P95 < 500ms 조회 가능
- [ ] 내비게이션에 "고객 관리" 메뉴 추가

---

### 3.2 [GAP-2] SLA 추적 & 에스컬레이션

**심각도**: CRITICAL
**벤치마크**: Zendesk (SLA Policies), Freshdesk (SLA Automations), ServiceNow (SLA Rules)
**기존 PRD 커버리지**: 없음

#### 3.2.1 현재 문제

- 문의 접수부터 발송까지 기한(SLA) 개념 없음
- 처리 지연 문의 자동 감지 불가
- 에스컬레이션 규칙 없음 (1차→2차→R&D)
- 대시보드에 평균 처리시간은 있으나 SLA 준수율은 없음
- 긴급 문의와 일반 문의 구분 불가

#### 3.2.2 SLA 정책 설계

```
┌─────────────────────────────────────────────┐
│           SLA Policy Engine                  │
│                                              │
│  Priority    First Response    Resolution    │
│  ─────────   ──────────────    ──────────    │
│  URGENT      1 영업시간         4 영업시간    │
│  HIGH        4 영업시간         8 영업시간    │
│  NORMAL      8 영업시간         24 영업시간   │
│  LOW         24 영업시간        72 영업시간   │
│                                              │
│  영업시간: 월-금 09:00-18:00 KST             │
│  SLA 일시정지: 고객 응답 대기 중              │
│                                              │
│  위반 시:                                    │
│  ├─ 50% 경과 → 담당자에게 경고 알림          │
│  ├─ 75% 경과 → 팀장에게 에스컬레이션          │
│  └─ 100% 경과 → SLA 위반 기록 + ADMIN 알림   │
└─────────────────────────────────────────────┘
```

#### 3.2.3 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| SLA-01 | 문의 우선순위 필드 추가 (URGENT/HIGH/NORMAL/LOW) | MUST |
| SLA-02 | 우선순위별 SLA 기한 자동 계산 (영업시간 기준) | MUST |
| SLA-03 | SLA 경과 50%/75%/100% 임계값별 알림 발송 | MUST |
| SLA-04 | 에스컬레이션 규칙: SLA 위반 시 상위 역할에 자동 배정 | SHOULD |
| SLA-05 | 문의 목록에 SLA 잔여시간/위반 여부 뱃지 표시 | MUST |
| SLA-06 | 대시보드에 SLA 준수율 메트릭 추가 | MUST |
| SLA-07 | SLA 일시정지: 고객 응답 대기 상태 시 타이머 정지 | SHOULD |
| SLA-08 | SLA 정책 관리 UI (ADMIN: 기한 설정 변경) | SHOULD |
| SLA-09 | 문의 생성 시 키워드 기반 우선순위 자동 분류 (긴급/장비 다운 등) | SHOULD |

#### 3.2.4 DB 마이그레이션

```sql
-- V28__sla_tracking.sql
ALTER TABLE inquiries ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE inquiries ADD COLUMN sla_first_response_due TIMESTAMP;
ALTER TABLE inquiries ADD COLUMN sla_resolution_due TIMESTAMP;
ALTER TABLE inquiries ADD COLUMN sla_first_response_at TIMESTAMP;
ALTER TABLE inquiries ADD COLUMN sla_resolved_at TIMESTAMP;
ALTER TABLE inquiries ADD COLUMN sla_status VARCHAR(20) DEFAULT 'ON_TRACK'; -- ON_TRACK, WARNING, BREACHED
ALTER TABLE inquiries ADD COLUMN assigned_to UUID REFERENCES app_users(id);
ALTER TABLE inquiries ADD COLUMN escalation_level INT DEFAULT 0;

CREATE INDEX idx_inquiries_sla_status ON inquiries(sla_status);
CREATE INDEX idx_inquiries_priority ON inquiries(priority);
CREATE INDEX idx_inquiries_assigned ON inquiries(assigned_to);

CREATE TABLE sla_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    priority VARCHAR(20) UNIQUE NOT NULL,
    first_response_hours INT NOT NULL,
    resolution_hours INT NOT NULL,
    business_hours_start TIME NOT NULL DEFAULT '09:00',
    business_hours_end TIME NOT NULL DEFAULT '18:00',
    business_days VARCHAR(20) NOT NULL DEFAULT 'MON-FRI'
);

INSERT INTO sla_policies (priority, first_response_hours, resolution_hours)
VALUES
    ('URGENT', 1, 4),
    ('HIGH', 4, 8),
    ('NORMAL', 8, 24),
    ('LOW', 24, 72);

CREATE TABLE sla_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inquiry_id UUID NOT NULL REFERENCES inquiries(id),
    event_type VARCHAR(50) NOT NULL, -- WARNING_50, WARNING_75, BREACHED, ESCALATED, PAUSED, RESUMED
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    details TEXT
);
```

#### 3.2.5 백엔드 구현

```java
// SlaService.java — 핵심 로직
@Scheduled(fixedRate = 60000) // 1분마다 체크
public void checkSlaCompliance() {
    List<Inquiry> activeInquiries = inquiryRepository
        .findByStatusNotIn(List.of("SENT", "CLOSED"));

    for (Inquiry inquiry : activeInquiries) {
        Duration remaining = calculateRemainingTime(inquiry);
        double progressPercent = calculateProgress(inquiry);

        if (progressPercent >= 100 && inquiry.getSlaStatus() != BREACHED) {
            inquiry.setSlaStatus(BREACHED);
            notificationService.notifySlaBreached(inquiry);
            escalateIfNeeded(inquiry);
        } else if (progressPercent >= 75 && ...) {
            notificationService.notifySlaWarning(inquiry, 75);
        } else if (progressPercent >= 50 && ...) {
            notificationService.notifySlaWarning(inquiry, 50);
        }
    }
}
```

#### 3.2.6 프론트엔드 변경

```
문의 목록 테이블:
┌───────┬──────────┬────────┬───────┬─────────────┬──────────┐
│ 접수일 │ 질문 요약 │ 우선순위│ 고객  │ SLA 잔여    │ 담당자   │
├───────┼──────────┼────────┼───────┼─────────────┼──────────┤
│ 02-21 │ naica... │ 🔴긴급 │ A사   │ ⚠️ 2h 남음  │ 김CS    │
│ 02-21 │ QX700... │ 🟡높음 │ B사   │ ✅ 6h 남음  │ 이CS    │
│ 02-20 │ ddPCR... │ 🟢보통 │ C사   │ 🔴 SLA 위반 │ 미배정   │
└───────┴──────────┴────────┴───────┴─────────────┴──────────┘
```

#### 3.2.7 인수 조건

- [ ] 문의 생성 시 우선순위 선택 가능 (기본값: NORMAL)
- [ ] SLA 기한이 영업시간 기준으로 자동 계산됨
- [ ] SLA 50% 경과 시 담당자에게 경고 알림 발송
- [ ] SLA 위반 시 문의 목록에 빨간색 "SLA 위반" 뱃지 표시
- [ ] 대시보드에 SLA 준수율 (%) 메트릭 표시

---

### 3.3 [GAP-3] 유사 문의 추천 (Similar Inquiry Recommendation)

**심각도**: HIGH
**벤치마크**: Zendesk (Similar Tickets), Freshdesk (Thank You Detector), Glean (Similar Questions)
**기존 PRD 커버리지**: 없음

#### 3.3.1 현재 문제

- RAG 시스템이 **문서 기반 검색만** 수행
- 과거에 동일/유사한 질문이 들어왔을 때 기존 답변을 활용하지 못함
- CS 담당자가 수동으로 문의 목록을 검색해야 함
- 축적된 Q&A 데이터가 조직 지식으로 전환되지 않음

#### 3.3.2 목표: 과거 Q&A 지식 활용

```
새 문의 접수: "naica multiplex mix에서 gDNA 처리 방법은?"
                    │
                    ▼
        ┌─────────────────────────┐
        │ Similar Inquiry Engine   │
        │                          │
        │ 1. 질문 임베딩 생성       │
        │ 2. 과거 문의 질문 벡터 검색│
        │ 3. 유사도 > 0.75 필터     │
        │ 4. SENT 상태 문의만       │
        └────────┬────────────────┘
                 │
                 ▼
        ┌─────────────────────────┐
        │ 유사 문의 추천 (3건)      │
        │                          │
        │ 📌 유사도 92%             │
        │ Q: "gDNA restriction..."  │
        │ A: "...p.6에 따르면..."   │
        │ 발송일: 2026-02-15        │
        │ [답변 보기] [참고하여 생성]│
        │                          │
        │ 📌 유사도 85%             │
        │ ...                       │
        └─────────────────────────┘
```

#### 3.3.3 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| SIM-01 | 문의 생성 시 질문 텍스트로 유사 과거 문의 검색 (벡터 유사도) | MUST |
| SIM-02 | 유사 문의 결과를 문의 상세 > 정보 탭에 패널로 표시 | MUST |
| SIM-03 | 유사 문의의 질문, 최종 답변, 발송일, 유사도 점수 표시 | MUST |
| SIM-04 | "이 답변 참고하여 생성" 버튼 → 답변 생성 시 context로 주입 | SHOULD |
| SIM-05 | 유사도 임계값 설정 (기본 0.75, 관리자 조정 가능) | SHOULD |
| SIM-06 | 답변이 SENT된 문의만 추천 대상에 포함 | MUST |
| SIM-07 | 추천 결과 클릭 시 해당 문의 상세 페이지 새 탭 열기 | SHOULD |

#### 3.3.4 구현 방안

```java
// SimilarInquiryService.java
public List<SimilarInquiry> findSimilar(String question, int topK) {
    // 1. 질문 텍스트 임베딩
    List<Double> queryVector = embeddingService.embed(question);

    // 2. inquiry_embeddings 테이블에서 유사도 검색
    List<VectorSearchResult> results = inquiryVectorStore.search(queryVector, topK);

    // 3. SENT 상태 필터 + 유사도 임계값 적용
    return results.stream()
        .filter(r -> r.getScore() >= minSimilarityThreshold)
        .map(r -> enrichWithInquiryDetail(r))
        .filter(s -> s.getLatestAnswerStatus().equals("SENT"))
        .collect(toList());
}
```

```sql
-- V29__inquiry_embeddings.sql
CREATE TABLE inquiry_embeddings (
    inquiry_id UUID PRIMARY KEY REFERENCES inquiries(id),
    embedding VECTOR(1536),  -- OpenAI embedding dimension
    question_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 벡터 인덱스 (pgvector 확장)
CREATE INDEX idx_inquiry_embeddings_vector ON inquiry_embeddings
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

#### 3.3.5 API

| Method | Path | 설명 |
|--------|------|------|
| GET | /api/v1/inquiries/{id}/similar | 유사 문의 추천 (자동) |
| POST | /api/v1/inquiries/search/similar | 질문 텍스트로 유사 검색 (수동) |

#### 3.3.6 인수 조건

- [ ] 문의 상세 페이지 진입 시 유사 문의 3건 자동 표시
- [ ] 유사도 90%+ 문의의 기존 답변이 정확하게 표시됨
- [ ] "참고하여 생성" 클릭 시 기존 답변이 RAG 프롬프트에 context로 주입
- [ ] 유사 문의 검색 응답시간 P95 < 2초

---

### 3.4 [GAP-4] 제품 카탈로그 관리

**심각도**: HIGH
**벤치마크**: ServiceNow (CMDB), Bio-Rad 자체 제품 포트폴리오
**기존 PRD 커버리지**: Quantum Jump PRD에서 ProductExtractor 신설했으나 하드코딩 regex 패턴

#### 3.4.1 현재 문제

```java
// QuestionDecomposerService.java — 제품명이 regex로 하드코딩
private static final Pattern PRODUCT_PATTERN = Pattern.compile(
    "(?i)(naica(?:\\s+\\w+)*|vericheck|QX\\d+[\\w]*|CFX\\d+[\\w]*|ddPCR(?:\\s+\\w+)*|Bio-Plex(?:\\s+\\w+)*)");
// → 새 제품 추가 시 코드 수정 필요
// → 동의어/별칭 관리 불가
// → 제품-문서 공식 매핑 없음
```

- 제품 패밀리가 `document_chunks.product_family` VARCHAR 문자열
- 새 제품 추가 시 코드 배포 필요
- KB 문서 업로드 시 제품 연결이 자유 텍스트 입력
- 제품별 보유 문서 수 추적 불가

#### 3.4.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| PROD-01 | Product 엔티티 생성 (이름, 카테고리, 별칭 목록, 설명) | MUST |
| PROD-02 | 제품 CRUD API + 관리 UI (ADMIN 전용) | MUST |
| PROD-03 | 제품 별칭(alias) 관리 — "QX700", "QX 700", "QX-700" 모두 동일 제품 | MUST |
| PROD-04 | KB 문서 업로드 시 제품 연결 (드롭다운 선택) | MUST |
| PROD-05 | ProductExtractorService가 DB 카탈로그 기반으로 매칭 (regex 제거) | MUST |
| PROD-06 | 제품별 보유 문서 통계 표시 | SHOULD |
| PROD-07 | 제품 카탈로그 CSV 가져오기/내보내기 | SHOULD |
| PROD-08 | 문의 생성 시 관련 제품 태그 (자동 감지 + 수동 선택) | SHOULD |

#### 3.4.3 DB

```sql
-- V30__product_catalog.sql
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL UNIQUE,
    category VARCHAR(100),         -- Reagent, Instrument, Software, Kit
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE product_aliases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    alias VARCHAR(200) NOT NULL,
    UNIQUE(alias)
);

CREATE INDEX idx_product_aliases_alias ON product_aliases(LOWER(alias));

-- KB 문서와 제품 M:N 연결
CREATE TABLE knowledge_document_products (
    document_id UUID NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, product_id)
);

-- 문의와 제품 M:N 연결
CREATE TABLE inquiry_products (
    inquiry_id UUID NOT NULL REFERENCES inquiries(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    PRIMARY KEY (inquiry_id, product_id)
);
```

#### 3.4.4 인수 조건

- [ ] ADMIN이 웹 UI에서 제품 등록/수정/별칭 추가 가능
- [ ] "QX700", "QX 700", "QX-700" 입력 시 모두 동일 제품으로 인식
- [ ] KB 문서 업로드 시 드롭다운에서 제품 선택 가능
- [ ] ProductExtractorService가 DB 기반으로 제품 매칭 (regex 하드코딩 제거)
- [ ] 새 제품 추가 시 코드 배포 없이 즉시 적용

---

### 3.5 [GAP-5] 팀 협업 — 내부 노트 & 배정

**심각도**: HIGH
**벤치마크**: Zendesk (Internal Notes), Freshdesk (Team Huddle), Jira SM (Comments)
**기존 PRD 커버리지**: PRD v3에서 사용자 관리 + 담당자 배정 일부 언급, 내부 노트는 없음

#### 3.5.1 현재 문제

- CS 담당자 간 문의에 대한 내부 의견 공유 수단 없음
- 복잡한 기술 문의 시 다른 전문가에게 의견 요청 불가
- 문의 히스토리 탭에 워크플로우 변경만 기록, 인간의 판단/논의 기록 없음
- 담당자 배정 UI 없음

#### 3.5.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| COLLAB-01 | 문의별 내부 노트 작성 기능 (고객에게 노출되지 않는 메모) | MUST |
| COLLAB-02 | 내부 노트에 @멘션으로 특정 사용자 호출 | SHOULD |
| COLLAB-03 | @멘션된 사용자에게 알림 발송 | SHOULD |
| COLLAB-04 | 이력 탭에 내부 노트 타임라인 표시 (워크플로우 변경과 통합) | MUST |
| COLLAB-05 | 문의 담당자 배정/변경 UI + API | MUST |
| COLLAB-06 | 담당자 배정 시 알림 발송 | SHOULD |
| COLLAB-07 | 미배정 문의 필터 (문의 목록에서 "미배정" 뱃지) | MUST |

#### 3.5.3 DB

```sql
-- V31__internal_notes.sql
CREATE TABLE inquiry_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inquiry_id UUID NOT NULL REFERENCES inquiries(id),
    author_id UUID NOT NULL REFERENCES app_users(id),
    content TEXT NOT NULL,
    mentions UUID[],  -- 멘션된 사용자 ID 배열
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inquiry_notes_inquiry ON inquiry_notes(inquiry_id);
```

#### 3.5.4 프론트엔드

```
문의 상세 > 이력 탭 (통합 타임라인):

  ──── 2026-02-21 14:30 ────────────────────────
  [노트] 김CS: "이 문의는 naica 장비 관련인데
         @이엔지 님이 이전에 유사 케이스 처리하셨습니다.
         확인 부탁드립니다."

  ──── 2026-02-21 14:35 ────────────────────────
  [배정] 담당자 변경: 김CS → 이엔지

  ──── 2026-02-21 15:00 ────────────────────────
  [워크플로우] 답변 초안 생성됨 (DRAFT)

  ──── 2026-02-21 15:10 ────────────────────────
  [노트] 이엔지: "p.6 매뉴얼 확인 완료.
         restriction enzyme 처리 권장 맞습니다.
         답변 승인해도 됩니다."
```

#### 3.5.5 인수 조건

- [ ] 문의 상세에서 내부 노트 작성 가능
- [ ] @멘션 시 해당 사용자에게 알림 발송
- [ ] 이력 탭에 노트와 워크플로우 이벤트가 시간순으로 통합 표시
- [ ] 문의 담당자 배정/변경 가능
- [ ] 문의 목록에서 "미배정" 필터 동작

---

### 3.6 [GAP-6] 답변 템플릿 관리 (Answer Templates)

**심각도**: HIGH
**벤치마크**: Zendesk (Macros), Freshdesk (Canned Responses), Intercom (Saved Replies)
**기존 PRD 커버리지**: 없음

#### 3.6.1 현재 문제

- 자주 반복되는 질문 유형에도 매번 RAG 파이프라인 전체 실행
- CS 담당자가 자주 쓰는 답변 문구를 개인적으로 관리 (텍스트 파일, 메모 등)
- 팀 내 표준 답변 공유 수단 없음
- "장비 설치 후 초기 설정" 같은 정형 질문에도 매번 AI가 답변 생성

#### 3.6.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| TMPL-01 | 답변 템플릿 생성/수정/삭제 | MUST |
| TMPL-02 | 템플릿 카테고리 분류 (설치, 유지보수, 시약, 소프트웨어 등) | MUST |
| TMPL-03 | 템플릿에 변수 슬롯 지원 ({{고객명}}, {{제품명}}, {{날짜}}) | SHOULD |
| TMPL-04 | 답변 탭에서 "템플릿에서 시작" 버튼 → 템플릿 선택 → 편집 | MUST |
| TMPL-05 | 발송된 답변에서 "템플릿으로 저장" 버튼 | SHOULD |
| TMPL-06 | 템플릿 사용 통계 (가장 많이 사용된 Top 10) | SHOULD |
| TMPL-07 | 제품별 템플릿 필터링 (제품 카탈로그와 연결) | SHOULD |
| TMPL-08 | 팀 공유 템플릿 vs 개인 템플릿 구분 | SHOULD |

#### 3.6.3 DB

```sql
-- V32__answer_templates.sql
CREATE TABLE answer_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(300) NOT NULL,
    category VARCHAR(100),
    content TEXT NOT NULL,
    variables TEXT,                  -- JSON: ["고객명", "제품명", "날짜"]
    product_id UUID REFERENCES products(id),
    scope VARCHAR(20) DEFAULT 'TEAM', -- TEAM, PERSONAL
    owner_id UUID REFERENCES app_users(id),
    usage_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_templates_category ON answer_templates(category);
CREATE INDEX idx_templates_product ON answer_templates(product_id);
```

#### 3.6.4 인수 조건

- [ ] 답변 탭에서 "템플릿에서 시작" 클릭 시 템플릿 목록 모달 표시
- [ ] 템플릿 선택 시 답변 편집 영역에 내용 삽입
- [ ] 변수 슬롯({{고객명}})이 자동으로 현재 문의 정보로 치환
- [ ] 발송된 답변에서 "템플릿으로 저장" 시 새 템플릿 생성
- [ ] 템플릿 사용 횟수가 추적됨

---

### 3.7 [GAP-7] 고객 만족도 추적 (CSAT)

**심각도**: MEDIUM
**벤치마크**: Zendesk (CSAT Surveys), Freshdesk (Happiness Ratings)
**기존 PRD 커버리지**: 없음

#### 3.7.1 현재 문제

- 답변 발송 후 고객 반응에 대한 피드백 루프 없음
- 답변 품질이 좋았는지/나빴는지 추적 불가
- RAG 파이프라인 개선을 위한 데이터 수집 경로 없음

#### 3.7.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| CSAT-01 | 답변 발송 후 CS 담당자가 고객 피드백을 수동 기록 | MUST |
| CSAT-02 | 피드백 유형: 만족 / 보통 / 불만족 + 사유 텍스트 | MUST |
| CSAT-03 | 이메일 발송 시 만족도 링크 포함 (선택적) | SHOULD |
| CSAT-04 | 대시보드에 CSAT 점수 추이 표시 | MUST |
| CSAT-05 | 불만족 피드백 시 자동 후속 문의 생성 | SHOULD |
| CSAT-06 | CSAT 데이터를 RAG 품질 개선에 활용 (낮은 CSAT 답변 분석) | SHOULD |

#### 3.7.3 DB

```sql
-- V33__csat_feedback.sql
CREATE TABLE csat_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inquiry_id UUID NOT NULL REFERENCES inquiries(id),
    answer_id UUID REFERENCES answer_drafts(id),
    rating VARCHAR(20) NOT NULL,       -- SATISFIED, NEUTRAL, DISSATISFIED
    feedback_text TEXT,
    feedback_source VARCHAR(20),       -- AGENT_INPUT, CUSTOMER_LINK
    recorded_by UUID REFERENCES app_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_csat_inquiry ON csat_feedback(inquiry_id);
```

#### 3.7.4 인수 조건

- [ ] 답변 발송 후 "고객 피드백 기록" 버튼 표시
- [ ] 만족/보통/불만족 선택 + 사유 텍스트 입력 가능
- [ ] 대시보드에 월별 CSAT 점수 (만족 비율 %) 차트 표시
- [ ] 불만족 건의 공통 사유 분석 가능

---

### 3.8 [GAP-8] 멀티채널 인바운드 (자동 접수)

**심각도**: MEDIUM
**벤치마크**: Zendesk (Email Channel), Freshdesk (Email Ticketing), Intercom (Inbox)
**기존 PRD 커버리지**: 없음

#### 3.8.1 현재 문제

- 모든 문의가 CS 담당자의 수동 입력으로만 접수됨
- 고객이 이메일로 보낸 문의를 CS 담당자가 복사-붙여넣기
- 외부 포털/폼이 없어 고객이 직접 문의 제출 불가

#### 3.8.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| INBOUND-01 | 이메일 인바운드: 지정 메일주소로 수신 → 자동 문의 생성 | SHOULD |
| INBOUND-02 | 이메일 파싱: 제목 → 질문, 본문 → 상세, 첨부 → 문서 업로드 | SHOULD |
| INBOUND-03 | 고객 이메일 자동 매칭 (기존 고객 DB 조회) | SHOULD |
| INBOUND-04 | 웹 폼 인바운드: 외부 접근 가능한 문의 제출 폼 | SHOULD |
| INBOUND-05 | 인바운드 문의에 자동 우선순위 분류 (키워드 기반) | SHOULD |
| INBOUND-06 | 중복 문의 감지 (동일 고객, 유사 제목, 24시간 이내) | SHOULD |

#### 3.8.3 구현 접근

```
Phase 1 (Sprint N): 이메일 인바운드
  - JavaMailSender로 IMAP 폴링 (5분 간격)
  - 수신 이메일 → Inquiry 자동 생성
  - 첨부파일 자동 업로드

Phase 2 (Sprint N+1): 웹 폼
  - 공개 URL (/public/inquiry-form) — 인증 불필요
  - reCAPTCHA 스팸 방지
  - 제출 시 자동 문의 생성 + 접수 확인 이메일 발송
```

---

### 3.9 [GAP-9] AI 성능 대시보드 (RAG Quality Metrics)

**심각도**: MEDIUM
**벤치마크**: Coveo (Relevance Dashboard), Ragas (RAG Evaluation Framework)
**기존 PRD 커버리지**: 없음 (기존 대시보드는 운영 메트릭만)

#### 3.9.1 현재 문제

- RAG 파이프라인의 답변 품질을 정량적으로 추적하지 않음
- 어떤 유형의 질문에서 답변이 부정확한지 파악 불가
- "I Don't Know" 경로 발동 빈도 모름
- Self-Review 점수 추이 없음
- 제품별/카테고리별 답변 품질 차이 분석 불가

#### 3.9.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| AIMON-01 | 답변 생성 파이프라인 메트릭 수집 (Retrieve 정확도, Verify 판정 분포, Compose 소요시간) | MUST |
| AIMON-02 | Self-Review 점수 추이 차트 (일별/주별) | MUST |
| AIMON-03 | "I Don't Know" 발동 빈도 + 해당 질문 유형 분석 | MUST |
| AIMON-04 | 제품별 답변 품질 히트맵 (어떤 제품에서 답변 품질이 낮은지) | SHOULD |
| AIMON-05 | KB 문서 활용 랭킹 (가장 많이 인용된 Top 10, 인용되지 않는 문서 목록) | SHOULD |
| AIMON-06 | 답변 수정률 (AI 초안 → 인간 수정 비율) — AI 정확도 프록시 | SHOULD |
| AIMON-07 | Hallucination 감지: 답변에 인용 없는 수치/절차가 포함된 비율 | SHOULD |
| AIMON-08 | AI 성능 대시보드 전용 페이지 | MUST |

#### 3.9.3 프론트엔드

```
/ai-analytics (새 페이지)

┌─────────────────────────────────────────────────────────┐
│ AI 성능 대시보드                     기간: [최근 30일 ▼] │
├─────────────┬──────────────┬───────────────┬────────────┤
│ 평균 Review  │ IDK 발동률   │ 답변 수정률    │ CSAT 연동  │
│ 점수: 7.8/10 │ 12.5%        │ 34%           │ 85% 만족   │
├─────────────┴──────────────┴───────────────┴────────────┤
│                                                          │
│  [Self-Review 점수 추이 라인차트]                         │
│  ──────────────────────────────────────────               │
│  8.2 ─ ╱╲                                                │
│  7.5 ─╱  ╲──╱╲─────╱╲──                                 │
│  7.0 ─        ╲──╱                                       │
│                                                          │
├──────────────────────────────────────────────────────────┤
│ 제품별 답변 품질 히트맵                                    │
│                                                          │
│ naica multiplex  ████████████████░░  85%                  │
│ QX700            ████████████░░░░░░  70%                  │
│ Vericheck        ██████████████████  92%                  │
│ CFX96            ████████░░░░░░░░░░  55% ← 개선 필요      │
│                                                          │
├──────────────────────────────────────────────────────────┤
│ KB 문서 활용 랭킹                   인용 0건 문서          │
│                                                          │
│ 1. naica_manual.pdf (42회 인용)     - old_protocol_v1.pdf │
│ 2. QX700_guide.pdf (38회 인용)      - deprecated_faq.pdf  │
│ 3. ddPCR_protocol.pdf (35회 인용)   - internal_memo.pdf   │
└──────────────────────────────────────────────────────────┘
```

#### 3.9.4 인수 조건

- [ ] AI 성능 대시보드 페이지에서 Self-Review 점수 추이 확인 가능
- [ ] 제품별 답변 품질이 히트맵으로 시각화
- [ ] 인용되지 않는 KB 문서 목록 확인 가능
- [ ] "I Don't Know" 발동률이 일별로 추적됨

---

### 3.10 [GAP-10] 실제 OCR 서비스 연동

**심각도**: HIGH
**벤치마크**: Google Cloud Vision, AWS Textract, Azure Document Intelligence
**기존 PRD 커버리지**: 없음

#### 3.10.1 현재 문제

```java
// MockOcrService.java
public String extractText(byte[] imageData) {
    return "[MOCK_OCR] extracted text from image-based document";
    // → 이미지 기반 PDF의 텍스트 추출 불가
    // → 스캔된 매뉴얼, 사진 찍은 문서 등 처리 불가
}
```

- Bio-Rad 기술 문서 중 상당수가 스캔 PDF (이미지 기반)
- 고객이 촬영한 장비 화면/에러 메시지 이미지 처리 불가
- OCR 신뢰도가 하드코딩 0.65로 고정

#### 3.10.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| OCR-01 | Google Cloud Vision 또는 AWS Textract 연동 | MUST |
| OCR-02 | 이미지 기반 PDF 텍스트 추출 | MUST |
| OCR-03 | OCR 결과 신뢰도 점수 반영 | SHOULD |
| OCR-04 | OCR 처리 상태 UI 표시 (처리 중/완료/실패) | MUST |
| OCR-05 | OCR 비용 모니터링 (페이지당 과금 추적) | SHOULD |
| OCR-06 | Mock/Real OCR 전환 가능 (기존 Provider 패턴 적용) | MUST |

---

### 3.11 [GAP-11] FAQ 자동 생성

**심각도**: MEDIUM
**벤치마크**: Guru (Auto-detect Knowledge Gaps), Zendesk (Answer Bot suggestions)
**기존 PRD 커버리지**: 없음

#### 3.11.1 현재 문제

- 동일/유사 질문이 반복적으로 접수되어도 자동으로 FAQ화 되지 않음
- 축적된 Q&A 데이터(질문+답변+인용)가 조직 지식 자산으로 전환되지 않음
- KB에 FAQ 문서를 수동으로 작성해야 함

#### 3.11.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| FAQ-01 | 발송된 답변(SENT)을 분석하여 자주 묻는 질문 클러스터링 | SHOULD |
| FAQ-02 | 클러스터별 대표 Q&A 쌍 추출 | SHOULD |
| FAQ-03 | "FAQ로 등록" 버튼 → KB에 FAQ 문서로 자동 저장 | SHOULD |
| FAQ-04 | FAQ 문서가 향후 유사 문의에서 우선 검색됨 | SHOULD |
| FAQ-05 | FAQ 후보 목록 관리 페이지 (검토 → 승인 → 등록) | SHOULD |

---

### 3.12 [GAP-12] 문서 버전 관리

**심각도**: MEDIUM
**벤치마크**: Confluence (Page History), SharePoint (Document Versioning)
**기존 PRD 커버리지**: 없음

#### 3.12.1 현재 문제

- KB 문서 업데이트 시 기존 버전 삭제 → 새 버전 업로드
- 매뉴얼 개정 이력 추적 불가
- 이전 버전 매뉴얼 기반 답변과 현재 버전 간 차이 확인 불가
- "이 답변은 구 버전 매뉴얼 기반입니다" 경고 불가

#### 3.12.2 요구사항

| ID | 요구사항 | 우선순위 |
|----|---------|---------|
| VER-01 | KB 문서 업데이트 시 기존 버전 보존 (새 버전으로 교체) | MUST |
| VER-02 | 버전 이력 조회 (버전 번호, 업로드일, 업로더) | MUST |
| VER-03 | 이전 버전 다운로드 가능 | SHOULD |
| VER-04 | 최신 버전만 검색 대상에 포함 (구 버전 벡터는 비활성) | MUST |
| VER-05 | 답변 인용에 문서 버전 정보 포함 "(매뉴얼 v3, p.6)" | SHOULD |

---

## 4. 우선순위 매트릭스 & 로드맵

### 4.1 Impact vs Effort 매트릭스

```
           HIGH IMPACT
               │
   ┌───────────┼───────────┐
   │           │           │
   │ GAP-1     │ GAP-3     │
   │ 고객관리  │ 유사문의  │
   │           │ 추천      │  LOW EFFORT
   │ GAP-2     │           │ ─────────────
   │ SLA추적   │ GAP-6     │
   │           │ 답변템플릿 │
   │ GAP-10    │           │
   │ OCR       │           │
   ├───────────┼───────────┤  HIGH EFFORT
   │           │           │
   │ GAP-4     │ GAP-11    │
   │ 제품카탈  │ FAQ자동   │
   │           │ 생성      │
   │ GAP-5     │           │
   │ 팀협업    │ GAP-12    │
   │           │ 문서버전  │
   │ GAP-8     │           │
   │ 멀티채널  │           │
   │           │           │
   └───────────┼───────────┘
               │
           LOW IMPACT
```

### 4.2 추천 실행 로드맵

```
══════════════════════════════════════════════════════════════
Phase A: CS 기본기 완성 (4주, Sprint 18-21)
  ※ PRD v3 Phase 1 (인증/보안)과 병행 또는 후속
──────────────────────────────────────────────────────────────
  Sprint 18-19: GAP-1 고객 관리 시스템
  Sprint 20:    GAP-10 실제 OCR 연동
  Sprint 21:    GAP-6 답변 템플릿 관리

══════════════════════════════════════════════════════════════
Phase B: 업무 효율 혁신 (4주, Sprint 22-25)
──────────────────────────────────────────────────────────────
  Sprint 22-23: GAP-2 SLA 추적 & 에스컬레이션
  Sprint 24:    GAP-4 제품 카탈로그 관리
  Sprint 25:    GAP-3 유사 문의 추천

══════════════════════════════════════════════════════════════
Phase C: 협업 & 품질 루프 (4주, Sprint 26-29)
──────────────────────────────────────────────────────────────
  Sprint 26-27: GAP-5 팀 협업 (내부 노트 & 배정)
  Sprint 28:    GAP-7 고객 만족도 추적
  Sprint 29:    GAP-9 AI 성능 대시보드

══════════════════════════════════════════════════════════════
Phase D: 자동화 & 지식 자산화 (3주, Sprint 30-32)
──────────────────────────────────────────────────────────────
  Sprint 30:    GAP-8 멀티채널 인바운드 (이메일)
  Sprint 31:    GAP-11 FAQ 자동 생성
  Sprint 32:    GAP-12 문서 버전 관리
══════════════════════════════════════════════════════════════
```

---

## 5. PRD v3와의 관계 정리

| PRD v3 항목 | 본 PRD v4와의 관계 |
|------------|-------------------|
| 인증/인가 (AUTH) | **선행 의존성** — GAP-1(고객관리), GAP-5(팀협업)에 필요 |
| 글로벌 에러 처리 (ERR) | **선행 의존성** — 모든 신규 API에 적용 |
| 테스트 인프라 (TEST) | **병행** — 신규 기능마다 테스트 작성 |
| SSE 실시간 (SSE) | **활용** — GAP-2(SLA 알림)에서 SSE 활용 |
| 이메일 발송 (MAIL) | **확장** — GAP-7(CSAT 링크 포함), GAP-8(인바운드) |
| 대시보드 (DASH) | **확장** — GAP-9(AI 성능), GAP-2(SLA 준수율) |
| 사용자 관리 (USER) | **확장** — GAP-5(담당자 배정), GAP-2(에스컬레이션) |
| 알림 센터 (NOTI) | **확장** — GAP-2(SLA 경고), GAP-5(@멘션) |
| Redis 캐싱 (CACHE) | **활용** — GAP-3(유사 검색 캐싱) |
| DDD 리팩토링 | **후행** — 신규 도메인 모듈(Customer, Product, Template)은 처음부터 DDD로 설계 |

---

## 6. 기술 스택 추가

| 영역 | 추가 기술 | 용도 |
|------|----------|------|
| Backend | pgvector (PostgreSQL extension) | 유사 문의 벡터 검색 |
| Backend | Google Cloud Vision SDK 또는 AWS Textract SDK | 실제 OCR |
| Backend | JavaMail (IMAP receiver) | 이메일 인바운드 |
| Frontend | recharts (기 도입) | AI 성능 차트 |
| Frontend | @tanstack/react-query (기 도입) | 신규 API 쿼리 |

---

## 7. 리스크 & 완화

| # | 리스크 | 영향 | 확률 | 완화 전략 |
|---|--------|------|------|----------|
| R1 | PRD v3와 v4 동시 진행 시 리소스 분산 | HIGH | HIGH | Phase별 우선순위로 직렬화, 병행은 독립 영역만 |
| R2 | pgvector 도입 시 PostgreSQL 확장 호환성 | MEDIUM | LOW | RDS 지원 확인 완료 (PostgreSQL 16 기본 제공) |
| R3 | OCR 비용 증가 (대량 스캔 PDF) | MEDIUM | MEDIUM | 페이지 수 제한 + 비용 모니터링 + 캐싱 |
| R4 | 고객 데이터 GDPR/개인정보보호법 준수 | HIGH | MEDIUM | 개인정보 암호화, 보존 기간 설정, 삭제 API |
| R5 | 유사 문의 추천 정확도 | MEDIUM | MEDIUM | 유사도 임계값 조정, 사용자 피드백으로 개선 |
| R6 | SLA 스케줄러 성능 (문의 수 증가 시) | MEDIUM | LOW | 배치 처리 + 인덱스 최적화 |

---

## 8. 성공 지표 (KPI)

| 지표 | 현재 | Phase A 목표 | Phase D 목표 |
|------|------|-------------|-------------|
| 고객 정보 관리율 | 0% | 100% (모든 문의에 고객 연결) | 100% |
| SLA 준수율 | 측정 불가 | 85%+ | 95%+ |
| 유사 문의 활용률 | 0% | 30%+ (추천 결과 클릭) | 50%+ |
| 답변 템플릿 사용률 | 0% | 20%+ | 40%+ |
| OCR 처리 가능 문서 비율 | 0% (이미지 PDF 미지원) | 100% | 100% |
| 평균 답변 작성 시간 | 측정 없음 | -30% (베이스라인 대비) | -50% |
| CSAT 만족 비율 | 측정 불가 | 80%+ | 90%+ |
| AI 답변 수정률 | 측정 없음 | 측정 시작 | < 20% |

---

## 9. 부록: 전체 Gap 발견 방법론

### 9.1 분석 방법

1. **코드베이스 정밀 분석**: 26개 Flyway 마이그레이션, 전체 Controller/Service 계층, 프론트엔드 컴포넌트 전수 검사
2. **벤치마킹**: Zendesk, Freshdesk, ServiceNow, Intercom, Salesforce, Glean, Guru, Forethought, Coveo, Ada 총 10개 플랫폼 기능 비교
3. **도메인 분석**: Bio-Rad B2B 기술 지원 업무 특성 (과학 장비, 시약, 프로토콜) 반영
4. **기존 PRD 교차 검증**: PRD v2, v3, Quantum Jump에서 이미 다룬 영역 제외

### 9.2 기존 PRD에서 이미 커버된 영역 (본 PRD 범위 외)

| 영역 | 커버한 PRD | 상태 |
|------|-----------|------|
| 인증/인가 | PRD v3 Phase 1 | 계획됨 |
| 시크릿 관리 | PRD v3 Phase 1 | 계획됨 |
| 에러 처리 | PRD v3 Phase 1 | 계획됨 |
| 테스트 인프라 | PRD v3 Phase 1 | 계획됨 |
| SSE 실시간 | PRD v3 Phase 2 | 계획됨 |
| React Query | PRD v3 Phase 2 | 계획됨 |
| 모니터링 | PRD v3 Phase 3 | 계획됨 |
| Redis 캐싱 | PRD v3 Phase 4 | 계획됨 |
| DDD 리팩토링 | PRD v3 Phase 4 | 계획됨 |
| 질문 분해 | Quantum Jump | 구현됨 |
| 제품 필터 검색 | Quantum Jump | 구현됨 |
| I Don't Know 경로 | Quantum Jump | 구현됨 |

---

## 10. 벤치마킹 기반 추가 고도화 권고 (Bio-Rad 특화)

아래 항목들은 12개 핵심 Gap 외에, 벤치마킹 과정에서 식별된 **Bio-Rad 도메인에 특화된 차별화 기회**입니다. 기존 GAP들이 해결된 후 장기적으로 추진을 권고합니다.

### 10.1 인용 정확성 검증 (NLI 기반 Hallucination 감지)

**벤치마크**: Coveo Citation Verification, FACTUM (arXiv 2601.05866)
**위험도**: CRITICAL (과학 장비 도메인)

2025년 연구(FACTUM, ReDeEP)에 따르면 법률/의학 도메인 RAG 시스템의 **17~33%가 인용을 잘못 생성**합니다. Bio-Rad 맥락에서 "CFX96의 PCR 최대 온도는 95°C입니다"라고 답변했는데 매뉴얼에는 "최대 98°C"로 기재된 경우, 과학적 정확성 측면에서 치명적입니다.

**현재 상태**: `SelfReviewStep.java`가 형식 검증 중심이며, 생성된 답변이 실제 인용 문서 내용과 일치하는지 NLI(Natural Language Inference) 검증 없음

**권고사항**:
- SelfReview에 NLI 기반 fact-checking 레이어 추가
- 특히 **수치(온도, 농도, 볼륨, 시간)의 정확성** 우선 검증
- 인용 문서의 원문과 답변의 주장을 대조하는 `CitationVerifier` 서비스 신설

### 10.2 소스 권위성 가중치 (Source Authority Weighting)

**벤치마크**: Glean Relevance-Augmented Retrieval

현재 `AnalysisService.buildVerdict()`는 벡터 유사도 평균값만으로 SUPPORTED/CONDITIONAL/REFUTED를 결정합니다. 공식 제품 매뉴얼과 내부 메모가 동일 가중치로 취급됩니다.

**권고 가중치 체계**:

| 소스 유형 | 가중치 | 근거 |
|----------|--------|------|
| KB 공식 매뉴얼 | ×1.3 | Bio-Rad 공인 문서 |
| KB 프로토콜 | ×1.1 | 검증된 실험 절차 |
| KB FAQ | ×1.0 | 기존 승인 답변 |
| KB 스펙시트 | ×0.9 | 참조 자료 |
| 문의 첨부 문서 | ×0.8 | 고객 제공, 미검증 |

### 10.3 KB 갭 자동 감지 (Knowledge Gap Detection)

**벤치마크**: Guru (Knowledge Verification), Glean (Content Gap Analysis)

`CONDITIONAL` 또는 `INSUFFICIENT_EVIDENCE` 판정이 반복되는 질문 패턴을 추적하여 KB 관리자에게 콘텐츠 추가를 제안합니다.

**예시**:
> "ddPCR sensitivity 관련 질문이 최근 30일간 5회 KB 미적중 → KB 관리자에게 '디지털 PCR 민감도 가이드' 문서 추가 권고"

### 10.4 Bio-Rad 특화 차별화 기회

| # | 기능 | 설명 | 경쟁사에 없는 이유 |
|---|------|------|-----------------|
| 1 | **제품 계보 기반 검색 확장** | QX200 질문 시 QX ONE 문서도 함께 검색 (Bio-Rad 제품 간 기술적 연속성 활용) | Bio-Rad 도메인 지식 필요 |
| 2 | **시약 로트 번호 연동** | 특정 로트 번호 문의 시 해당 CoA(Certificate of Analysis) 자동 연결 | 과학 장비 CS 특화 |
| 3 | **다국어 과학 용어 정규화** | "피씨알" → "PCR", "디디피씨알" → "ddPCR" 자동 확장 검색 | 한국 CS 맥락 특화 |
| 4 | **규제 문서 우선 인용** | FDA 허가, CE 마킹 관련 문서에 높은 가중치 적용 | 의료기기 컴플라이언스 |
| 5 | **KB 문서 유효기간 관리** | 매뉴얼에 `valid_until` 설정, 만료 문서 검색 제외 + 검증 주기 리마인더 | 기기 펌웨어/시약 로트 업데이트 대응 |

### 10.5 케이스 연결 & 장비 프로파일

**벤치마크**: ServiceNow CMDB, Jira SM Related Issues

동일 기기/제품/증상에 대한 문의를 자동으로 연결하여 "알려진 이슈" 패턴을 형성합니다. ddPCR QX200에서 동일 오류 코드로 5개 CS 문의가 동시에 들어오면, 이를 "동일 이슈 군집"으로 처리하여 하나의 해결책을 모든 케이스에 적용합니다.

**추가 고려**:
- 고객사별 보유 장비 목록 + 펌웨어 버전 + 유지보수 계약 관리
- 문의 종료(`SENT`) 이후 케이스 결과 추적 (해결됨/미해결/RMA 진행)
- 현장 엔지니어 파견 에스컬레이션 워크플로우

---

> **다음 단계**: Stakeholder 리뷰 → 우선순위 확정 → PRD v3/v4 통합 로드맵 작성 → Sprint 18 실행 백로그
