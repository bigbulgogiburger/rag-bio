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
        <p className="rounded-xl border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">
          {loadError}
        </p>
      </div>
    );
  }

  if (!inquiry) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-3">
          <div className="h-8 w-16 rounded-lg bg-muted animate-pulse" />
          <div className="h-6 w-px bg-border" />
          <div className="space-y-2 flex-1">
            <div className="h-7 w-32 rounded bg-muted animate-pulse" />
            <div className="h-4 w-48 rounded bg-muted animate-pulse" />
          </div>
        </div>
        <div className="rounded-2xl border border-border/50 bg-card shadow-brand p-6">
          <div className="flex gap-2 mb-6">
            {[1, 2, 3].map(i => <div key={i} className="h-10 flex-1 rounded-lg bg-muted animate-pulse" />)}
          </div>
          <div className="space-y-4">
            {[1, 2, 3, 4].map(i => <div key={i} className="h-16 rounded-xl bg-muted animate-pulse" />)}
          </div>
        </div>
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
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" onClick={() => router.push("/inquiries")} className="rounded-lg">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true" className="mr-1">
            <path d="M10 3L5 8L10 13" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          목록
        </Button>
        <div className="h-6 w-px bg-border" aria-hidden="true" />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h2 className="text-xl sm:text-2xl font-bold tracking-tight truncate">문의 상세</h2>
            <button
              onClick={() => navigator.clipboard.writeText(inquiryId)}
              className="shrink-0 rounded-md bg-muted px-2 py-0.5 text-xs font-mono text-muted-foreground hover:bg-primary/10 hover:text-primary transition-colors"
              title="ID 복사"
            >
              #{inquiryId.slice(0, 8)}
            </button>
          </div>
          {inquiry?.question && (
            <p className="text-xs text-muted-foreground mt-0.5 truncate max-w-md">
              {inquiry.question.slice(0, 60)}{inquiry.question.length > 60 ? "..." : ""}
            </p>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-border/50 bg-card shadow-brand p-4 sm:p-6">
        <Tabs
          tabs={tabs}
          defaultTab={defaultTab}
          listClassName="sticky top-[4.5rem] z-10 bg-card/90 backdrop-blur-sm pt-2 pb-1 -mx-4 px-4 sm:-mx-6 sm:px-6 border-b border-border/30"
        />
      </div>
    </div>
  );
}
