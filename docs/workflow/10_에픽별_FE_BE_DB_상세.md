# 10. 에픽별 FE/BE/DB 상세 분해 (실행형)

## 사용 방법
- 각 에픽에서 이번 스프린트 범위만 선택
- Story 단위로 담당자/예상일정/리스크를 채운 뒤 실행

## 템플릿
- Story ID:
- 담당: PM / FE / BE / AI / QA / DevOps
- 예상: X일
- 선행조건:
- FE 작업:
- BE 작업:
- DB 작업:
- 테스트:
- 수용기준:
- 릴리즈 영향:

---

## A-1 저장소/개발표준
- 담당: FE리드, BE리드
- FE: lint/prettier/컴포넌트 규약
- BE: 패키지 아키 규칙 테스트
- DB: migration naming 규칙
- 테스트: 샘플 PR로 게이트 검증
- 수용기준: 규약 위반 PR 자동 실패

## B-1 문의/업로드
- 담당: BE, FE
- FE: 업로드/진행률/에러 UI
- BE: inquiry API + multipart
- DB: inquiry/document 테이블
- 테스트: 50MB, 100MB 파일 경계 테스트
- 수용기준: 실패율 1% 미만

## B-2 OCR/인덱싱
- 담당: BE, AI
- FE: 상태 배지/재처리 UX
- BE: OCR 분기 + 재시도 큐
- DB: indexing_job_event
- 테스트: 이미지형 PDF 20종
- 수용기준: OCR 성공률 목표치 합의

## C-1 검색/검증
- 담당: AI, BE
- FE: verdict/근거 UI
- BE: retrieval + verifier
- DB: evidence/result 테이블
- 테스트: 평가셋 정확도 측정
- 수용기준: 베이스라인 대비 개선

## C-2 답변 생성
- 담당: AI, FE
- FE: 답변/추가확인 UX
- BE: structured output + citation enforcement
- DB: composed_answer
- 테스트: 출처 누락 탐지 규칙
- 수용기준: 출처 포함률 95%+

## D-1 커뮤니케이션/승인
- 담당: FE, BE
- FE: Draft/Review/Approve 화면
- BE: 상태머신/권한체크/발송어댑터
- DB: approval/delivery_attempt
- 테스트: 승인 없이 발송 시도 차단
- 수용기준: 오발송 0건

## E-1 운영/보안
- 담당: DevOps, BE
- FE: 운영 대시보드
- BE: 로그/메트릭/알람
- DB: 백업/복구 시나리오
- 테스트: 장애훈련 게임데이
- 수용기준: MTTR 목표 달성
