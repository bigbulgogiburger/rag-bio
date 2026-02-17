export interface CreateInquiryPayload {
  question: string;
  customerChannel?: string;
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
}

export type AnswerTone = "professional" | "technical" | "brief";
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
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function createInquiry(payload: CreateInquiryPayload): Promise<AskQuestionResult> {
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries`, {
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

  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/documents`, {
    method: "POST",
    body: formData
  });

  if (!response.ok) {
    throw new Error(`Failed to upload document: ${response.status}`);
  }

  return (await response.json()) as DocumentUploadResult;
}

export async function getInquiry(inquiryId: string): Promise<InquiryDetail> {
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}`);

  if (!response.ok) {
    throw new Error(`Failed to fetch inquiry: ${response.status}`);
  }

  return (await response.json()) as InquiryDetail;
}

export async function listInquiryDocuments(inquiryId: string): Promise<DocumentStatus[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/documents`);

  if (!response.ok) {
    throw new Error(`Failed to fetch documents: ${response.status}`);
  }

  return (await response.json()) as DocumentStatus[];
}

export async function getInquiryIndexingStatus(inquiryId: string): Promise<InquiryIndexingStatus> {
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/documents/indexing-status`);

  if (!response.ok) {
    throw new Error(`Failed to fetch indexing status: ${response.status}`);
  }

  return (await response.json()) as InquiryIndexingStatus;
}

export async function runInquiryIndexing(inquiryId: string, failedOnly = false): Promise<IndexingRunResult> {
  const response = await fetch(
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
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/analysis`, {
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
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/draft`, {
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
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/latest`);
  if (!response.ok) {
    throw new Error(`Failed to fetch latest draft: ${response.status}`);
  }
  return (await response.json()) as AnswerDraftResult;
}

export async function listAnswerDraftHistory(inquiryId: string): Promise<AnswerDraftResult[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/history`);
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
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/review`, {
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
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/approve`, {
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
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/send`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-User-Id": principal, "X-User-Roles": "SENDER" },
    body: JSON.stringify({ actor: principal, channel, sendRequestId })
  });
  if (!response.ok) {
    throw new Error(`Failed to send draft: ${response.status}`);
  }
  return (await response.json()) as AnswerDraftResult;
}

export async function getOpsMetrics(): Promise<OpsMetrics> {
  const response = await fetch(`${API_BASE_URL}/api/v1/ops/metrics`);
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

  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries?${query.toString()}`);
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

  const response = await fetch(`${API_BASE_URL}/api/v1/knowledge-base/documents?${query.toString()}`);
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

  const response = await fetch(`${API_BASE_URL}/api/v1/knowledge-base/documents`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`KB 문서 업로드 실패: ${response.status}`);
  }
  return (await response.json()) as KbDocument;
}

export async function getKbDocument(docId: string): Promise<KbDocument> {
  const response = await fetch(`${API_BASE_URL}/api/v1/knowledge-base/documents/${docId}`);
  if (!response.ok) {
    throw new Error(`KB 문서 조회 실패: ${response.status}`);
  }
  return (await response.json()) as KbDocument;
}

export async function deleteKbDocument(docId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/v1/knowledge-base/documents/${docId}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    throw new Error(`KB 문서 삭제 실패: ${response.status}`);
  }
}

export async function indexKbDocument(docId: string): Promise<KbIndexingAccepted> {
  const response = await fetch(`${API_BASE_URL}/api/v1/knowledge-base/documents/${docId}/indexing/run`, {
    method: "POST",
  });
  if (!response.ok) {
    throw new Error(`KB 문서 인덱싱 실패: ${response.status}`);
  }
  return (await response.json()) as KbIndexingAccepted;
}

export async function indexAllKbDocuments(): Promise<KbBatchIndexingAccepted> {
  const response = await fetch(`${API_BASE_URL}/api/v1/knowledge-base/indexing/run`, {
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
  const response = await fetch(`${API_BASE_URL}/api/v1/knowledge-base/stats`);
  if (!response.ok) {
    throw new Error(`KB 통계 조회 실패: ${response.status}`);
  }
  return (await response.json()) as KbStats;
}

// ===== Document Download =====

export function getDocumentDownloadUrl(documentId: string): string {
  return `${API_BASE_URL}/api/v1/documents/${documentId}/download`;
}

export function getDocumentPagesUrl(documentId: string, from: number, to: number): string {
  return `${API_BASE_URL}/api/v1/documents/${documentId}/pages?from=${from}&to=${to}`;
}
