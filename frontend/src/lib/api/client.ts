export interface CreateInquiryPayload {
  question: string;
  customerChannel?: string;
  preferredTone?: string;
}

export interface AskQuestionResult {
  inquiryId: string;
  status: string;
  message: string;
}

export interface OpsFailureReason {
  reason: string;
  count: number;
}

export interface OpsMetrics {
  approvedOrSentCount: number;
  sentCount: number;
  sendSuccessRate: number;
  duplicateBlockedCount: number;
  totalSendAttemptCount: number;
  duplicateBlockRate: number;
  fallbackDraftCount: number;
  totalDraftCount: number;
  fallbackDraftRate: number;
  topFailureReasons: OpsFailureReason[];
}

export interface InquiryDetail {
  inquiryId: string;
  question: string;
  customerChannel: string;
  preferredTone?: string;
  status: string;
  createdAt: string;
}

export interface DocumentUploadResult {
  documentId: string;
  inquiryId: string;
  fileName: string;
  status: string;
}

export interface DocumentStatus {
  documentId: string;
  inquiryId: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  status: string;
  createdAt: string;
  updatedAt: string;
  lastError: string | null;
  ocrConfidence: number | null;
  chunkCount: number | null;
  vectorCount: number | null;
}

export interface InquiryIndexingStatus {
  inquiryId: string;
  total: number;
  uploaded: number;
  parsing: number;
  parsed: number;
  chunked: number;
  indexed: number;
  failed: number;
  documents: DocumentStatus[];
}

export interface IndexingRunResult {
  inquiryId: string;
  processed: number;
  succeeded: number;
  failed: number;
}

export interface AnalyzeEvidenceItem {
  chunkId: string;
  documentId: string;
  score: number;
  excerpt: string;
  sourceType?: "INQUIRY" | "KNOWLEDGE_BASE";
  fileName?: string;
  pageStart?: number;
  pageEnd?: number;
}

export interface AnalyzeResult {
  inquiryId: string;
  verdict: "SUPPORTED" | "REFUTED" | "CONDITIONAL";
  confidence: number;
  reason: string;
  riskFlags: string[];
  evidences: AnalyzeEvidenceItem[];
  translatedQuery?: string;
}

export type AnswerTone = "professional" | "technical" | "brief" | "gilseon";
export type AnswerChannel = "email" | "messenger";

export interface AnswerDraftResult {
  answerId: string;
  inquiryId: string;
  version: number;
  status: "DRAFT" | "REVIEWED" | "APPROVED" | "SENT";
  verdict: "SUPPORTED" | "REFUTED" | "CONDITIONAL";
  confidence: number;
  draft: string;
  citations: string[];
  riskFlags: string[];
  tone: AnswerTone;
  channel: AnswerChannel;
  reviewedBy: string | null;
  reviewComment: string | null;
  approvedBy: string | null;
  approveComment: string | null;
  sentBy: string | null;
  sendChannel: string | null;
  sendMessageId: string | null;
  formatWarnings: string[];
  evidences?: AnalyzeEvidenceItem[];
  translatedQuery?: string;
  reviewScore?: number;
  reviewDecision?: string;
  approvalDecision?: string;
  approvalReason?: string;
}

// ===== AI Review / Approval =====

export interface ReviewIssue {
  category: "ACCURACY" | "COMPLETENESS" | "TONE" | "RISK" | "FORMAT";
  severity: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
  description: string;
  suggestion: string;
}

export interface AiReviewResult {
  decision: "PASS" | "REVISE" | "REJECT";
  score: number;
  issues: ReviewIssue[];
  revisedDraft: string | null;
  summary: string;
  status: string;
  reviewedBy: string;
}

export interface GateResult {
  gate: string;
  passed: boolean;
  actualValue: string;
  threshold: string;
}

export interface AiApprovalResult {
  decision: "AUTO_APPROVED" | "ESCALATED" | "REJECTED";
  reason: string;
  gateResults: GateResult[];
  status: string;
  approvedBy: string;
}

export interface AutoWorkflowResult {
  review: AiReviewResult;
  approval: AiApprovalResult;
  finalStatus: string;
  requiresHumanAction: boolean;
  summary: string;
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081";

// ===== Auth Types =====

export interface AuthUser {
  id: string;
  username: string;
  displayName: string | null;
  email: string | null;
  roles: string[];
}

export interface LoginPayload {
  username: string;
  password: string;
}

export interface LoginResult {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUser;
}

export interface TokenRefreshResult {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

// ===== Token Storage =====

const TOKEN_KEY = "cs_access_token";
const REFRESH_KEY = "cs_refresh_token";

export function getStoredAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(REFRESH_KEY);
}

export function storeTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_KEY, refreshToken);
}

export function clearTokens(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

// ===== Auth API =====

export async function login(payload: LoginPayload): Promise<LoginResult> {
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("아이디 또는 비밀번호가 올바르지 않습니다.");
    }
    throw new Error(`로그인 실패: ${response.status}`);
  }

  const data = (await response.json()) as LoginResult;
  storeTokens(data.accessToken, data.refreshToken);
  return data;
}

export async function refreshAccessToken(): Promise<TokenRefreshResult> {
  const refreshToken = getStoredRefreshToken();
  if (!refreshToken) {
    throw new Error("No refresh token available");
  }

  const response = await fetch(`${API_BASE_URL}/api/v1/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });

  if (!response.ok) {
    clearTokens();
    throw new Error("토큰 갱신 실패");
  }

  const data = (await response.json()) as TokenRefreshResult;
  localStorage.setItem(TOKEN_KEY, data.accessToken);
  return data;
}

export async function fetchMe(): Promise<AuthUser> {
  const token = getStoredAccessToken();
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/me`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });

  if (!response.ok) {
    throw new Error("인증 정보 조회 실패");
  }

  return (await response.json()) as AuthUser;
}

// ===== Authenticated Fetch =====

let refreshPromise: Promise<TokenRefreshResult> | null = null;

export async function authFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const token = getStoredAccessToken();
  const headers = new Headers(init?.headers);
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let response = await fetch(input, { ...init, headers });

  if (response.status === 401 && getStoredRefreshToken()) {
    // Deduplicate concurrent refresh calls
    if (!refreshPromise) {
      refreshPromise = refreshAccessToken().finally(() => {
        refreshPromise = null;
      });
    }

    try {
      await refreshPromise;
    } catch {
      return response;
    }

    const newToken = getStoredAccessToken();
    if (newToken) {
      headers.set("Authorization", `Bearer ${newToken}`);
      response = await fetch(input, { ...init, headers });
    }
  }

  return response;
}

export async function createInquiry(payload: CreateInquiryPayload): Promise<AskQuestionResult> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    throw new Error(`Failed to create inquiry: ${response.status}`);
  }

  return (await response.json()) as AskQuestionResult;
}

export async function uploadInquiryDocument(inquiryId: string, file: File): Promise<DocumentUploadResult> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/documents`, {
    method: "POST",
    body: formData
  });

  if (!response.ok) {
    throw new Error(`Failed to upload document: ${response.status}`);
  }

  return (await response.json()) as DocumentUploadResult;
}

export async function getInquiry(inquiryId: string): Promise<InquiryDetail> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}`);

  if (!response.ok) {
    throw new Error(`Failed to fetch inquiry: ${response.status}`);
  }

  return (await response.json()) as InquiryDetail;
}

export async function updateInquiry(
  inquiryId: string,
  question: string
): Promise<InquiryDetail> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question }),
  });

  if (!response.ok) {
    throw new Error(`Failed to update inquiry: ${response.status}`);
  }

  return (await response.json()) as InquiryDetail;
}

export async function listInquiryDocuments(inquiryId: string): Promise<DocumentStatus[]> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/documents`);

  if (!response.ok) {
    throw new Error(`Failed to fetch documents: ${response.status}`);
  }

  return (await response.json()) as DocumentStatus[];
}

export async function getInquiryIndexingStatus(inquiryId: string): Promise<InquiryIndexingStatus> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/documents/indexing-status`);

  if (!response.ok) {
    throw new Error(`Failed to fetch indexing status: ${response.status}`);
  }

  return (await response.json()) as InquiryIndexingStatus;
}

export async function runInquiryIndexing(inquiryId: string, failedOnly = false): Promise<IndexingRunResult> {
  const response = await authFetch(
    `${API_BASE_URL}/api/v1/inquiries/${inquiryId}/documents/indexing/run?failedOnly=${failedOnly}`,
    {
      method: "POST"
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to run indexing: ${response.status}`);
  }

  return (await response.json()) as IndexingRunResult;
}

export async function analyzeInquiry(
  inquiryId: string,
  question: string,
  topK = 5
): Promise<AnalyzeResult> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/analysis`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question, topK })
  });

  if (!response.ok) {
    throw new Error(`Failed to analyze inquiry: ${response.status}`);
  }

  return (await response.json()) as AnalyzeResult;
}

export async function draftInquiryAnswer(
  inquiryId: string,
  question: string,
  tone: AnswerTone = "professional",
  channel: AnswerChannel = "email"
): Promise<AnswerDraftResult> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/draft`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question, tone, channel })
  });

  if (!response.ok) {
    throw new Error(`Failed to draft answer: ${response.status}`);
  }

  return (await response.json()) as AnswerDraftResult;
}

export async function getLatestAnswerDraft(inquiryId: string): Promise<AnswerDraftResult> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/latest`);
  if (!response.ok) {
    throw new Error(`Failed to fetch latest draft: ${response.status}`);
  }
  return (await response.json()) as AnswerDraftResult;
}

export async function listAnswerDraftHistory(inquiryId: string): Promise<AnswerDraftResult[]> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/history`);
  if (!response.ok) {
    throw new Error(`Failed to fetch answer history: ${response.status}`);
  }
  return (await response.json()) as AnswerDraftResult[];
}

export async function reviewAnswerDraft(
  inquiryId: string,
  answerId: string,
  actor?: string,
  comment?: string
): Promise<AnswerDraftResult> {
  const principal = actor?.trim() || "reviewer-user";
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/review`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-User-Id": principal, "X-User-Roles": "REVIEWER" },
    body: JSON.stringify({ actor: principal, comment })
  });
  if (!response.ok) {
    throw new Error(`Failed to review draft: ${response.status}`);
  }
  return (await response.json()) as AnswerDraftResult;
}

export async function approveAnswerDraft(
  inquiryId: string,
  answerId: string,
  actor?: string,
  comment?: string
): Promise<AnswerDraftResult> {
  const principal = actor?.trim() || "approver-user";
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/approve`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-User-Id": principal, "X-User-Roles": "APPROVER" },
    body: JSON.stringify({ actor: principal, comment })
  });
  if (!response.ok) {
    throw new Error(`Failed to approve draft: ${response.status}`);
  }
  return (await response.json()) as AnswerDraftResult;
}

export async function sendAnswerDraft(
  inquiryId: string,
  answerId: string,
  actor?: string,
  channel?: AnswerChannel,
  sendRequestId?: string
): Promise<AnswerDraftResult> {
  const principal = actor?.trim() || "sender-user";
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/send`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-User-Id": principal, "X-User-Roles": "SENDER" },
    body: JSON.stringify({ actor: principal, channel, sendRequestId })
  });
  if (!response.ok) {
    throw new Error(`Failed to send draft: ${response.status}`);
  }
  return (await response.json()) as AnswerDraftResult;
}

export async function aiReviewAnswer(inquiryId: string, answerId: string): Promise<AiReviewResult> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/ai-review`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
  });
  if (!response.ok) {
    throw new Error(`AI 리뷰 실패: ${response.status}`);
  }
  return (await response.json()) as AiReviewResult;
}

export async function aiApproveAnswer(inquiryId: string, answerId: string): Promise<AiApprovalResult> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/ai-approve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
  });
  if (!response.ok) {
    throw new Error(`AI 승인 실패: ${response.status}`);
  }
  return (await response.json()) as AiApprovalResult;
}

export async function autoWorkflow(inquiryId: string, answerId: string): Promise<AutoWorkflowResult> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/auto-workflow`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
  });
  if (!response.ok) {
    throw new Error(`자동 워크플로우 실패: ${response.status}`);
  }
  return (await response.json()) as AutoWorkflowResult;
}

export async function updateAnswerDraft(
  inquiryId: string,
  answerId: string,
  draft: string
): Promise<AnswerDraftResult> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/edit-draft`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ draft })
  });
  if (!response.ok) {
    throw new Error(`Failed to update draft: ${response.status}`);
  }
  return (await response.json()) as AnswerDraftResult;
}

export async function getOpsMetrics(): Promise<OpsMetrics> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/ops/metrics`);
  if (!response.ok) {
    throw new Error(`Failed to fetch ops metrics: ${response.status}`);
  }
  return (await response.json()) as OpsMetrics;
}

// ===== Inquiry List =====

export interface InquiryListItem {
  inquiryId: string;
  question: string;
  customerChannel: string;
  status: string;
  documentCount: number;
  latestAnswerStatus: string | null;
  createdAt: string;
}

export interface InquiryListResponse {
  content: InquiryListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface InquiryListParams {
  page?: number;
  size?: number;
  sort?: string;
  status?: string[];
  channel?: string;
  keyword?: string;
  from?: string;
  to?: string;
}

export async function listInquiries(params: InquiryListParams = {}): Promise<InquiryListResponse> {
  const query = new URLSearchParams();
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  if (params.sort) query.set("sort", params.sort);
  if (params.status?.length) params.status.forEach(s => query.append("status", s));
  if (params.channel) query.set("channel", params.channel);
  if (params.keyword) query.set("keyword", params.keyword);
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);

  const response = await authFetch(`${API_BASE_URL}/api/v1/inquiries?${query.toString()}`);
  if (!response.ok) {
    throw new Error(`목록 조회 실패: ${response.status}`);
  }
  return (await response.json()) as InquiryListResponse;
}

// ===== Knowledge Base =====

export interface KbDocument {
  documentId: string;
  title: string;
  category: string;
  productFamily: string | null;
  fileName: string;
  fileSize: number;
  status: string;
  chunkCount: number | null;
  vectorCount: number | null;
  uploadedBy: string | null;
  tags: string | null;
  description: string | null;
  lastError: string | null;
  createdAt: string;
}

export interface KbDocumentListResponse {
  content: KbDocument[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface KbStats {
  totalDocuments: number;
  indexedDocuments: number;
  totalChunks: number;
  byCategory: Record<string, number>;
  byProductFamily: Record<string, number>;
}

export interface KbIndexingAccepted {
  documentId: string;
  status: string;
  message: string;
}

export interface KbBatchIndexingAccepted {
  queued: number;
  message: string;
}

export interface KbDocumentListParams {
  page?: number;
  size?: number;
  sort?: string;
  category?: string;
  productFamily?: string;
  status?: string;
  keyword?: string;
}

export interface UploadKbDocumentParams {
  file: File;
  title: string;
  category: string;
  productFamily?: string;
  description?: string;
  tags?: string;
}

export async function listKbDocuments(params: KbDocumentListParams = {}): Promise<KbDocumentListResponse> {
  const query = new URLSearchParams();
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  if (params.sort) query.set("sort", params.sort);
  if (params.category) query.set("category", params.category);
  if (params.productFamily) query.set("productFamily", params.productFamily);
  if (params.status) query.set("status", params.status);
  if (params.keyword) query.set("keyword", params.keyword);

  const response = await authFetch(`${API_BASE_URL}/api/v1/knowledge-base/documents?${query.toString()}`);
  if (!response.ok) {
    throw new Error(`KB 문서 목록 조회 실패: ${response.status}`);
  }
  return (await response.json()) as KbDocumentListResponse;
}

export async function uploadKbDocument(params: UploadKbDocumentParams): Promise<KbDocument> {
  const formData = new FormData();
  formData.append("file", params.file);
  formData.append("title", params.title);
  formData.append("category", params.category);
  if (params.productFamily) formData.append("productFamily", params.productFamily);
  if (params.description) formData.append("description", params.description);
  if (params.tags) formData.append("tags", params.tags);

  const response = await authFetch(`${API_BASE_URL}/api/v1/knowledge-base/documents`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`KB 문서 업로드 실패: ${response.status}`);
  }
  return (await response.json()) as KbDocument;
}

export async function getKbDocument(docId: string): Promise<KbDocument> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/knowledge-base/documents/${docId}`);
  if (!response.ok) {
    throw new Error(`KB 문서 조회 실패: ${response.status}`);
  }
  return (await response.json()) as KbDocument;
}

export async function deleteKbDocument(docId: string): Promise<void> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/knowledge-base/documents/${docId}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error(`KB 문서 삭제 실패: ${response.status}`);
  }
}

export async function indexKbDocument(docId: string): Promise<KbIndexingAccepted> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/knowledge-base/documents/${docId}/indexing/run`, {
    method: "POST",
  });
  if (!response.ok) {
    throw new Error(`KB 문서 인덱싱 실패: ${response.status}`);
  }
  return (await response.json()) as KbIndexingAccepted;
}

export async function indexAllKbDocuments(): Promise<KbBatchIndexingAccepted> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/knowledge-base/indexing/run`, {
    method: "POST",
  });
  if (!response.ok) {
    throw new Error(`KB 일괄 인덱싱 실패: ${response.status}`);
  }
  return (await response.json()) as KbBatchIndexingAccepted;
}

export interface UploadQueueFile {
  id: string;
  file: File;
  status: "pending" | "uploading" | "success" | "error";
  progress: number;
  result?: KbDocument;
  error?: string;
  metadata: {
    title: string;
    category: string;
    productFamily: string;
    description: string;
    tags: string;
  };
}

export async function uploadKbDocumentWithProgress(
  params: UploadKbDocumentParams,
  onProgress?: (percent: number) => void,
): Promise<KbDocument> {
  const formData = new FormData();
  formData.append("file", params.file);
  formData.append("title", params.title);
  formData.append("category", params.category);
  if (params.productFamily) formData.append("productFamily", params.productFamily);
  if (params.description) formData.append("description", params.description);
  if (params.tags) formData.append("tags", params.tags);

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${API_BASE_URL}/api/v1/knowledge-base/documents`);
    const token = getStoredAccessToken();
    if (token) {
      xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    }

    xhr.upload.addEventListener("progress", (e) => {
      if (e.lengthComputable && onProgress) {
        onProgress(Math.round((e.loaded / e.total) * 100));
      }
    });

    xhr.addEventListener("load", () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(JSON.parse(xhr.responseText) as KbDocument);
      } else {
        reject(new Error(`KB 문서 업로드 실패: ${xhr.status}`));
      }
    });

    xhr.addEventListener("error", () => reject(new Error("네트워크 오류")));
    xhr.addEventListener("abort", () => reject(new Error("업로드 취소됨")));

    xhr.send(formData);
  });
}

export async function getKbStats(): Promise<KbStats> {
  const response = await authFetch(`${API_BASE_URL}/api/v1/knowledge-base/stats`);
  if (!response.ok) {
    throw new Error(`KB 통계 조회 실패: ${response.status}`);
  }
  return (await response.json()) as KbStats;
}

// ===== Dashboard Analytics =====

export type DashboardPeriod = "today" | "7d" | "30d" | "90d" | "custom";

export interface TimelineDailyMetric {
  date: string;
  inquiriesCreated: number;
  answersSent: number;
  draftsCreated: number;
}

export interface TimelineData {
  period: string;
  from: string;
  to: string;
  data: TimelineDailyMetric[];
}

export interface ProcessingTimeData {
  period: string;
  from: string;
  to: string;
  avgProcessingTimeHours: number;
  medianProcessingTimeHours: number;
  minProcessingTimeHours: number;
  maxProcessingTimeHours: number;
  totalCompleted: number;
  avgByStep: Record<string, number>;
}

export interface KbUsageTopDocument {
  documentId: string;
  fileName: string;
  referenceCount: number;
}

export interface KbUsageData {
  period: string;
  from: string;
  to: string;
  totalEvidences: number;
  kbEvidences: number;
  kbUsageRate: number;
  topDocuments: KbUsageTopDocument[];
}

export async function getTimeline(period: DashboardPeriod = "30d", from?: string, to?: string): Promise<TimelineData> {
  const query = new URLSearchParams({ period });
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  const response = await authFetch(`${API_BASE_URL}/api/v1/ops/metrics/timeline?${query.toString()}`);
  if (!response.ok) {
    throw new Error(`타임라인 조회 실패: ${response.status}`);
  }
  return (await response.json()) as TimelineData;
}

export async function getProcessingTime(period: DashboardPeriod = "30d", from?: string, to?: string): Promise<ProcessingTimeData> {
  const query = new URLSearchParams({ period });
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  const response = await authFetch(`${API_BASE_URL}/api/v1/ops/metrics/processing-time?${query.toString()}`);
  if (!response.ok) {
    throw new Error(`처리 시간 조회 실패: ${response.status}`);
  }
  return (await response.json()) as ProcessingTimeData;
}

export async function getKbUsage(period: DashboardPeriod = "30d", from?: string, to?: string): Promise<KbUsageData> {
  const query = new URLSearchParams({ period });
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  const response = await authFetch(`${API_BASE_URL}/api/v1/ops/metrics/kb-usage?${query.toString()}`);
  if (!response.ok) {
    throw new Error(`KB 활용 통계 조회 실패: ${response.status}`);
  }
  return (await response.json()) as KbUsageData;
}

export function getMetricsCsvUrl(period: DashboardPeriod = "30d", from?: string, to?: string): string {
  const query = new URLSearchParams({ period });
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  return `${API_BASE_URL}/api/v1/ops/metrics/export/csv?${query.toString()}`;
}

// ===== Document Download =====

export function getDocumentDownloadUrl(documentId: string): string {
  return `${API_BASE_URL}/api/v1/documents/${documentId}/download`;
}

export function getDocumentPagesUrl(documentId: string, from: number, to: number): string {
  return `${API_BASE_URL}/api/v1/documents/${documentId}/pages?from=${from}&to=${to}`;
}
