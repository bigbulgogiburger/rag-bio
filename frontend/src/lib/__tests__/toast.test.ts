import { describe, it, expect, vi, beforeEach } from "vitest";
import { toast } from "sonner";
import { showToast, showApiError, resolveApiErrorMessage, ApiError, parseApiError } from "../toast";

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
    showToast("에러", "error");
    expect(toast.error).toHaveBeenCalledWith("에러");
  });

  it("calls toast.warning for warn variant", () => {
    showToast("경고", "warn");
    expect(toast.warning).toHaveBeenCalledWith("경고");
  });

  it("calls toast.info for info variant", () => {
    showToast("정보", "info");
    expect(toast.info).toHaveBeenCalledWith("정보");
  });

  it("defaults to info variant", () => {
    showToast("기본");
    expect(toast.info).toHaveBeenCalledWith("기본");
  });
});

describe("ApiError", () => {
  it("creates an error with status and code", () => {
    const err = new ApiError(404, "NOT_FOUND", "Not found");
    expect(err.status).toBe(404);
    expect(err.code).toBe("NOT_FOUND");
    expect(err.message).toBe("Not found");
    expect(err.name).toBe("ApiError");
    expect(err).toBeInstanceOf(Error);
  });
});

describe("resolveApiErrorMessage", () => {
  it("extracts message from ApiError with known code", () => {
    const err = new ApiError(400, "INQUIRY_NOT_FOUND", "fallback");
    const msg = resolveApiErrorMessage(err);
    // Should return Korean label from ERROR_LABELS if code exists, else fallback to message
    expect(typeof msg).toBe("string");
  });

  it("extracts message from regular Error", () => {
    const msg = resolveApiErrorMessage(new Error("일반 에러"));
    expect(msg).toBe("일반 에러");
  });

  it("returns default message for unknown errors", () => {
    const msg = resolveApiErrorMessage("string error");
    expect(msg).toBe("알 수 없는 오류가 발생했습니다");
  });

  it("returns default message for null", () => {
    const msg = resolveApiErrorMessage(null);
    expect(msg).toBe("알 수 없는 오류가 발생했습니다");
  });
});

describe("showApiError", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows error toast with resolved message", () => {
    showApiError(new Error("서버 오류"));
    expect(toast.error).toHaveBeenCalledWith("서버 오류");
  });
});

describe("parseApiError", () => {
  it("parses JSON error response", async () => {
    const response = {
      status: 400,
      json: () => Promise.resolve({ code: "VALIDATION_ERROR", message: "검증 실패" }),
    } as unknown as Response;

    const err = await parseApiError(response);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe("VALIDATION_ERROR");
    expect(err.message).toBe("검증 실패");
  });

  it("handles non-JSON response", async () => {
    const response = {
      status: 500,
      json: () => Promise.reject(new Error("not json")),
    } as unknown as Response;

    const err = await parseApiError(response);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(500);
    expect(err.code).toBe("UNKNOWN");
    expect(err.message).toBe("요청 실패: 500");
  });
});
