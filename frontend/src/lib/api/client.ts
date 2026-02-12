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
}

export interface InquiryIndexingStatus {
  inquiryId: string;
  total: number;
  uploaded: number;
  parsing: number;
  parsed: number;
  chunked: number;
  failed: number;
  documents: DocumentStatus[];
}

export interface IndexingRunResult {
  inquiryId: string;
  processed: number;
  succeeded: number;
  failed: number;
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

export async function runInquiryIndexing(inquiryId: string): Promise<IndexingRunResult> {
  const response = await fetch(`${API_BASE_URL}/api/v1/inquiries/${inquiryId}/documents/indexing/run`, {
    method: "POST"
  });

  if (!response.ok) {
    throw new Error(`Failed to run indexing: ${response.status}`);
  }

  return (await response.json()) as IndexingRunResult;
}
