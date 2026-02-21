"use client";

import { useEffect, useState, type ReactNode } from "react";
import dynamic from "next/dynamic";
import {
  listAnswerDraftHistory,
  getDocumentDownloadUrl,
  getDocumentPagesUrl,
  type AnswerDraftResult,
  type AnalyzeEvidenceItem,
} from "@/lib/api/client";
import {
  labelAnswerStatus,
  labelVerdict,
  labelChannel,
  labelTone,
} from "@/lib/i18n/labels";
import { DataTable, Badge, EmptyState, Skeleton } from "@/components/ui";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const PdfViewer = dynamic(() => import("@/components/ui/PdfViewer"), {
  ssr: false,
  loading: () => <div className="flex items-center justify-center p-8 text-sm text-muted-foreground" role="status" aria-live="polite">PDF 뷰어 로딩 중...</div>,
});

interface CitationView {
  chunkId: string;
  score: number | null;
  documentId: string | null;
  fileName: string | null;
  pageStart: number | null;
  pageEnd: number | null;
}

function parseCitation(raw: string): CitationView {
  const chunkMatch = raw.match(/chunk=([^\s]+)/);
  const scoreMatch = raw.match(/score=([0-9.]+)/);
  const docMatch = raw.match(/documentId=([^\s]+)/);
  // fileName은 공백을 포함할 수 있으므로, 다음 key= 패턴이나 문자열 끝까지 매칭
  const fileMatch = raw.match(/fileName=(.+?)(?=\s+\w+=|$)/);
  const psMatch = raw.match(/pageStart=(\d+)/);
  const peMatch = raw.match(/pageEnd=(\d+)/);
  return {
    chunkId: chunkMatch?.[1] ?? raw,
    score: scoreMatch ? Number(scoreMatch[1]) : null,
    documentId: docMatch?.[1] ?? null,
    fileName: fileMatch?.[1]?.trim() ?? null,
    pageStart: psMatch ? Number(psMatch[1]) : null,
    pageEnd: peMatch ? Number(peMatch[1]) : null,
  };
}

function formatCitationLabel(c: CitationView): string {
  if (c.fileName) {
    const pageStr =
      c.pageStart != null
        ? c.pageEnd != null && c.pageEnd !== c.pageStart
          ? ` (p.${c.pageStart}-${c.pageEnd})`
          : ` (p.${c.pageStart})`
        : "";
    return `${c.fileName}${pageStr}`;
  }
  if (c.documentId) {
    return `문서 ${c.documentId.slice(0, 8)}`;
  }
  return `청크 ${c.chunkId.slice(0, 8)}`;
}

function isPdf(fileName: string | null): boolean {
  return fileName != null && fileName.toLowerCase().endsWith(".pdf");
}

function evidenceToCitationView(ev: AnalyzeEvidenceItem): CitationView {
  return {
    chunkId: ev.chunkId,
    score: ev.score,
    documentId: ev.documentId,
    fileName: ev.fileName ?? null,
    pageStart: ev.pageStart ?? null,
    pageEnd: ev.pageEnd ?? null,
  };
}

interface InquiryHistoryTabProps {
  inquiryId: string;
}

export default function InquiryHistoryTab({ inquiryId }: InquiryHistoryTabProps) {
  const [history, setHistory] = useState<AnswerDraftResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<AnswerDraftResult | null>(null);
  const [selectedEvidence, setSelectedEvidence] = useState<CitationView | null>(null);

  useEffect(() => {
    fetchHistory();
  }, [inquiryId]);

  // Reset evidence preview when switching versions
  useEffect(() => {
    setSelectedEvidence(null);
  }, [selected]);

  const fetchHistory = async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await listAnswerDraftHistory(inquiryId);
      setHistory(data);
      if (data.length > 0) setSelected(data[0]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "버전 이력 조회 중 오류가 발생했습니다.");
    } finally {
      setLoading(false);
    }
  };

  // Build evidence list from selected version
  const evidenceItems: CitationView[] = selected
    ? selected.evidences
      ? selected.evidences.map(evidenceToCitationView)
      : selected.citations.map(parseCitation)
    : [];

  // Render answer body with clickable citation links
  const renderDraftWithCitations = (text: string): ReactNode => {
    const citationRegex = /\(([^,]+\.pdf),\s*p\.(\d+)(?:-(\d+))?\)/gi;
    const parts: ReactNode[] = [];
    let lastIndex = 0;
    let match: RegExpExecArray | null;

    while ((match = citationRegex.exec(text)) !== null) {
      if (match.index > lastIndex) {
        parts.push(text.slice(lastIndex, match.index));
      }

      const matchedFileName = match[1].trim();
      const pageStart = parseInt(match[2], 10);
      const pageEnd = match[3] ? parseInt(match[3], 10) : pageStart;
      const fullMatch = match[0];

      const matchingEvidence = evidenceItems.find((ev) => {
        if (!ev.fileName) return false;
        const nameMatch = ev.fileName.toLowerCase() === matchedFileName.toLowerCase();
        if (!nameMatch) return false;
        if (ev.pageStart == null) return true;
        return ev.pageStart === pageStart || (ev.pageStart <= pageStart && (ev.pageEnd ?? ev.pageStart) >= pageEnd);
      });

      if (matchingEvidence) {
        parts.push(
          <button
            key={`citation-${match.index}`}
            type="button"
            className="text-primary underline cursor-pointer hover:text-primary/80"
            onClick={() => setSelectedEvidence(matchingEvidence)}
          >
            {fullMatch}
          </button>
        );
      } else {
        parts.push(fullMatch);
      }

      lastIndex = match.index + fullMatch.length;
    }

    if (lastIndex < text.length) {
      parts.push(text.slice(lastIndex));
    }

    return parts.length > 0 ? parts : text;
  };

  const getAnswerStatusBadgeVariant = (status: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (["SENT", "APPROVED"].includes(status)) return "success";
    if (status === "REVIEWED") return "warn";
    return "info";
  };

  const getVerdictBadgeVariant = (verdict: string): "info" | "success" | "warn" | "danger" | "neutral" => {
    if (verdict === "SUPPORTED") return "success";
    if (verdict === "NOT_SUPPORTED") return "danger";
    if (verdict === "CONDITIONAL") return "warn";
    return "info";
  };

  const columns = [
    {
      key: "version",
      header: "버전",
      render: (item: AnswerDraftResult) => `v${item.version}`,
      width: "80px",
    },
    {
      key: "status",
      header: "상태",
      render: (item: AnswerDraftResult) => (
        <Badge variant={getAnswerStatusBadgeVariant(item.status)}>
          {labelAnswerStatus(item.status)}
        </Badge>
      ),
      width: "120px",
    },
    {
      key: "verdict",
      header: "판정",
      render: (item: AnswerDraftResult) => (
        <Badge variant={getVerdictBadgeVariant(item.verdict)}>
          {labelVerdict(item.verdict)}
        </Badge>
      ),
      width: "120px",
    },
    {
      key: "confidence",
      header: "신뢰도",
      render: (item: AnswerDraftResult) => item.confidence,
      width: "100px",
    },
    {
      key: "channel",
      header: "채널",
      render: (item: AnswerDraftResult) => labelChannel(item.channel),
      width: "100px",
    },
    {
      key: "tone",
      header: "톤",
      render: (item: AnswerDraftResult) => labelTone(item.tone),
      width: "100px",
    },
  ];

  if (loading) {
    return (
      <div className="space-y-6" role="status" aria-label="버전 이력 로딩 중">
        <Skeleton className="h-12" />
        <Skeleton className="h-[200px]" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border bg-card p-6 shadow-sm">
        <p className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive" role="alert">
          {error}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-base font-semibold">
            답변 버전 이력 ({history.length}건)
          </h3>
          <Button variant="ghost" size="sm" onClick={fetchHistory} disabled={loading}>
            새로고침
          </Button>
        </div>

        <hr className="border-t border-border" />

        <DataTable
          columns={columns}
          data={history}
          onRowClick={(item) => setSelected(item)}
          emptyMessage="생성된 답변 버전이 없습니다"
        />
      </div>

      {selected && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_400px]">
          {/* Left: Version detail */}
          <div>
            <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
              <h3 className="text-base font-semibold">v{selected.version} 버전 상세</h3>

              <hr className="border-t border-border" />

              {/* Version summary metrics */}
              <div className="grid grid-cols-3 gap-4">
                <div className="rounded-xl border bg-card p-5 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">버전</p>
                  <p className="text-2xl font-bold tracking-tight text-foreground">v{selected.version}</p>
                </div>
                <div className="rounded-xl border bg-card p-5 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">상태</p>
                  <div className="text-lg font-bold tracking-tight text-foreground">
                    <Badge variant={getAnswerStatusBadgeVariant(selected.status)}>
                      {labelAnswerStatus(selected.status)}
                    </Badge>
                  </div>
                </div>
                <div className="rounded-xl border bg-card p-5 shadow-sm">
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">판정 / 신뢰도</p>
                  <div className="text-lg font-bold tracking-tight text-foreground">
                    <Badge variant={getVerdictBadgeVariant(selected.verdict)}>
                      {labelVerdict(selected.verdict)}
                    </Badge>
                    <span className="ml-2 text-sm text-muted-foreground">
                      ({selected.confidence})
                    </span>
                  </div>
                </div>
              </div>

              <div className="space-y-2 text-sm">
                <div>
                  <b>채널:</b> {labelChannel(selected.channel)} | <b>톤:</b> {labelTone(selected.tone)}
                </div>
              </div>

              {/* Draft content */}
              {selected.draft && (
                <>
                  <hr className="border-t border-border" />
                  <h4 className="text-base font-semibold">답변 초안</h4>
                  <div className="rounded-lg border border-border/50 bg-muted/30 p-4 whitespace-pre-wrap text-sm leading-relaxed">
                    {renderDraftWithCitations(selected.draft)}
                  </div>
                </>
              )}

              {/* Citations - clickable */}
              {selected.citations && selected.citations.length > 0 && (
                <>
                  <hr className="border-t border-border" />
                  <h4 className="text-base font-semibold">참조 자료 ({selected.citations.length}건)</h4>
                  <div className="space-y-1">
                    {selected.citations.map((cite, i) => {
                      const parsed = parseCitation(cite);
                      const isSelected =
                        selectedEvidence?.chunkId === parsed.chunkId;
                      return (
                        <div
                          key={`${parsed.chunkId}-${i}`}
                          className={cn(
                            "rounded-lg border border-border/50 bg-muted/30 p-4 cursor-pointer transition-colors hover:border-primary/30 hover:bg-primary/5",
                            isSelected && "border-primary/50 bg-primary/5 ring-1 ring-primary/20"
                          )}
                          onClick={() => setSelectedEvidence(parsed)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter" || e.key === " ") {
                              e.preventDefault();
                              setSelectedEvidence(parsed);
                            }
                          }}
                          role="button"
                          tabIndex={0}
                          aria-label={`참조 자료 미리보기: ${formatCitationLabel(parsed)}`}
                          aria-pressed={isSelected}
                        >
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-medium">
                              {formatCitationLabel(parsed)}
                            </span>
                            {parsed.score != null && (
                              <span className="text-xs text-muted-foreground">
                                유사도 {(parsed.score * 100).toFixed(1)}%
                              </span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </>
              )}

              {/* Workflow trail */}
              {(selected.reviewedBy || selected.approvedBy || selected.sentBy) && (
                <>
                  <hr className="border-t border-border" />
                  <h4 className="text-base font-semibold">워크플로우 이력</h4>
                  <div className="space-y-4">
                    {selected.reviewedBy && (
                      <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                        <span className="mr-2"><Badge variant="info">리뷰</Badge></span>
                        {selected.reviewedBy}
                        {selected.reviewComment && (
                          <span className="text-sm text-muted-foreground"> - {selected.reviewComment}</span>
                        )}
                      </div>
                    )}
                    {selected.approvedBy && (
                      <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                        <span className="mr-2"><Badge variant="success">승인</Badge></span>
                        {selected.approvedBy}
                        {selected.approveComment && (
                          <span className="text-sm text-muted-foreground"> - {selected.approveComment}</span>
                        )}
                      </div>
                    )}
                    {selected.sentBy && (
                      <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                        <span className="mr-2"><Badge variant="success">발송</Badge></span>
                        {selected.sentBy} | {selected.sendChannel}
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>
          </div>

          {/* Right: Document Preview */}
          <div className="lg:sticky lg:top-20 lg:self-start">
            <div className="rounded-xl border bg-card p-6 shadow-sm space-y-4">
              <h4 className="text-base font-semibold">문서 미리보기</h4>
              <hr className="border-t border-border" />
              {selectedEvidence && selectedEvidence.documentId ? (
                isPdf(selectedEvidence.fileName) ? (
                  <PdfViewer
                    url={
                      selectedEvidence.pageStart != null
                        ? getDocumentPagesUrl(
                            selectedEvidence.documentId,
                            selectedEvidence.pageStart,
                            selectedEvidence.pageEnd ?? selectedEvidence.pageStart
                          )
                        : getDocumentDownloadUrl(selectedEvidence.documentId)
                    }
                    initialPage={1}
                    downloadUrl={getDocumentDownloadUrl(selectedEvidence.documentId)}
                    pagesDownloadUrl={
                      selectedEvidence.pageStart != null
                        ? getDocumentPagesUrl(
                            selectedEvidence.documentId,
                            selectedEvidence.pageStart,
                            selectedEvidence.pageEnd ?? selectedEvidence.pageStart
                          ) + "&download=true"
                        : undefined
                    }
                    fileName={selectedEvidence.fileName ?? undefined}
                  />
                ) : (
                  <div className="flex flex-col items-center space-y-6 p-6">
                    <p className="text-center text-sm text-muted-foreground">
                      {selectedEvidence.fileName ?? "문서"} 파일은 PDF가 아니므로 미리보기가 지원되지 않습니다.
                    </p>
                    <Button asChild>
                      <a
                        href={getDocumentDownloadUrl(selectedEvidence.documentId)}
                        download
                      >
                        원본 파일 다운로드
                      </a>
                    </Button>
                  </div>
                )
              ) : (
                <EmptyState title="참조 자료를 클릭하면 문서 미리보기가 표시됩니다" />
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
