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
// Simplified to 4 display states: 업로드됨 / 인덱싱 중 / 인덱싱 완료 / 인덱싱 실패
export const DOC_STATUS_LABELS: Record<string, string> = {
  UPLOADED: "업로드됨",
  PARSING: "인덱싱 중",
  PARSED: "인덱싱 중",
  PARSED_OCR: "인덱싱 중",
  CHUNKED: "인덱싱 중",
  INDEXING: "인덱싱 중",
  INDEXED: "인덱싱 완료",
  FAILED: "인덱싱 실패",
  FAILED_PARSING: "인덱싱 실패",
};

export type DocStatusBadgeVariant = "info" | "success" | "warn" | "danger" | "neutral";

export function docStatusBadgeVariant(status: string): DocStatusBadgeVariant {
  if (status === "UPLOADED") return "neutral";
  if (["PARSING", "PARSED", "PARSED_OCR", "CHUNKED", "INDEXING"].includes(status)) return "warn";
  if (status === "INDEXED") return "success";
  if (status === "FAILED" || status === "FAILED_PARSING") return "danger";
  return "neutral";
}

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
  SELF_REVIEW_INCOMPLETE: "자체 검증 미완료",
};

// ===== 톤 =====
export const TONE_LABELS: Record<string, string> = {
  professional: "정중체",
  technical: "기술 상세",
  brief: "요약",
  gilseon: "길선체",
};

// ===== 채널 =====
export const CHANNEL_LABELS: Record<string, string> = {
  email: "이메일",
  messenger: "메신저",
  portal: "포털",
};

// ===== 에러 코드 =====
export const ERROR_LABELS: Record<string, string> = {
  // 인증/권한
  AUTH_USER_ID_REQUIRED: "사용자 ID가 필요합니다",
  AUTH_ROLE_FORBIDDEN: "권한이 부족합니다",
  UNAUTHORIZED: "인증이 필요합니다. 다시 로그인해 주세요",
  FORBIDDEN: "접근 권한이 없습니다",
  TOKEN_EXPIRED: "인증이 만료되었습니다. 다시 로그인해 주세요",

  // 상태/충돌
  INVALID_STATE: "현재 상태에서는 수행할 수 없습니다",
  NOT_FOUND: "요청한 항목을 찾을 수 없습니다",
  CONFLICT: "이미 처리된 요청입니다",

  // 입력 검증
  VALIDATION_ERROR: "입력값이 올바르지 않습니다",
  FILE_TOO_LARGE: "파일 크기가 제한을 초과합니다",
  UNSUPPORTED_FILE_TYPE: "지원하지 않는 파일 형식입니다",
  MISSING_REQUIRED_FIELD: "필수 항목을 입력해 주세요",

  // 서버/인프라
  INTERNAL_SERVER_ERROR: "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요",
  SERVICE_UNAVAILABLE: "서비스가 일시적으로 사용할 수 없습니다",
  RATE_LIMIT_EXCEEDED: "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요",
  TIMEOUT: "요청 시간이 초과되었습니다. 다시 시도해 주세요",

  // 문서/인덱싱
  INDEXING_FAILED: "문서 인덱싱에 실패했습니다",
  PARSING_FAILED: "문서 파싱에 실패했습니다",
  NO_DOCUMENTS: "첨부된 문서가 없습니다",
  DOCUMENT_NOT_INDEXED: "문서가 아직 인덱싱되지 않았습니다",

  // 답변/워크플로우
  DRAFT_GENERATION_FAILED: "답변 초안 생성에 실패했습니다",
  REVIEW_FAILED: "답변 리뷰에 실패했습니다",
  SEND_FAILED: "답변 발송에 실패했습니다",
  DUPLICATE_SEND: "이미 발송된 답변입니다",

  // 네트워크
  NETWORK_ERROR: "네트워크 오류가 발생했습니다. 인터넷 연결을 확인해 주세요",
  UNKNOWN: "알 수 없는 오류가 발생했습니다",
};

// ===== Knowledge Base 카테고리 =====
export const KB_CATEGORY_LABELS: Record<string, string> = {
  MANUAL: "매뉴얼",
  PROTOCOL: "프로토콜",
  FAQ: "FAQ",
  SPEC_SHEET: "스펙시트",
};

// ===== 검색 관련 =====
export const SEARCH_LABELS: Record<string, string> = {
  TRANSLATED_QUERY: "번역된 질문",
  MATCH_SOURCE_VECTOR: "벡터 검색",
  MATCH_SOURCE_KEYWORD: "키워드 검색",
  MATCH_SOURCE_HYBRID: "벡터+키워드",
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
export function labelError(code: string): string {
  return label(ERROR_LABELS, code);
}

// ===== AI 리뷰 판정 =====
const REVIEW_DECISION_LABELS: Record<string, string> = {
  PASS: "통과",
  REVISE: "수정 필요",
  REJECT: "거부",
};

export function labelReviewDecision(d: string): string {
  return label(REVIEW_DECISION_LABELS, d);
}

// ===== AI 승인 판정 =====
const APPROVAL_DECISION_LABELS: Record<string, string> = {
  AUTO_APPROVED: "자동 승인",
  ESCALATED: "사람 확인 필요",
  REJECTED: "거부",
};

export function labelApprovalDecision(d: string): string {
  return label(APPROVAL_DECISION_LABELS, d);
}

// ===== 이슈 심각도 =====
const ISSUE_SEVERITY_LABELS: Record<string, string> = {
  CRITICAL: "치명적",
  HIGH: "높음",
  MEDIUM: "보통",
  LOW: "낮음",
  WARNING: "경고",
  INFO: "정보",
};

export function labelIssueSeverity(s: string): string {
  return label(ISSUE_SEVERITY_LABELS, s);
}

// ===== 이슈 카테고리 =====
const ISSUE_CATEGORY_LABELS: Record<string, string> = {
  ACCURACY: "정확성",
  COMPLETENESS: "완전성",
  TONE: "톤/형식",
  RISK: "리스크",
  FORMAT: "포맷",
  DUPLICATION: "중복",
  INCONSISTENCY: "일관성",
  INCOMPLETE_PROCEDURE: "절차 누락",
  CITATION_MISMATCH: "인용 불일치",
};

export function labelIssueCategory(c: string): string {
  return label(ISSUE_CATEGORY_LABELS, c);
}
