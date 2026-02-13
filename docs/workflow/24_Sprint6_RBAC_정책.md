# 24. Sprint6 RBAC 정책 (초안)

## 목적
- 답변 라이프사이클(review/approve/send)에 대해 역할 기반 접근 통제를 일관 적용한다.

## 인증/권한 헤더
- `X-User-Id` : 사용자 식별자 (필수)
- `X-User-Roles` : 역할 목록(csv, 대소문자 무관)
  - 예: `REVIEWER`, `APPROVER,SENDER`
- 레거시 호환: `X-Role` 단일 역할 헤더 허용(점진 제거 예정)

## 역할 정의
- `REVIEWER` : 초안 리뷰
- `APPROVER` : 리뷰 완료 초안 승인
- `SENDER` : 승인 완료 초안 발송
- `ADMIN` : review/approve/send 전체 허용

## 엔드포인트 권한 매핑
- `POST /answers/{answerId}/review` : `REVIEWER` or `ADMIN`
- `POST /answers/{answerId}/approve` : `APPROVER` or `ADMIN`
- `POST /answers/{answerId}/send` : `SENDER` or `ADMIN`

## 실패 규약 (403)
- 사용자 식별자 없음: `AUTH_USER_ID_REQUIRED`
- 역할 부족: `AUTH_ROLE_FORBIDDEN principal=<id> required=<roles>`

## 감사 추적
- review/approve/send의 actor는 요청 principal(`X-User-Id`)을 우선 사용한다.
- request body의 actor는 호환용 입력이며, principal 없을 때만 fallback으로 사용한다.
