"use client";

import { useState } from "react";
import {
  type AutoWorkflowResult,
  type ReviewIssue,
} from "@/lib/api/client";
import {
  labelApprovalDecision,
  labelReviewDecision,
  labelIssueSeverity,
  labelIssueCategory,
} from "@/lib/i18n/labels";
import { Badge } from "@/components/ui";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// ===== Exported utility functions =====

export type BadgeVariant = "info" | "success" | "warn" | "danger" | "neutral";

export function getSeverityIcon(severity: string): string {
  switch (severity) {
    case "CRITICAL": return "\u{1F534}";
    case "HIGH": return "\u{1F7E0}";
    case "MEDIUM": return "\u{1F7E1}";
    case "LOW": return "\u{1F535}";
    default: return "\u{26AA}";
  }
}

export function getSeverityBadgeVariant(severity: string): "danger" | "warn" | "info" | "neutral" {
  switch (severity) {
    case "CRITICAL": return "danger";
    case "HIGH": return "danger";
    case "MEDIUM": return "warn";
    case "LOW": return "info";
    default: return "neutral";
  }
}

export function getScoreBadgeVariant(score: number): "success" | "warn" | "danger" {
  if (score >= 90) return "success";
  if (score >= 70) return "warn";
  return "danger";
}

export function groupIssuesByCategory(issues: ReviewIssue[]): Record<string, ReviewIssue[]> {
  return issues.reduce<Record<string, ReviewIssue[]>>((acc, issue) => {
    const cat = issue.category;
    if (!acc[cat]) acc[cat] = [];
    acc[cat].push(issue);
    return acc;
  }, {});
}

// ===== Component =====

const SpinnerIcon = () => (
  <svg className="h-4 w-4 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" aria-hidden="true">
    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
  </svg>
);

interface WorkflowResultCardProps {
  workflowResult: AutoWorkflowResult;
  currentDraft?: string;
  showActions?: boolean;
  onSend?: () => void;
  onApprove?: () => void;
  onAdoptRevised?: () => void;
  onRegenerate?: () => void;
  onManualModeToggle?: () => void;
  manualMode?: boolean;
  actionLoading?: boolean;
}

export default function WorkflowResultCard({
  workflowResult,
  currentDraft,
  showActions = true,
  onSend,
  onApprove,
  onAdoptRevised,
  onRegenerate,
  onManualModeToggle,
  manualMode,
  actionLoading,
}: WorkflowResultCardProps) {
  const [reviewDetailExpanded, setReviewDetailExpanded] = useState(!showActions);
  const [revisedDraftExpanded, setRevisedDraftExpanded] = useState(false);

  return (
    <div className="space-y-4">
      {/* Case A: AUTO_APPROVED */}
      {workflowResult.approval.decision === "AUTO_APPROVED" && (
        <div className="rounded-xl border border-emerald-300 bg-emerald-50 p-5 dark:border-emerald-800 dark:bg-emerald-950" role="status">
          <div className="flex items-center gap-3">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-emerald-600 dark:bg-emerald-900 dark:text-emerald-400" aria-hidden="true">
              <svg className="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
              </svg>
            </span>
            <div className="flex-1">
              <p className="font-semibold text-emerald-800 dark:text-emerald-200">
                AI 리뷰 통과 ({workflowResult.review.score}점) &rarr; 자동 승인 완료
              </p>
              <p className="text-sm text-emerald-700 dark:text-emerald-300">{workflowResult.summary}</p>
            </div>
            <Badge variant="success">{workflowResult.review.score}점</Badge>
          </div>

          {/* Gate Results Summary */}
          <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {workflowResult.approval.gateResults.map((gate) => (
              <div key={gate.gate} className="flex items-center gap-1.5 text-xs text-emerald-700 dark:text-emerald-300">
                <span aria-hidden="true">{gate.passed ? "\u2705" : "\u274C"}</span>
                <span>{gate.gate}</span>
              </div>
            ))}
          </div>

          <div className="mt-4 flex items-center gap-2">
            {showActions && onSend && (
              <Button size="sm" onClick={onSend} disabled={actionLoading}>
                {actionLoading && <SpinnerIcon />}
                발송하기
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => setReviewDetailExpanded(!reviewDetailExpanded)}
            >
              {reviewDetailExpanded ? "상세 리뷰 닫기" : "상세 리뷰 보기"}
            </Button>
          </div>
        </div>
      )}

      {/* Case B: ESCALATED */}
      {workflowResult.approval.decision === "ESCALATED" && (
        <div className="rounded-xl border border-amber-300 bg-amber-50 p-5 dark:border-amber-800 dark:bg-amber-950" role="alert">
          <div className="flex items-center gap-3">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-amber-100 text-amber-600 dark:bg-amber-900 dark:text-amber-400" aria-hidden="true">
              <svg className="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z" />
              </svg>
            </span>
            <div className="flex-1">
              <p className="font-semibold text-amber-800 dark:text-amber-200">
                {labelApprovalDecision("ESCALATED")}
              </p>
              <p className="text-sm text-amber-700 dark:text-amber-300">{workflowResult.approval.reason}</p>
            </div>
            <Badge variant="warn">{workflowResult.review.score}점</Badge>
          </div>

          {/* Failed Gates */}
          <div className="mt-4 space-y-2">
            {workflowResult.approval.gateResults.filter((g) => !g.passed).map((gate) => (
              <div key={gate.gate} className="flex items-center gap-2 rounded-lg border border-amber-200 bg-white/60 px-3 py-2 text-sm dark:border-amber-800 dark:bg-amber-950/50">
                <span aria-hidden="true">{"\u274C"}</span>
                <span className="font-medium">{gate.gate}</span>
                <span className="text-muted-foreground">실제: {gate.actualValue} / 기준: {gate.threshold}</span>
              </div>
            ))}
          </div>

          <div className="mt-4 flex items-center gap-2">
            {showActions && onApprove && (
              <Button size="sm" onClick={onApprove} disabled={actionLoading}>
                {actionLoading && <SpinnerIcon />}
                검토 후 승인
              </Button>
            )}
            {showActions && onRegenerate && (
              <Button
                variant="outline"
                size="sm"
                onClick={onRegenerate}
                disabled={actionLoading}
              >
                거부하고 재생성
              </Button>
            )}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setReviewDetailExpanded(!reviewDetailExpanded)}
            >
              {reviewDetailExpanded ? "상세 리뷰 닫기" : "상세 리뷰 보기"}
            </Button>
          </div>
        </div>
      )}

      {/* Case C: REJECTED */}
      {workflowResult.approval.decision === "REJECTED" && (
        <div className="rounded-xl border border-red-300 bg-red-50 p-5 dark:border-red-800 dark:bg-red-950" role="alert">
          <div className="flex items-center gap-3">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-red-100 text-red-600 dark:bg-red-900 dark:text-red-400" aria-hidden="true">
              <svg className="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
              </svg>
            </span>
            <div className="flex-1">
              <p className="font-semibold text-red-800 dark:text-red-200">
                답변 품질이 기준 미달입니다
              </p>
              <p className="text-sm text-red-700 dark:text-red-300">{workflowResult.approval.reason}</p>
            </div>
            <Badge variant="danger">{workflowResult.review.score}점</Badge>
          </div>

          {/* Issues Summary */}
          <div className="mt-4 space-y-1.5">
            {workflowResult.review.issues.slice(0, 5).map((issue, idx) => (
              <div key={idx} className="flex items-start gap-2 text-sm text-red-700 dark:text-red-300">
                <span className="shrink-0" aria-hidden="true">{getSeverityIcon(issue.severity)}</span>
                <span>
                  <Badge variant={getSeverityBadgeVariant(issue.severity)} className="mr-1.5">
                    {labelIssueSeverity(issue.severity)}
                  </Badge>
                  {issue.description}
                </span>
              </div>
            ))}
          </div>

          <div className="mt-4 flex items-center gap-2">
            {showActions && workflowResult.review.revisedDraft && onAdoptRevised && (
              <Button size="sm" onClick={onAdoptRevised}>
                수정안 채택
              </Button>
            )}
            {showActions && onRegenerate && (
              <Button
                variant="outline"
                size="sm"
                onClick={onRegenerate}
                disabled={actionLoading}
              >
                {actionLoading && <SpinnerIcon />}
                수정안으로 재생성
              </Button>
            )}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setReviewDetailExpanded(!reviewDetailExpanded)}
            >
              {reviewDetailExpanded ? "상세 리뷰 닫기" : "상세 리뷰 보기"}
            </Button>
          </div>
        </div>
      )}

      {/* Review Detail Section (Collapsible) */}
      {reviewDetailExpanded && (
        <div className="rounded-xl border bg-card p-5 shadow-sm space-y-4">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-semibold">AI 리뷰 상세</h4>
            <div className="flex items-center gap-2">
              <Badge variant={getScoreBadgeVariant(workflowResult.review.score)}>
                {workflowResult.review.score}점
              </Badge>
              <Badge variant={
                workflowResult.review.decision === "PASS" ? "success" :
                workflowResult.review.decision === "REVISE" ? "warn" : "danger"
              }>
                {labelReviewDecision(workflowResult.review.decision)}
              </Badge>
            </div>
          </div>

          <p className="text-sm text-muted-foreground">{workflowResult.review.summary}</p>

          {/* Issues grouped by category */}
          {workflowResult.review.issues.length > 0 && (
            <div className="space-y-3">
              <h5 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">발견된 이슈</h5>
              {Object.entries(groupIssuesByCategory(workflowResult.review.issues)).map(([category, issues]) => (
                <div key={category} className="space-y-2">
                  <p className="text-xs font-medium text-muted-foreground">
                    {labelIssueCategory(category)} ({issues.length})
                  </p>
                  {issues.map((issue, idx) => (
                    <div key={idx} className="rounded-lg border border-border/50 bg-muted/20 p-3">
                      <div className="flex items-center gap-2">
                        <span className="shrink-0" aria-hidden="true">{getSeverityIcon(issue.severity)}</span>
                        <Badge variant={getSeverityBadgeVariant(issue.severity)}>
                          {labelIssueSeverity(issue.severity)}
                        </Badge>
                        <span className="text-sm">{issue.description}</span>
                      </div>
                      {issue.suggestion && (
                        <p className="mt-1.5 ml-7 text-xs text-muted-foreground">
                          제안: {issue.suggestion}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              ))}
            </div>
          )}

          {/* Revised Draft Toggle */}
          {workflowResult.review.decision === "REVISE" && workflowResult.review.revisedDraft && (
            <div className="space-y-3">
              <button
                onClick={() => setRevisedDraftExpanded(!revisedDraftExpanded)}
                className="flex items-center gap-2 text-sm font-medium text-primary hover:underline"
                aria-expanded={revisedDraftExpanded}
              >
                <svg
                  className={cn("h-4 w-4 transition-transform", revisedDraftExpanded && "rotate-90")}
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  strokeWidth={2}
                  stroke="currentColor"
                  aria-hidden="true"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" />
                </svg>
                수정안 보기
              </button>

              {revisedDraftExpanded && (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <div className="space-y-2">
                    <p className="text-xs font-medium text-muted-foreground">원본</p>
                    <div className="rounded-lg border bg-muted/20 p-3 text-sm leading-relaxed whitespace-pre-wrap max-h-64 overflow-y-auto">
                      {currentDraft ?? "(원본 없음)"}
                    </div>
                  </div>
                  <div className="space-y-2">
                    <p className="text-xs font-medium text-muted-foreground">수정안</p>
                    <div className="rounded-lg border border-emerald-200 bg-emerald-50/50 p-3 text-sm leading-relaxed whitespace-pre-wrap max-h-64 overflow-y-auto dark:border-emerald-800 dark:bg-emerald-950/30">
                      {workflowResult.review.revisedDraft}
                    </div>
                  </div>
                </div>
              )}

              {showActions && (
                <div className="flex items-center gap-2">
                  {onAdoptRevised && (
                    <Button size="sm" onClick={onAdoptRevised}>
                      수정안 채택
                    </Button>
                  )}
                  {onApprove && (
                    <Button variant="outline" size="sm" onClick={onApprove} disabled={actionLoading}>
                      원본 유지하고 승인 요청
                    </Button>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Gate Results Card */}
          <div className="space-y-3">
            <h5 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">승인 게이트 결과</h5>
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              {workflowResult.approval.gateResults.map((gate) => (
                <div
                  key={gate.gate}
                  className={cn(
                    "rounded-lg border p-3 text-center",
                    gate.passed
                      ? "border-emerald-200 bg-emerald-50/50 dark:border-emerald-800 dark:bg-emerald-950/30"
                      : "border-red-200 bg-red-50/50 dark:border-red-800 dark:bg-red-950/30"
                  )}
                >
                  <span className="text-lg" aria-hidden="true">{gate.passed ? "\u2705" : "\u274C"}</span>
                  <p className="mt-1 text-xs font-medium">{gate.gate}</p>
                  <p className="text-xs text-muted-foreground">
                    {gate.actualValue} / {gate.threshold}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Manual fallback after workflow result */}
      {showActions && workflowResult.approval.decision !== "AUTO_APPROVED" && onManualModeToggle && (
        <button
          onClick={onManualModeToggle}
          className="text-xs text-muted-foreground hover:text-foreground hover:underline transition-colors"
        >
          {manualMode ? "자동 모드로 전환" : "수동 모드로 전환"}
        </button>
      )}
    </div>
  );
}
