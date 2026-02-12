export interface CreateInquiryPayload {
  question: string;
  customerChannel?: string;
}

export interface AskQuestionResult {
  inquiryId: string;
  status: string;
  message: string;
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
