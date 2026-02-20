import { toast } from "sonner";
import { ERROR_LABELS } from "@/lib/i18n/labels";

type ToastVariant = "success" | "error" | "warn" | "info";

/** 전역 토스트 표시 */
export function showToast(message: string, variant: ToastVariant = "info") {
  switch (variant) {
    case "success":
      toast.success(message);
      break;
    case "error":
      toast.error(message);
      break;
    case "warn":
      toast.warning(message);
      break;
    case "info":
      toast.info(message);
      break;
  }
}

/** API 에러에서 한국어 메시지를 추출하여 토스트로 표시 */
export function showApiError(error: unknown) {
  const message = resolveApiErrorMessage(error);
  showToast(message, "error");
}

/** API 에러에서 한국어 메시지를 추출 */
export function resolveApiErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return ERROR_LABELS[error.code] ?? error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "알 수 없는 오류가 발생했습니다";
}

/** API 에러 클래스 — 서버 에러 코드를 포함 */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/** fetch 응답에서 에러를 파싱하여 ApiError 인스턴스를 생성 */
export async function parseApiError(response: Response): Promise<ApiError> {
  let code = "UNKNOWN";
  let message = `요청 실패: ${response.status}`;

  try {
    const body = await response.json();
    if (body.code) code = body.code;
    if (body.message) message = body.message;
  } catch {
    // JSON 파싱 실패 시 기본 메시지 사용
  }

  return new ApiError(response.status, code, message);
}
