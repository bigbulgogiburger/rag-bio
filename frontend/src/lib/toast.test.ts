import { describe, it, expect, vi, beforeEach } from "vitest";
import { toast } from "sonner";
import {
  showToast,
  showApiError,
  resolveApiErrorMessage,
  ApiError,
  parseApiError,
} from "./toast";

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}));

describe("showToast", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls toast.success for success variant", () => {
    showToast("성공", "success");
    expect(toast.success).toHaveBeenCalledWith("성공");
  });

  it("calls toast.error for error variant", () => {
    showToast("실패", "error");
    expect(toast.error).toHaveBeenCalledWith("실패");
  });

  it("calls toast.warning for warn variant", () => {
    showToast("주의", "warn");
    expect(toast.warning).toHaveBeenCalledWith("주의");
  });

  it("calls toast.info for info variant", () => {
    showToast("정보", "info");
    expect(toast.info).toHaveBeenCalledWith("정보");
  });

  it("defaults to info variant when not specified", () => {
    showToast("기본");
    expect(toast.info).toHaveBeenCalledWith("기본");
  });
});

describe("ApiError", () => {
  it("creates instance with status, code, message", () => {
    const err = new ApiError(404, "NOT_FOUND", "리소스를 찾을 수 없습니다");
    expect(err.status).toBe(404);
    expect(err.code).toBe("NOT_FOUND");
    expect(err.message).toBe("리소스를 찾을 수 없습니다");
    expect(err.name).toBe("ApiError");
    expect(err).toBeInstanceOf(Error);
  });
});

describe("resolveApiErrorMessage", () => {
  it("returns Korean label for known ApiError code", () => {
    const err = new ApiError(401, "UNAUTHORIZED", "Unauthorized");
    const message = resolveApiErrorMessage(err);
    // UNAUTHORIZED exists in ERROR_LABELS
    expect(message).toBe("인증이 필요합니다. 다시 로그인해 주세요");
    expect(typeof message).toBe("string");
    expect(message.length).toBeGreaterThan(0);
  });

  it("falls back to error message for unknown ApiError code", () => {
    const err = new ApiError(500, "UNKNOWN_CODE_XYZ", "서버 오류");
    const message = resolveApiErrorMessage(err);
    expect(message).toBe("서버 오류");
  });

  it("returns message for generic Error", () => {
    const err = new Error("일반 에러 발생");
    expect(resolveApiErrorMessage(err)).toBe("일반 에러 발생");
  });

  it("returns default message for non-Error values", () => {
    expect(resolveApiErrorMessage("string error")).toBe("알 수 없는 오류가 발생했습니다");
    expect(resolveApiErrorMessage(null)).toBe("알 수 없는 오류가 발생했습니다");
    expect(resolveApiErrorMessage(undefined)).toBe("알 수 없는 오류가 발생했습니다");
    expect(resolveApiErrorMessage(42)).toBe("알 수 없는 오류가 발생했습니다");
  });
});

describe("showApiError", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows error toast with resolved message", () => {
    const err = new ApiError(400, "UNKNOWN_XYZ", "잘못된 요청");
    showApiError(err);
    expect(toast.error).toHaveBeenCalledWith("잘못된 요청");
  });

  it("shows error toast for generic Error", () => {
    showApiError(new Error("테스트 에러"));
    expect(toast.error).toHaveBeenCalledWith("테스트 에러");
  });

  it("shows default error toast for non-Error", () => {
    showApiError("random");
    expect(toast.error).toHaveBeenCalledWith("알 수 없는 오류가 발생했습니다");
  });
});

describe("parseApiError", () => {
  it("parses JSON response with code and message", async () => {
    const response = {
      status: 400,
      json: vi.fn().mockResolvedValue({ code: "VALIDATION_ERROR", message: "필드가 누락되었습니다" }),
    } as unknown as Response;

    const err = await parseApiError(response);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe("VALIDATION_ERROR");
    expect(err.message).toBe("필드가 누락되었습니다");
  });

  it("uses defaults when JSON parsing fails", async () => {
    const response = {
      status: 500,
      json: vi.fn().mockRejectedValue(new Error("not json")),
    } as unknown as Response;

    const err = await parseApiError(response);
    expect(err.status).toBe(500);
    expect(err.code).toBe("UNKNOWN");
    expect(err.message).toBe("요청 실패: 500");
  });

  it("handles partial JSON body (only code)", async () => {
    const response = {
      status: 403,
      json: vi.fn().mockResolvedValue({ code: "FORBIDDEN" }),
    } as unknown as Response;

    const err = await parseApiError(response);
    expect(err.code).toBe("FORBIDDEN");
    expect(err.message).toBe("요청 실패: 403");
  });

  it("handles partial JSON body (only message)", async () => {
    const response = {
      status: 404,
      json: vi.fn().mockResolvedValue({ message: "리소스 없음" }),
    } as unknown as Response;

    const err = await parseApiError(response);
    expect(err.code).toBe("UNKNOWN");
    expect(err.message).toBe("리소스 없음");
  });
});
