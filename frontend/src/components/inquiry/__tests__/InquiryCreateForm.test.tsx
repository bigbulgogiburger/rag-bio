import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import InquiryCreateForm from "../InquiryCreateForm";

const mockCreateInquiry = vi.fn();
const mockUploadDocument = vi.fn();
const mockPush = vi.fn();

vi.mock("@/lib/api/client", () => ({
  createInquiry: (...args: unknown[]) => mockCreateInquiry(...args),
  uploadInquiryDocument: (...args: unknown[]) => mockUploadDocument(...args),
  authFetch: vi.fn(),
  getStoredAccessToken: () => null,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: vi.fn(), back: vi.fn(), prefetch: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/inquiries/new",
  useSearchParams: () => new URLSearchParams(),
  useParams: () => ({}),
}));

describe("InquiryCreateForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the form title and fields", () => {
    render(<InquiryCreateForm />);
    expect(screen.getByText("새 고객 문의 등록")).toBeInTheDocument();
    expect(screen.getByText("질문")).toBeInTheDocument();
    expect(screen.getByText("채널")).toBeInTheDocument();
    expect(screen.getByText("답변 톤")).toBeInTheDocument();
  });

  it("renders submit button", () => {
    render(<InquiryCreateForm />);
    expect(screen.getByRole("button", { name: "문의 등록" })).toBeInTheDocument();
  });

  it("shows validation error for short question", async () => {
    const user = userEvent.setup();
    render(<InquiryCreateForm />);

    const textarea = screen.getByPlaceholderText("고객 기술 문의 내용을 입력하세요");
    await user.type(textarea, "짧은");
    await user.click(screen.getByRole("button", { name: "문의 등록" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("최소 10자 이상 입력해 주세요");
    });
  });

  it("shows validation error for empty question", async () => {
    const user = userEvent.setup();
    render(<InquiryCreateForm />);

    await user.click(screen.getByRole("button", { name: "문의 등록" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("질문을 입력해 주세요");
    });
  });

  it("submits form and redirects on success", async () => {
    mockCreateInquiry.mockResolvedValue({
      inquiryId: "new-inquiry-123",
      status: "RECEIVED",
      message: "Created",
    });

    const user = userEvent.setup();
    render(<InquiryCreateForm />);

    const textarea = screen.getByPlaceholderText("고객 기술 문의 내용을 입력하세요");
    await user.type(textarea, "이것은 테스트 질문입니다. 충분히 길게 작성합니다.");
    await user.click(screen.getByRole("button", { name: "문의 등록" }));

    await waitFor(() => {
      expect(mockCreateInquiry).toHaveBeenCalledWith({
        question: "이것은 테스트 질문입니다. 충분히 길게 작성합니다.",
        customerChannel: "email",
        preferredTone: "professional",
      });
    });

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith("/inquiries/new-inquiry-123?tab=answer");
    });
  });

  it("shows error toast on submission failure", async () => {
    mockCreateInquiry.mockRejectedValue(new Error("서버 오류"));

    const user = userEvent.setup();
    render(<InquiryCreateForm />);

    const textarea = screen.getByPlaceholderText("고객 기술 문의 내용을 입력하세요");
    await user.type(textarea, "이것은 테스트 질문입니다. 서버 오류 테스트.");
    await user.click(screen.getByRole("button", { name: "문의 등록" }));

    await waitFor(() => {
      expect(screen.getByText("서버 오류")).toBeInTheDocument();
    });
  });

  it("renders channel selector with options", () => {
    render(<InquiryCreateForm />);
    const channelSelect = screen.getByText("채널").closest("label")?.querySelector("select");
    expect(channelSelect).toBeInTheDocument();
    expect(channelSelect?.querySelectorAll("option")).toHaveLength(3);
  });

  it("renders tone selector with options", () => {
    render(<InquiryCreateForm />);
    const toneSelect = screen.getByText("답변 톤").closest("label")?.querySelector("select");
    expect(toneSelect).toBeInTheDocument();
    expect(toneSelect?.querySelectorAll("option")).toHaveLength(3);
  });
});
