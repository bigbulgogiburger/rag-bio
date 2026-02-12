# 14. Sprint2 마무리 리포트

## 완료 범위
- S2-01 문서 파싱 파이프라인 (기본)
- S2-02 OCR 분기 처리 (mock OCR)
- S2-03 청킹(1000/overlap 150) + 저장
- S2-04 임베딩/벡터 저장 (mock embedding + mock vector store)
- S2-05 인덱싱 상태 조회 API/UI
- S2-06 실패건 재처리 옵션(`failedOnly=true`)
- S2-07 인덱싱 로그/메트릭성 이벤트(구조화 로그)

## 현재 상태 머신
- UPLOADED
- PARSING
- PARSED / PARSED_OCR
- CHUNKED
- INDEXED
- FAILED_PARSING

## API
- `GET /api/v1/inquiries/{inquiryId}/documents/indexing-status`
- `POST /api/v1/inquiries/{inquiryId}/documents/indexing/run?failedOnly={bool}`

## 확인 결과
- Backend: `./gradlew :app-api:test build` 성공
- Frontend: `npm run build` 성공

## 알려진 제한사항
- OCR/Embedding/Vector는 비용 없는 mock 구현
- 실제 OpenAI Vector Store 연동은 Sprint3/운영단계에서 adapter 교체 예정
