"use client";

import { useParams, useRouter } from "next/navigation";
import { Tabs } from "@/components/ui";
import { Button } from "@/components/ui/button";
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
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => router.push("/inquiries")}
          >
            ← 목록으로
          </Button>
          <h2 className="text-xl font-semibold tracking-tight">
            문의 상세 <span className="text-sm text-muted-foreground">#{inquiryId.slice(0, 8)}</span>
          </h2>
        </div>
      </div>

      <div className="rounded-xl border bg-card p-6 shadow-sm">
        <Tabs tabs={tabs} defaultTab="info" />
      </div>
    </div>
  );
}
