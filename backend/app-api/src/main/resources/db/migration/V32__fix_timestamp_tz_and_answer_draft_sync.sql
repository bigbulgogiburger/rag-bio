-- V32: send_attempt_logs TIMESTAMP WITH TIME ZONE 보정 + answer_drafts V26 컬럼 JPA 동기화 확인
-- V12에서 누락된 WITH TIME ZONE 보정
ALTER TABLE send_attempt_logs ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE;

-- V26에서 추가된 컬럼이 존재하지만 JPA 엔티티에 매핑이 누락되었던 상태를 보완
-- (컬럼 자체는 V26에서 이미 생성됨, 이 마이그레이션은 타입 보정만 수행)
