import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// Mock fetch globally
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// We import AFTER mocking fetch
import {
  getStoredAccessToken,
  getStoredRefreshToken,
  storeTokens,
  clearTokens,
  login,
  refreshAccessToken,
  fetchMe,
  authFetch,
  createInquiry,
  uploadInquiryDocument,
  getInquiry,
  updateInquiry,
  listInquiryDocuments,
  getInquiryIndexingStatus,
  runInquiryIndexing,
  analyzeInquiry,
  draftInquiryAnswer,
  getLatestAnswerDraft,
  listAnswerDraftHistory,
  reviewAnswerDraft,
  approveAnswerDraft,
  sendAnswerDraft,
  aiReviewAnswer,
  aiApproveAnswer,
  autoWorkflow,
  updateAnswerDraft,
  getOpsMetrics,
  listInquiries,
  listKbDocuments,
  uploadKbDocument,
  getKbDocument,
  deleteKbDocument,
  indexKbDocument,
  indexAllKbDocuments,
  getKbStats,
  getTimeline,
  getProcessingTime,
  getKbUsage,
  getMetricsCsvUrl,
  getDocumentDownloadUrl,
  getDocumentPagesUrl,
} from "../client";

function mockResponse(data: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: () => Promise.resolve(data),
    headers: new Headers(),
    redirected: false,
    statusText: ok ? "OK" : "Error",
    type: "basic" as ResponseType,
    url: "",
    clone: () => mockResponse(data, ok, status),
    body: null,
    bodyUsed: false,
    arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
    blob: () => Promise.resolve(new Blob()),
    formData: () => Promise.resolve(new FormData()),
    text: () => Promise.resolve(JSON.stringify(data)),
    bytes: () => Promise.resolve(new Uint8Array()),
  } as Response;
}

describe("Token Storage", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("stores and retrieves access token", () => {
    expect(getStoredAccessToken()).toBeNull();
    storeTokens("access-123", "refresh-456");
    expect(getStoredAccessToken()).toBe("access-123");
  });

  it("stores and retrieves refresh token", () => {
    expect(getStoredRefreshToken()).toBeNull();
    storeTokens("access-123", "refresh-456");
    expect(getStoredRefreshToken()).toBe("refresh-456");
  });

  it("clears tokens", () => {
    storeTokens("access-123", "refresh-456");
    clearTokens();
    expect(getStoredAccessToken()).toBeNull();
    expect(getStoredRefreshToken()).toBeNull();
  });
});

describe("Auth API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  describe("login", () => {
    it("logs in and stores tokens", async () => {
      const loginResult = {
        accessToken: "at-1",
        refreshToken: "rt-1",
        tokenType: "Bearer",
        expiresIn: 900,
        user: { id: "u1", username: "admin", displayName: "Admin", email: "a@b.com", roles: ["ADMIN"] },
      };
      mockFetch.mockResolvedValueOnce(mockResponse(loginResult));

      const result = await login({ username: "admin", password: "pass" });
      expect(result).toEqual(loginResult);
      expect(getStoredAccessToken()).toBe("at-1");
      expect(getStoredRefreshToken()).toBe("rt-1");
    });

    it("throws on 401", async () => {
      mockFetch.mockResolvedValueOnce(mockResponse(null, false, 401));
      await expect(login({ username: "wrong", password: "wrong" })).rejects.toThrow("아이디 또는 비밀번호가 올바르지 않습니다.");
    });

    it("throws on other errors", async () => {
      mockFetch.mockResolvedValueOnce(mockResponse(null, false, 500));
      await expect(login({ username: "a", password: "b" })).rejects.toThrow("로그인 실패: 500");
    });
  });

  describe("refreshAccessToken", () => {
    it("refreshes and stores new access token", async () => {
      storeTokens("old-at", "valid-rt");
      const refreshResult = { accessToken: "new-at", tokenType: "Bearer", expiresIn: 900 };
      mockFetch.mockResolvedValueOnce(mockResponse(refreshResult));

      const result = await refreshAccessToken();
      expect(result).toEqual(refreshResult);
      expect(getStoredAccessToken()).toBe("new-at");
    });

    it("throws when no refresh token", async () => {
      await expect(refreshAccessToken()).rejects.toThrow("No refresh token available");
    });

    it("clears tokens on refresh failure", async () => {
      storeTokens("at", "rt");
      mockFetch.mockResolvedValueOnce(mockResponse(null, false, 401));

      await expect(refreshAccessToken()).rejects.toThrow("토큰 갱신 실패");
      expect(getStoredAccessToken()).toBeNull();
    });
  });

  describe("fetchMe", () => {
    it("fetches authenticated user", async () => {
      storeTokens("my-token", "rt");
      const user = { id: "u1", username: "admin", displayName: "Admin", email: null, roles: ["USER"] };
      mockFetch.mockResolvedValueOnce(mockResponse(user));

      const result = await fetchMe();
      expect(result).toEqual(user);
    });

    it("throws on failure", async () => {
      mockFetch.mockResolvedValueOnce(mockResponse(null, false, 401));
      await expect(fetchMe()).rejects.toThrow("인증 정보 조회 실패");
    });
  });
});

describe("authFetch", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("adds Authorization header when token exists", async () => {
    storeTokens("my-token", "rt");
    mockFetch.mockResolvedValueOnce(mockResponse({ ok: true }));

    await authFetch("http://api/test");
    const callHeaders = mockFetch.mock.calls[0][1]?.headers;
    expect(callHeaders.get("Authorization")).toBe("Bearer my-token");
  });

  it("retries with new token on 401", async () => {
    storeTokens("expired-token", "valid-refresh");
    // First call: 401
    mockFetch.mockResolvedValueOnce(mockResponse(null, false, 401));
    // Refresh call: success
    mockFetch.mockResolvedValueOnce(mockResponse({ accessToken: "new-token", tokenType: "Bearer", expiresIn: 900 }));
    // Retry call: success
    mockFetch.mockResolvedValueOnce(mockResponse({ data: "ok" }));

    const response = await authFetch("http://api/test");
    expect(response.ok).toBe(true);
    expect(mockFetch).toHaveBeenCalledTimes(3);
  });
});

describe("Inquiry API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("createInquiry sends POST and returns result", async () => {
    const result = { inquiryId: "i-1", status: "RECEIVED", message: "Created" };
    mockFetch.mockResolvedValueOnce(mockResponse(result));

    const data = await createInquiry({ question: "Test question" });
    expect(data).toEqual(result);
  });

  it("createInquiry throws on error", async () => {
    mockFetch.mockResolvedValueOnce(mockResponse(null, false, 500));
    await expect(createInquiry({ question: "q" })).rejects.toThrow("Failed to create inquiry: 500");
  });

  it("getInquiry fetches inquiry detail", async () => {
    const detail = { inquiryId: "i-1", question: "Q", customerChannel: "email", status: "RECEIVED", createdAt: "2025-01-01" };
    mockFetch.mockResolvedValueOnce(mockResponse(detail));

    const data = await getInquiry("i-1");
    expect(data).toEqual(detail);
  });

  it("updateInquiry sends PATCH", async () => {
    const updated = { inquiryId: "i-1", question: "Updated Q", customerChannel: "email", status: "RECEIVED", createdAt: "2025-01-01" };
    mockFetch.mockResolvedValueOnce(mockResponse(updated));

    const data = await updateInquiry("i-1", "Updated Q");
    expect(data).toEqual(updated);
  });

  it("uploadInquiryDocument uploads file", async () => {
    const result = { documentId: "d-1", inquiryId: "i-1", fileName: "test.pdf", status: "UPLOADED" };
    mockFetch.mockResolvedValueOnce(mockResponse(result));

    const file = new File(["content"], "test.pdf");
    const data = await uploadInquiryDocument("i-1", file);
    expect(data).toEqual(result);
  });

  it("listInquiryDocuments fetches documents", async () => {
    const docs = [{ documentId: "d-1", fileName: "test.pdf" }];
    mockFetch.mockResolvedValueOnce(mockResponse(docs));

    const data = await listInquiryDocuments("i-1");
    expect(data).toEqual(docs);
  });

  it("getInquiryIndexingStatus fetches status", async () => {
    const status = { total: 3, indexed: 2, failed: 0, documents: [] };
    mockFetch.mockResolvedValueOnce(mockResponse(status));

    const data = await getInquiryIndexingStatus("i-1");
    expect(data).toEqual(status);
  });

  it("runInquiryIndexing triggers indexing", async () => {
    const result = { inquiryId: "i-1", processed: 3, succeeded: 3, failed: 0 };
    mockFetch.mockResolvedValueOnce(mockResponse(result));

    const data = await runInquiryIndexing("i-1");
    expect(data).toEqual(result);
  });

  it("listInquiries with params", async () => {
    const list = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
    mockFetch.mockResolvedValueOnce(mockResponse(list));

    const data = await listInquiries({ page: 0, size: 20, status: ["RECEIVED"], channel: "email", keyword: "test", from: "2025-01-01", to: "2025-12-31", sort: "createdAt,desc" });
    expect(data).toEqual(list);
  });
});

describe("Analysis & Answer API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("analyzeInquiry sends analysis request", async () => {
    const result = { inquiryId: "i-1", verdict: "SUPPORTED", confidence: 95, reason: "r", riskFlags: [], evidences: [] };
    mockFetch.mockResolvedValueOnce(mockResponse(result));

    const data = await analyzeInquiry("i-1", "question", 5);
    expect(data).toEqual(result);
  });

  it("draftInquiryAnswer generates draft", async () => {
    const draft = { answerId: "a-1", version: 1, status: "DRAFT", draft: "Answer text" };
    mockFetch.mockResolvedValueOnce(mockResponse(draft));

    const data = await draftInquiryAnswer("i-1", "question");
    expect(data).toEqual(draft);
  });

  it("getLatestAnswerDraft fetches latest", async () => {
    const draft = { answerId: "a-1", version: 2 };
    mockFetch.mockResolvedValueOnce(mockResponse(draft));

    const data = await getLatestAnswerDraft("i-1");
    expect(data).toEqual(draft);
  });

  it("listAnswerDraftHistory fetches history", async () => {
    const history = [{ answerId: "a-1", version: 1 }, { answerId: "a-2", version: 2 }];
    mockFetch.mockResolvedValueOnce(mockResponse(history));

    const data = await listAnswerDraftHistory("i-1");
    expect(data).toEqual(history);
  });

  it("reviewAnswerDraft sends review", async () => {
    const reviewed = { answerId: "a-1", status: "REVIEWED" };
    mockFetch.mockResolvedValueOnce(mockResponse(reviewed));

    const data = await reviewAnswerDraft("i-1", "a-1", "reviewer", "LGTM");
    expect(data).toEqual(reviewed);
  });

  it("approveAnswerDraft sends approval", async () => {
    const approved = { answerId: "a-1", status: "APPROVED" };
    mockFetch.mockResolvedValueOnce(mockResponse(approved));

    const data = await approveAnswerDraft("i-1", "a-1", "lead", "OK");
    expect(data).toEqual(approved);
  });

  it("sendAnswerDraft sends draft", async () => {
    const sent = { answerId: "a-1", status: "SENT" };
    mockFetch.mockResolvedValueOnce(mockResponse(sent));

    const data = await sendAnswerDraft("i-1", "a-1", "sender", "email", "req-1");
    expect(data).toEqual(sent);
  });

  it("aiReviewAnswer calls AI review", async () => {
    const result = { decision: "PASS", score: 90, issues: [], revisedDraft: null, summary: "Good" };
    mockFetch.mockResolvedValueOnce(mockResponse(result));

    const data = await aiReviewAnswer("i-1", "a-1");
    expect(data).toEqual(result);
  });

  it("aiApproveAnswer calls AI approval", async () => {
    const result = { decision: "AUTO_APPROVED", reason: "OK", gateResults: [] };
    mockFetch.mockResolvedValueOnce(mockResponse(result));

    const data = await aiApproveAnswer("i-1", "a-1");
    expect(data).toEqual(result);
  });

  it("autoWorkflow calls auto workflow", async () => {
    const result = { review: {}, approval: {}, finalStatus: "APPROVED", requiresHumanAction: false, summary: "Done" };
    mockFetch.mockResolvedValueOnce(mockResponse(result));

    const data = await autoWorkflow("i-1", "a-1");
    expect(data).toEqual(result);
  });

  it("updateAnswerDraft sends edit", async () => {
    const updated = { answerId: "a-1", draft: "Updated" };
    mockFetch.mockResolvedValueOnce(mockResponse(updated));

    const data = await updateAnswerDraft("i-1", "a-1", "Updated");
    expect(data).toEqual(updated);
  });
});

describe("Ops API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("getOpsMetrics fetches metrics", async () => {
    const metrics = { approvedOrSentCount: 10, sentCount: 8 };
    mockFetch.mockResolvedValueOnce(mockResponse(metrics));

    const data = await getOpsMetrics();
    expect(data).toEqual(metrics);
  });
});

describe("Knowledge Base API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("listKbDocuments fetches KB documents", async () => {
    const list = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
    mockFetch.mockResolvedValueOnce(mockResponse(list));

    const data = await listKbDocuments({ page: 0, size: 20, category: "FAQ", productFamily: "CFX", status: "INDEXED", keyword: "test", sort: "createdAt" });
    expect(data).toEqual(list);
  });

  it("uploadKbDocument uploads document", async () => {
    const doc = { documentId: "kb-1", title: "Doc", category: "FAQ" };
    mockFetch.mockResolvedValueOnce(mockResponse(doc));

    const file = new File(["content"], "doc.pdf");
    const data = await uploadKbDocument({ file, title: "Doc", category: "FAQ", productFamily: "CFX", description: "desc", tags: "tag1" });
    expect(data).toEqual(doc);
  });

  it("getKbDocument fetches single KB document", async () => {
    const doc = { documentId: "kb-1", title: "Doc" };
    mockFetch.mockResolvedValueOnce(mockResponse(doc));

    const data = await getKbDocument("kb-1");
    expect(data).toEqual(doc);
  });

  it("deleteKbDocument deletes document", async () => {
    mockFetch.mockResolvedValueOnce(mockResponse(null, true, 204));
    await expect(deleteKbDocument("kb-1")).resolves.toBeUndefined();
  });

  it("indexKbDocument triggers indexing", async () => {
    const result = { documentId: "kb-1", status: "INDEXING", message: "Started" };
    mockFetch.mockResolvedValueOnce(mockResponse(result));

    const data = await indexKbDocument("kb-1");
    expect(data).toEqual(result);
  });

  it("indexAllKbDocuments batch indexes", async () => {
    const result = { queued: 5, message: "Queued" };
    mockFetch.mockResolvedValueOnce(mockResponse(result));

    const data = await indexAllKbDocuments();
    expect(data).toEqual(result);
  });

  it("getKbStats fetches statistics", async () => {
    const stats = { totalDocuments: 10, indexedDocuments: 8, totalChunks: 100, byCategory: {}, byProductFamily: {} };
    mockFetch.mockResolvedValueOnce(mockResponse(stats));

    const data = await getKbStats();
    expect(data).toEqual(stats);
  });
});

describe("Dashboard Analytics API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("getTimeline fetches timeline data", async () => {
    const timeline = { period: "30d", from: "2025-01-01", to: "2025-01-31", data: [] };
    mockFetch.mockResolvedValueOnce(mockResponse(timeline));

    const data = await getTimeline("30d", "2025-01-01", "2025-01-31");
    expect(data).toEqual(timeline);
  });

  it("getProcessingTime fetches processing time data", async () => {
    const pt = { period: "7d", avgProcessingTimeHours: 2.5 };
    mockFetch.mockResolvedValueOnce(mockResponse(pt));

    const data = await getProcessingTime("7d", "2025-01-01", "2025-01-07");
    expect(data).toEqual(pt);
  });

  it("getKbUsage fetches KB usage data", async () => {
    const usage = { period: "30d", totalEvidences: 100, kbEvidences: 60, kbUsageRate: 60 };
    mockFetch.mockResolvedValueOnce(mockResponse(usage));

    const data = await getKbUsage("30d", "2025-01-01", "2025-01-31");
    expect(data).toEqual(usage);
  });
});

describe("URL Builders", () => {
  it("getMetricsCsvUrl builds correct URL", () => {
    const url = getMetricsCsvUrl("7d", "2025-01-01", "2025-01-07");
    expect(url).toContain("/api/v1/ops/metrics/export/csv");
    expect(url).toContain("period=7d");
    expect(url).toContain("from=2025-01-01");
    expect(url).toContain("to=2025-01-07");
  });

  it("getDocumentDownloadUrl builds correct URL", () => {
    const url = getDocumentDownloadUrl("doc-1");
    expect(url).toContain("/api/v1/documents/doc-1/download");
  });

  it("getDocumentPagesUrl builds correct URL", () => {
    const url = getDocumentPagesUrl("doc-1", 1, 5);
    expect(url).toContain("/api/v1/documents/doc-1/pages?from=1&to=5");
  });
});
