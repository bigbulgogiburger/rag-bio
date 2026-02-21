"use client";

import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useState, useEffect } from "react";
import { Tabs } from "@/components/ui";
import { Button } from "@/components/ui/button";
import InquiryInfoTab from "@/components/inquiry/InquiryInfoTab";
import InquiryAnswerTab from "@/components/inquiry/InquiryAnswerTab";
import InquiryHistoryTab from "@/components/inquiry/InquiryHistoryTab";
import { TabErrorBoundary } from "@/components/error";
import { getInquiry, type InquiryDetail } from "@/lib/api/client";

function useInquiryId(): string {
  const params = useParams();
  const paramId = params.id as string;
  const [resolvedId, setResolvedId] = useState(paramId);

  useEffect(() => {
    // In static export, useParams() returns the pre-rendered placeholder "_".
    // Read the actual inquiry ID from the current URL instead.
    const match = window.location.pathname.match(/\/inquiries\/([^/]+)/);
    if (match && match[1] && match[1] !== "_") {
      setResolvedId(match[1]);
    }
  }, []);

  return resolvedId !== "_" ? resolvedId : paramId;
}

export default function InquiryDetailClient() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const inquiryId = useInquiryId();

  const [inquiry, setInquiry] = useState<InquiryDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const tabParam = searchParams.get("tab");
  const validTabs = ["info", "answer", "history"];
  const defaultTab = tabParam && validTabs.includes(tabParam) ? tabParam : "info";

  useEffect(() => {
    if (inquiryId === "_") return;
    getInquiry(inquiryId)
      .then(setInquiry)
      .catch((err) => setLoadError(err instanceof Error ? err.message : "문의를 불러올 수 없습니다."));
  }, [inquiryId]);

  if (loadError) {
    return (
      <div className="space-y-6">
        <p className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">
          {loadError}
        </p>
      </div>
    );
  }

  if (!inquiry) {
    return (
      <div className="flex items-center justify-center p-12 text-sm text-muted-foreground" role="status" aria-live="polite">
        불러오는 중...
      </div>
    );
  }

  const tabs = [
    { key: "info", label: "문의 정보", content: <TabErrorBoundary><InquiryInfoTab inquiryId={inquiryId} /></TabErrorBoundary> },
    { key: "answer", label: "답변", content: <TabErrorBoundary><InquiryAnswerTab inquiryId={inquiryId} inquiry={inquiry} /></TabErrorBoundary> },
    { key: "history", label: "이력", content: <TabErrorBoundary><InquiryHistoryTab inquiryId={inquiryId} inquiryQuestion={inquiry.question} /></TabErrorBoundary> },
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
        <Tabs tabs={tabs} defaultTab={defaultTab} />
      </div>
    </div>
  );
}
