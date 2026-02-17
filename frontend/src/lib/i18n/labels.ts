// ===== 판정 (Verdict) =====
export const VERDICT_LABELS: Record<string, string> = {
  SUPPORTED: "근거 충분",
  REFUTED: "근거 부족",
  CONDITIONAL: "조건부",
};

// ===== 답변 상태 (Answer Status) =====
export const ANSWER_STATUS_LABELS: Record<string, string> = {
  DRAFT: "초안",
  REVIEWED: "검토 완료",
  APPROVED: "승인 완료",
  SENT: "발송 완료",
};

// ===== 문서 처리 상태 (Document Status) =====
export const DOC_STATUS_LABELS: Record<string, string> = {
  UPLOADED: "업로드됨",
  INDEXING: "인덱싱 중",
  INDEXED: "인덱싱 완료",
  FAILED: "인덱싱 실패",
};

// ===== 문의 상태 (Inquiry Status) =====
export const INQUIRY_STATUS_LABELS: Record<string, string> = {
  RECEIVED: "접수됨",
  ANALYZED: "분석 완료",
  ANSWERED: "답변 생성됨",
  CLOSED: "종료",
};

// ===== 리스크 플래그 =====
export const RISK_FLAG_LABELS: Record<string, string> = {
  LOW_CONFIDENCE: "신뢰도 낮음",
  WEAK_EVIDENCE_MATCH: "근거 약함",
  CONFLICTING_EVIDENCE: "근거 상충",
  INSUFFICIENT_EVIDENCE: "근거 부족",
  FALLBACK_DRAFT_USED: "대체 초안 사용됨",
  ORCHESTRATION_FALLBACK: "처리 중 오류 발생",
};

// ===== 톤 =====
export const TONE_LABELS: Record<string, string> = {
  professional: "정중체",
  technical: "기술 상세",
  brief: "요약",
};

// ===== 채널 =====
export const CHANNEL_LABELS: Record<string, string> = {
  email: "이메일",
  messenger: "메신저",
  portal: "포털",
};

// ===== 에러 코드 =====
export const ERROR_LABELS: Record<string, string> = {
  AUTH_USER_ID_REQUIRED: "사용자 ID가 필요합니다",
  AUTH_ROLE_FORBIDDEN: "권한이 부족합니다",
  INVALID_STATE: "현재 상태에서는 수행할 수 없습니다",
  NOT_FOUND: "요청한 항목을 찾을 수 없습니다",
  CONFLICT: "이미 처리된 요청입니다",
};

// ===== Knowledge Base 카테고리 =====
export const KB_CATEGORY_LABELS: Record<string, string> = {
  MANUAL: "매뉴얼",
  PROTOCOL: "프로토콜",
  FAQ: "FAQ",
  SPEC_SHEET: "스펙시트",
};

// ===== 공통 변환 함수 =====
export function label(map: Record<string, string>, key: string): string {
  return map[key] ?? key; // 매핑 없으면 원문 그대로 표시 (안전 장치)
}

export function labelVerdict(v: string): string {
  return label(VERDICT_LABELS, v);
}
export function labelAnswerStatus(s: string): string {
  return label(ANSWER_STATUS_LABELS, s);
}
export function labelDocStatus(s: string): string {
  return label(DOC_STATUS_LABELS, s);
}
export function labelInquiryStatus(s: string): string {
  return label(INQUIRY_STATUS_LABELS, s);
}
export function labelRiskFlag(f: string): string {
  return label(RISK_FLAG_LABELS, f);
}
export function labelTone(t: string): string {
  return label(TONE_LABELS, t);
}
export function labelChannel(c: string): string {
  return label(CHANNEL_LABELS, c);
}
export function labelKbCategory(c: string): string {
  return label(KB_CATEGORY_LABELS, c);
}
