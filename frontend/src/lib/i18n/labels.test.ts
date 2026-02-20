import { describe, it, expect } from "vitest";
import {
  label,
  labelVerdict,
  labelInquiryStatus,
  labelDocStatus,
  labelChannel,
  labelTone,
  labelAnswerStatus,
  labelRiskFlag,
  labelKbCategory,
  labelError,
  labelReviewDecision,
  labelApprovalDecision,
  labelIssueSeverity,
  labelIssueCategory,
  docStatusBadgeVariant,
  VERDICT_LABELS,
} from "./labels";

describe("labels (i18n)", () => {
  describe("label()", () => {
    it("returns mapped value for known key", () => {
      expect(label(VERDICT_LABELS, "SUPPORTED")).toBe("근거 충분");
    });

    it("returns the key itself for unknown key", () => {
      expect(label(VERDICT_LABELS, "UNKNOWN")).toBe("UNKNOWN");
    });
  });

  describe("labelVerdict", () => {
    it("returns Korean labels for known verdicts", () => {
      expect(labelVerdict("SUPPORTED")).toBe("근거 충분");
      expect(labelVerdict("REFUTED")).toBe("근거 부족");
      expect(labelVerdict("CONDITIONAL")).toBe("조건부");
    });

    it("returns the key for unknown verdicts", () => {
      expect(labelVerdict("UNKNOWN_VERDICT")).toBe("UNKNOWN_VERDICT");
    });
  });

  describe("labelInquiryStatus", () => {
    it("returns Korean labels for known statuses", () => {
      expect(labelInquiryStatus("RECEIVED")).toBe("접수됨");
      expect(labelInquiryStatus("ANALYZED")).toBe("분석 완료");
      expect(labelInquiryStatus("ANSWERED")).toBe("답변 생성됨");
      expect(labelInquiryStatus("CLOSED")).toBe("종료");
    });
  });

  describe("labelDocStatus", () => {
    it("returns Korean labels for document statuses", () => {
      expect(labelDocStatus("UPLOADED")).toBe("업로드됨");
      expect(labelDocStatus("PARSING")).toBe("인덱싱 중");
      expect(labelDocStatus("INDEXED")).toBe("인덱싱 완료");
      expect(labelDocStatus("FAILED_PARSING")).toBe("인덱싱 실패");
    });
  });

  describe("labelChannel", () => {
    it("returns Korean labels for channels", () => {
      expect(labelChannel("email")).toBe("이메일");
      expect(labelChannel("messenger")).toBe("메신저");
      expect(labelChannel("portal")).toBe("포털");
    });
  });

  describe("labelTone", () => {
    it("returns Korean labels for tones", () => {
      expect(labelTone("professional")).toBe("정중체");
      expect(labelTone("technical")).toBe("기술 상세");
      expect(labelTone("brief")).toBe("요약");
    });
  });

  describe("labelAnswerStatus", () => {
    it("returns Korean labels for answer statuses", () => {
      expect(labelAnswerStatus("DRAFT")).toBe("초안");
      expect(labelAnswerStatus("REVIEWED")).toBe("검토 완료");
      expect(labelAnswerStatus("APPROVED")).toBe("승인 완료");
      expect(labelAnswerStatus("SENT")).toBe("발송 완료");
    });
  });

  describe("labelRiskFlag", () => {
    it("returns Korean labels for risk flags", () => {
      expect(labelRiskFlag("LOW_CONFIDENCE")).toBe("신뢰도 낮음");
      expect(labelRiskFlag("FALLBACK_DRAFT_USED")).toBe("대체 초안 사용됨");
    });
  });

  describe("labelKbCategory", () => {
    it("returns Korean labels for KB categories", () => {
      expect(labelKbCategory("MANUAL")).toBe("매뉴얼");
      expect(labelKbCategory("FAQ")).toBe("FAQ");
    });
  });

  describe("labelError", () => {
    it("returns Korean labels for known error codes", () => {
      expect(labelError("NOT_FOUND")).toBe("요청한 항목을 찾을 수 없습니다");
      expect(labelError("NETWORK_ERROR")).toBe("네트워크 오류가 발생했습니다. 인터넷 연결을 확인해 주세요");
    });

    it("returns key for unknown error codes", () => {
      expect(labelError("SOME_NEW_ERROR")).toBe("SOME_NEW_ERROR");
    });
  });

  describe("labelReviewDecision", () => {
    it("returns Korean labels for review decisions", () => {
      expect(labelReviewDecision("PASS")).toBe("통과");
      expect(labelReviewDecision("REVISE")).toBe("수정 필요");
      expect(labelReviewDecision("REJECT")).toBe("거부");
    });
  });

  describe("labelApprovalDecision", () => {
    it("returns Korean labels for approval decisions", () => {
      expect(labelApprovalDecision("AUTO_APPROVED")).toBe("자동 승인");
      expect(labelApprovalDecision("ESCALATED")).toBe("사람 확인 필요");
    });
  });

  describe("labelIssueSeverity", () => {
    it("returns Korean labels for severities", () => {
      expect(labelIssueSeverity("CRITICAL")).toBe("치명적");
      expect(labelIssueSeverity("LOW")).toBe("낮음");
    });
  });

  describe("labelIssueCategory", () => {
    it("returns Korean labels for categories", () => {
      expect(labelIssueCategory("ACCURACY")).toBe("정확성");
      expect(labelIssueCategory("TONE")).toBe("톤/형식");
    });
  });

  describe("docStatusBadgeVariant", () => {
    it("returns correct badge variants", () => {
      expect(docStatusBadgeVariant("UPLOADED")).toBe("neutral");
      expect(docStatusBadgeVariant("PARSING")).toBe("warn");
      expect(docStatusBadgeVariant("INDEXED")).toBe("success");
      expect(docStatusBadgeVariant("FAILED")).toBe("danger");
      expect(docStatusBadgeVariant("FAILED_PARSING")).toBe("danger");
      expect(docStatusBadgeVariant("UNKNOWN")).toBe("neutral");
    });
  });
});
