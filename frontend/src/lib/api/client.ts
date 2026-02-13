export interface CreateInquiryPayload {
  question: string;
  customerChannel?: string;
}

export interface AskQuestionResult {
  inquiryId: string;
  status: string;
  message: string;
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
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/review`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ actor, comment })
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
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/approve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ actor, comment })
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
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/answers/${answerId}/send`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ actor, channel, sendRequestId })
  });
  if (!response.ok) {
    throw new Error(`Failed to send draft: ${response.status}`);
  }
  return (await response.json()) as AnswerDraftResult;
}
