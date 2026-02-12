# PRD — Bio-Rad CS RAG 기반 멀티 에이전트 컨설팅 어플리케이션

- 작성일: 2026-02-12
- 작성자: Product (Big Tech 스타일 기준)
- 문서 버전: v0.1 (Draft)

---

## 1. 제품 개요

### 1.1 제품명 (가칭)
**Bio-Rad CS Copilot**

### 1.2 한 줄 정의
Bio-Rad CS 조직이 연구자(고객)의 기술 질문을 빠르고 정확하게 검증하고, 근거 기반 답변/가이드/커뮤니케이션(이메일·메시지)까지 자동 생성할 수 있도록 돕는 **RAG 기반 멀티 에이전트 서비스**.

### 1.3 해결하려는 문제
- 연구자 질문이 고도화/전문화되어 CS 응답 품질 편차가 큼
- PDF/Word 기반 레퍼런스 문서가 많아 수작업 검색 시간이 길고 누락 위험이 큼
- 기술 검증(맞다/틀리다/조건부)과 외부 커뮤니케이션 문안 작성까지 담당자에게 과부하

### 1.4 기대 효과
- FRT(First Response Time) 단축
- 기술 검증 정확도 향상
- 답변 일관성/근거 투명성 강화
- 최종 커뮤니케이션(이메일/메시지) 품질 표준화

---

## 2. 목표 및 비목표

### 2.1 목표 (Goals)
1. 질문 접수 후 **근거 기반 정답/오답/조건부 판정** 제공
2. 판정 근거(출처 문서, chunk, 요약 근거) 제시
3. 멀티 에이전트 협업으로 답변·이메일·고객 메시지까지 원클릭 생성
4. 문서 업로드 파이프라인(PDF/Word + OCR) 안정화

### 2.2 비목표 (Non-Goals)
- 연구 실험 자동 실행/장비 제어
- 법률/규제 최종 판정 자동화(최종 승인자는 사람)
- 범용 고객센터 전체 도메인 커버리지(초기에는 Bio-Rad CS 기술 질의 중심)

---

## 3. 사용자 및 핵심 시나리오

### 3.1 주요 사용자
- **1차 사용자:** Bio-Rad CS 담당자 (내부 사용자)
- **2차 간접 사용자:** 연구자/고객(외부 수신자)

### 3.2 핵심 시나리오
1. CS 담당자가 고객 질문과 첨부 문서(PDF/Word)를 업로드
2. 시스템이 문서를 파싱/OCR/청킹/임베딩/인덱싱
3. 멀티 에이전트가 검색→검증→답변 생성→커뮤니케이션 초안 작성
4. CS 담당자가 리뷰 후 이메일/고객 메시지 발송

---

## 4. 기능 요구사항 (Functional Requirements)

## 4.1 입력/문서 처리
- 지원 포맷:
  - PDF (텍스트 추출 가능)
  - PDF (텍스트 추출 불가: OCR 필요)
  - Word (.doc/.docx)
- OCR:
  - 스캔본/이미지형 PDF에 대해 OCR 파이프라인 수행
  - OCR 결과의 신뢰도(confidence) 메타데이터 저장
- 청킹:
  - 기본 chunk size: **1000 토큰/문자 기준(시스템 표준에 맞게 토큰 우선)**
  - overlap: 표준 RAG 설정 적용 (초기 10~20% 권장)
- 임베딩/벡터 저장:
  - 임베딩 모델: OpenAI
  - Vector Store: OpenAI Vector Store
- 메타데이터:
  - 문서명, 버전, 업로드 시각, 제품군, 실험 조건, 언어, OCR 여부, chunk 인덱스

## 4.2 RAG 질의 응답
- 사용자 질문에 대해 관련 chunk 검색
- 검색 결과 기반으로 LLM(ChatGPT)이 답변 생성
- 출력 형태:
  1) 판정: 맞음/틀림/조건부
  2) 근거 요약
  3) 출처(문서/페이지/chunk)
  4) 추가 확인 필요사항
- 표준 RAG 설정 준수:
  - Hybrid/semantic retrieval 기본
  - 재정렬(rerank) 표준 옵션 적용 가능
  - Top-k, threshold는 운영 중 튜닝

## 4.3 멀티 에이전트 구성
1. **자료 찾는 에이전트 (Retriever Agent)**
   - 질문 의도 분석
   - 관련 문서/청크 검색, 증거 후보 수집
2. **기술 검증 에이전트 (Technical Verifier Agent)**
   - 사실성/일관성/조건 검토
   - 맞음/틀림/조건부 판단 및 리스크 플래그
3. **올바른 답변 작성 에이전트 (Answer Composer Agent)**
   - 사용자 친화적 최종 답변 작성
   - 톤/명확성/실행 가능한 가이드 포함
4. **이메일 작성·발송 에이전트 (Email Agent)**
   - 내부 승인 후 고객 발송용 이메일 초안 작성
   - 템플릿/서명/면책 문구 반영
5. **고객 메시지 작성 에이전트 (Customer Message Agent)**
   - 채널별(짧은 메시지/메신저) 요약 문안 생성

## 4.4 Human-in-the-loop
- 외부 발송(이메일/고객 메시지)은 기본적으로 **사람 승인 후 발송**
- 고위험 답변(낮은 근거 점수, 상충 근거)에는 경고 배지 표시

## 4.5 감사/추적
- 질문, 검색 결과, 에이전트별 중간 산출물, 최종 답변 이력 저장
- “왜 이 답변이 나왔는지” 추적 가능한 audit trail 제공

---

## 5. 비기능 요구사항 (NFR)

### 5.1 성능
- 일반 질의(문서 인덱싱 완료 기준) 응답: P95 8초 이내 목표
- 대용량 문서 인덱싱: 비동기 처리 + 진행률 표시

### 5.2 정확성/품질
- 근거 없는 단정 금지
- 출처 없는 문장 최소화
- 도메인 검증 실패 시 “추가 확인 필요”로 안전 출력

### 5.3 보안/컴플라이언스
- 전송·저장 암호화
- 사용자 권한 기반 접근 제어(RBAC)
- 민감정보 마스킹/로그 최소수집
- 감사 로그 보관 정책 수립

### 5.4 가용성
- 장애 시 재시도/폴백(검색 실패, OCR 실패, LLM 타임아웃)
- 운영 대시보드/알림 제공

---

## 6. 시스템 아키텍처 요구사항

### 6.1 기술 스택
- Frontend: **React + Next.js**
- Backend: **Spring Boot 3.3.8**
- Persistence: **JPA + QueryDSL**
- Architecture: **DDD (Domain-Driven Design)**
- Development Method: **TDD**
- LLM: **ChatGPT (OpenAI)**
- Vector Store: **OpenAI Vector Store**

### 6.2 DDD 권장 바운디드 컨텍스트
- Ingestion Context (문서 수집/OCR/청킹)
- Knowledge Retrieval Context (검색/재정렬)
- Verification Context (기술 검증/판정)
- Response Composition Context (답변/문안 생성)
- Communication Context (이메일/메시지 발송)
- Audit Context (로그/추적)

### 6.3 백엔드 레이어
- Domain (Entity/Aggregate/VO/Domain Service)
- Application (UseCase/Facade/Orchestration)
- Infrastructure (JPA Repos, OpenAI SDK, OCR Adapter, Mail Adapter)
- Interface (REST API, Admin API)

### 6.4 TDD 원칙
- UseCase 단위 테스트 우선
- Domain 규칙 테스트 필수
- 외부 의존(OpenAI/OCR/메일)은 테스트 더블로 격리
- 통합 테스트: 주요 시나리오(업로드→검색→검증→응답) 최소 1개 이상/릴리즈

---

## 7. 주요 사용자 흐름

1) 질문 접수
- CS가 질문 텍스트 + 문서 업로드

2) 인덱싱
- 파서/OCR → 청킹(1000) → 임베딩 → 벡터 저장

3) 멀티 에이전트 실행
- Retriever → Verifier → Answer Composer

4) 결과 확인
- 판정 + 근거 + 출처 + 리스크 확인

5) 커뮤니케이션 생성
- Email Agent / Customer Message Agent 초안 생성

6) 사람 승인 후 발송

---

## 8. API/도메인 초안 (요약)

### 8.1 핵심 엔티티
- Inquiry
- Document
- Chunk
- RetrievalEvidence
- VerificationResult
- ComposedAnswer
- OutboundMessage (Email/Customer)
- AuditLog

### 8.2 핵심 유스케이스
- UploadDocumentUseCase
- IndexDocumentUseCase
- AskQuestionUseCase
- RunVerificationUseCase
- ComposeAnswerUseCase
- DraftEmailUseCase
- DraftCustomerMessageUseCase
- ApproveAndSendUseCase

---

## 9. 성공 지표 (KPI)

- 응답시간: P95
- 판정 정확도(샘플 평가셋)
- 출처 포함률
- CS 만족도(내부)
- 재작성률(초안 수정 비율)
- 고객 회신 소요시간 단축률

---

## 10. 릴리즈 계획 (MVP → 확장)

### MVP (Phase 1)
- PDF/Word 업로드 + OCR
- 기본 RAG + 3개 핵심 에이전트(Retriever/Verifier/Answer)
- 판정 + 출처 표시
- 이메일/고객 메시지 초안 생성(발송 전 승인)

### Phase 2
- 고급 재정렬 및 품질 튜닝
- 템플릿/브랜드 톤 고도화
- 품질 대시보드/KPI 자동 리포팅

### Phase 3
- 다국어 지원
- 도메인별 지식 베이스 분리 운영
- 고급 정책 엔진(규정/금지어/법무 검토 플로우)

---

## 11. 리스크 및 대응

- OCR 품질 불안정 → 품질 점수 기반 재처리/수동검수 큐
- 환각(Hallucination) → 근거 강제, 낮은 신뢰도 시 보수 응답
- 문서 버전 충돌 → 버전 메타데이터 및 최신 우선 정책
- 운영 복잡도 증가(멀티 에이전트) → 오케스트레이터 + 표준 로그 스키마

---

## 12. 오픈 이슈 (결정 필요)

1. OCR 엔진 선택 (클라우드/온프레미스)
2. “맞음/틀림/조건부”의 판정 기준(정량 규칙) 상세화
3. 외부 이메일 실제 자동 발송 범위(완전 자동 vs 승인 필수)
4. 데이터 보존 기간 및 삭제 정책
5. 초기 도메인 범위(제품군/언어/지역)

---

## 13. 개발 시작 체크리스트

- [ ] DDD 컨텍스트 경계 확정
- [ ] API 계약(OpenAPI) 초안
- [ ] 테스트 전략(Unit/Integration/E2E) 문서화
- [ ] OpenAI 키/권한/비용 한도 설정
- [ ] OCR 파이프라인 PoC
- [ ] 샘플 평가셋 구축(정확도/KPI 측정용)

---

## 14. 한 줄 요약

이 제품은 Bio-Rad CS가 연구자 질문에 대해 **근거 기반 기술 검증 + 고품질 커뮤니케이션**을 빠르게 수행하게 만드는, **RAG + 멀티 에이전트 기반 업무 코파일럿**이다.
