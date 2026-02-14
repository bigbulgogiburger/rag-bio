"use client";

import { useParams, useRouter } from "next/navigation";
import { Tabs } from "@/components/ui";
import InquiryInfoTab from "@/components/inquiry/InquiryInfoTab";
import InquiryAnalysisTab from "@/components/inquiry/InquiryAnalysisTab";
import InquiryAnswerTab from "@/components/inquiry/InquiryAnswerTab";
import InquiryHistoryTab from "@/components/inquiry/InquiryHistoryTab";

export default function InquiryDetailPage() {
  const params = useParams();
  const router = useRouter();
  const inquiryId = params.id as string;

  const tabs = [
    { key: "info", label: "기본 정보", content: <InquiryInfoTab inquiryId={inquiryId} /> },
    { key: "analysis", label: "분석", content: <InquiryAnalysisTab inquiryId={inquiryId} /> },
    { key: "answer", label: "답변", content: <InquiryAnswerTab inquiryId={inquiryId} /> },
    { key: "history", label: "이력", content: <InquiryHistoryTab inquiryId={inquiryId} /> },
  ];

  return (
    <div className="stack">
      <div style={{ display: "flex", alignItems: "center", gap: "1rem", marginBottom: "1rem" }}>
        <button
          className="btn"
          onClick={() => router.push("/inquiries")}
          style={{ padding: "0.4rem 0.8rem" }}
        >
          ← 목록으로
        </button>
        <h2 style={{ margin: 0 }}>
          문의 상세 — {inquiryId.slice(0, 8)}
        </h2>
      </div>

      <div className="card">
        <Tabs tabs={tabs} defaultTab="info" />
      </div>
    </div>
  );
}
