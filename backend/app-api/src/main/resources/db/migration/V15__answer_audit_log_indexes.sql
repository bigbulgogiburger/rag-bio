-- Sprint6-A3: 감사로그 조회 성능 보강 (status/actor/date + sort 대응)
CREATE INDEX IF NOT EXISTS idx_answer_drafts_inquiry_status_created
    ON answer_drafts (inquiry_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_answer_drafts_inquiry_reviewed_created
    ON answer_drafts (inquiry_id, reviewed_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_answer_drafts_inquiry_approved_created
    ON answer_drafts (inquiry_id, approved_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_answer_drafts_inquiry_sent_created
    ON answer_drafts (inquiry_id, sent_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_answer_drafts_inquiry_updated
    ON answer_drafts (inquiry_id, updated_at DESC);
