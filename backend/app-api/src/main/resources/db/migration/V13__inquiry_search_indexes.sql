-- 문의 목록 조회 성능 최적화를 위한 인덱스 추가

-- 상태 필터 인덱스
CREATE INDEX IF NOT EXISTS idx_inquiries_status ON inquiries(status);

-- 채널 필터 인덱스
CREATE INDEX IF NOT EXISTS idx_inquiries_channel ON inquiries(customer_channel);

-- 생성일 정렬 인덱스 (createdAt 기준 정렬이 기본이므로)
CREATE INDEX IF NOT EXISTS idx_inquiries_created_at ON inquiries(created_at);
