"use client";

import { useState, useEffect, Fragment } from "react";
import { cn } from "@/lib/utils";
import type { DraftStepData } from "@/hooks/useInquiryEvents";

/* ── Display step definitions ────────────────────────────────── */

const DISPLAY_STEPS = [
  { key: "DECOMPOSE", label: "질문 분석" },
  { key: "RETRIEVE", label: "자료 검색" },
  { key: "VERIFY", label: "근거 검증" },
  { key: "COMPOSE", label: "답변 작성" },
  { key: "CRITIC", label: "팩트 체크" },
  { key: "SELF_REVIEW", label: "최종 점검" },
] as const;

const STEP_MESSAGES: Record<string, string> = {
  DECOMPOSE: "질문을 꼼꼼히 분석하고 있어요",
  RETRIEVE: "관련 문서에서 내용을 찾고 있어요",
  ADAPTIVE_RETRIEVE: "더 넓은 범위에서 단서를 찾는 중이에요",
  MULTI_HOP: "여러 문서를 교차 확인하고 있어요",
  VERIFY: "찾은 근거가 정확한지 확인하고 있어요",
  COMPOSE: "검증된 근거로 답변을 작성하고 있어요",
  CRITIC: "답변의 사실관계를 점검하고 있어요",
  SELF_REVIEW: "마지막으로 깔끔하게 다듬고 있어요",
};

const WAIT_MESSAGES = [
  "조금만 기다려 주세요~",
  "정확한 답변을 위해 노력 중이에요",
  "꼼꼼하게 확인하고 있으니 잠시만요~",
  "좋은 답변을 만들고 있어요",
  "거의 다 됐어요, 조금만 더~",
];

function toDisplayKey(step: string): string {
  if (step === "ADAPTIVE_RETRIEVE" || step === "MULTI_HOP") return "RETRIEVE";
  return step;
}

/* ── Component ───────────────────────────────────────────────── */

interface PipelineProgressProps {
  steps: DraftStepData[];
  isGenerating: boolean;
  startedAt?: string;
  connectionStatus?: string;
  onRetry?: () => void;
  error?: string;
  streamingTokenCount?: number;
  isStreaming?: boolean;
}

export default function PipelineProgress({ steps, isGenerating, startedAt, connectionStatus, onRetry, error, streamingTokenCount, isStreaming }: PipelineProgressProps) {
  const [waitIdx, setWaitIdx] = useState(0);
  const [dots, setDots] = useState("");
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (!isGenerating) return;
    const id = setInterval(() => setWaitIdx((i) => (i + 1) % WAIT_MESSAGES.length), 6000);
    return () => clearInterval(id);
  }, [isGenerating]);

  useEffect(() => {
    if (!isGenerating) return;
    const id = setInterval(() => setDots((d) => (d.length >= 3 ? "" : d + ".")), 500);
    return () => clearInterval(id);
  }, [isGenerating]);

  // Elapsed time counter
  useEffect(() => {
    if (!startedAt || !isGenerating) {
      setElapsed(0);
      return;
    }
    const startMs = new Date(startedAt).getTime();
    const update = () => setElapsed(Math.floor((Date.now() - startMs) / 1000));
    update();
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, [startedAt, isGenerating]);

  // Error state (pipeline failed)
  if (error) {
    return (
      <div className="rounded-2xl border-2 border-dashed border-destructive/30 bg-destructive/5 px-4 py-5 sm:px-6">
        <div className="flex items-center gap-3">
          <svg className="h-5 w-5 text-destructive shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
          </svg>
          <span className="text-sm text-destructive">{error}</span>
          {onRetry && (
            <button
              onClick={onRetry}
              className="ml-auto shrink-0 rounded-md border border-destructive/30 bg-background px-3 py-1.5 text-xs font-medium text-destructive hover:bg-destructive/10 transition-colors"
            >
              재시도
            </button>
          )}
        </div>
      </div>
    );
  }

  if (!isGenerating || steps.length === 0) return null;

  // active step = last step with IN_PROGRESS or RETRY
  const activeRaw = [...steps].reverse().find((s) => s.status === "IN_PROGRESS" || s.status === "RETRY");
  const activeKey = activeRaw?.step || steps[steps.length - 1]?.step || "";
  const desc = activeRaw?.message || STEP_MESSAGES[activeKey] || "처리 중이에요";

  const stepStatus = (displayKey: string) => {
    const related = steps.filter((s) => toDisplayKey(s.step) === displayKey);
    if (related.length === 0) return "pending" as const;
    if (related.some((s) => s.status === "IN_PROGRESS" || s.status === "RETRY")) return "active" as const;
    if (related.some((s) => s.status === "COMPLETED")) return "completed" as const;
    if (related.some((s) => s.status === "FAILED")) return "failed" as const;
    return "pending" as const;
  };

  return (
    <div
      className="relative overflow-hidden rounded-2xl border-2 border-dashed border-amber-300/50 dark:border-amber-600/30 bg-gradient-to-br from-amber-50/80 via-orange-50/50 to-rose-50/30 dark:from-amber-950/30 dark:via-orange-950/20 dark:to-rose-950/10 px-4 py-5 sm:px-6"
      role="status"
      aria-label="답변 생성 진행 중"
    >
      {/* retro dot-grid overlay */}
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.04] dark:opacity-[0.06]"
        style={{
          backgroundImage: "radial-gradient(circle, currentColor 1px, transparent 1px)",
          backgroundSize: "14px 14px",
        }}
      />

      <div className="relative flex flex-col items-center gap-4">
        {/* ── Cat + Speech Bubble ── */}
        <div className="flex items-center gap-3 sm:gap-4">
          <PixelDog />

          <div className="relative rounded-lg border-2 border-amber-300/70 dark:border-amber-700/50 bg-white/90 dark:bg-white/10 px-4 py-3 shadow-sm max-w-[240px] sm:max-w-[280px]" style={{ imageRendering: "pixelated" }}>
            {/* pixel bubble tail */}
            <div className="absolute -left-[6px] top-5 h-3 w-3 rotate-45 border-l-2 border-b-2 border-amber-300/70 dark:border-amber-700/50 bg-white/90 dark:bg-white/10" />
            <p className="text-sm font-medium text-foreground/80 leading-snug">
              {desc}{dots}
            </p>
            <p
              className="mt-1.5 text-[11px] text-muted-foreground/70"
              key={waitIdx}
              style={{ animation: "pipeline-fade-in 0.5s ease" }}
            >
              {WAIT_MESSAGES[waitIdx]}
            </p>
          </div>
        </div>

        {/* ── Elapsed time + connection status ── */}
        <div className="flex items-center gap-3 text-[11px] text-muted-foreground/70">
          {elapsed > 0 && (
            <span>{Math.floor(elapsed / 60)}분 {elapsed % 60}초 경과</span>
          )}
          {connectionStatus === "connecting" && (
            <span className="animate-pulse">진행 상태에 다시 연결하고 있어요...</span>
          )}
        </div>

        {/* ── Step Progress ── */}
        <div className="flex items-center">
          {DISPLAY_STEPS.map((step, i) => {
            const st = stepStatus(step.key);
            return (
              <Fragment key={step.key}>
                {i > 0 && (
                  <div
                    className={cn(
                      "h-[2px] w-3 sm:w-5 rounded-full transition-colors duration-500",
                      st === "completed" || st === "active"
                        ? "bg-emerald-400/70 dark:bg-emerald-500/50"
                        : "bg-border/60",
                    )}
                  />
                )}
                <div className="flex flex-col items-center gap-1">
                  <div
                    className={cn(
                      "flex h-6 w-6 items-center justify-center rounded-full text-[10px] font-bold transition-all duration-300",
                      st === "completed" && "bg-emerald-500 text-white shadow-sm shadow-emerald-500/25",
                      st === "active" && "bg-primary text-white shadow-md shadow-primary/30",
                      st === "failed" && "bg-destructive/80 text-white",
                      st === "pending" && "bg-muted/80 text-muted-foreground border border-border/60",
                    )}
                    style={st === "active" ? { animation: "pipeline-pulse-dot 1.8s ease-in-out infinite" } : undefined}
                  >
                    {st === "completed" ? (
                      <svg className="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round"><path d="m5 12 5 5L20 7" /></svg>
                    ) : st === "failed" ? (
                      <svg className="h-3 w-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18M6 6l12 12" /></svg>
                    ) : (
                      <span>{i + 1}</span>
                    )}
                  </div>
                  <span
                    className={cn(
                      "text-[9px] sm:text-[10px] leading-tight whitespace-nowrap transition-colors",
                      st === "active" && "font-semibold text-primary",
                      st === "completed" && "text-emerald-600 dark:text-emerald-400",
                      st === "pending" && "text-muted-foreground/60",
                      st === "failed" && "text-destructive",
                    )}
                  >
                    {step.key === "COMPOSE" && st === "active" && isStreaming
                      ? `실시간 생성 중${streamingTokenCount ? ` · ${streamingTokenCount}토큰` : ''}`
                      : step.label}
                  </span>
                </div>
              </Fragment>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/* ── Pixel Dog (retro pixel-art mascot) ──────────────────────── */

const PX: Record<string, string> = {
  D: "#3D2414", // dark brown (ears, paws)
  d: "#6B4226", // mid-dark (ear inner)
  m: "#C4956A", // medium brown (body)
  l: "#DEB887", // light tan (muzzle, belly)
  B: "#1A1A2E", // black (eyes)
  W: "#FFFFFF", // white (eye highlight)
};

/* 16×16 pixel grid — '#' = eye slot, 'T' = tongue slot */
const DOG_GRID = [
  "...dd......dd...", // 0  ear tips
  "..dddd....dddd..", // 1  ears
  "..ddddmmmmdddd..", // 2  ears merge into head
  "..mmmmmmmmmmmm..", // 3  head
  ".mmmmmmmmmmmmmm.", // 4  wider head
  ".mmm##mmmm##mmm.", // 5  eyes top
  ".mmm##mllm##mmm.", // 6  eyes bot + muzzle
  "..mmmllllllmmm..", // 7  wide muzzle
  "..mmmllDDllmmm..", // 8  nose
  "...mmlllTllmm...", // 9  tongue
  "....mmmllmmm....", // 10 chin
  "....mmmmmmmm....", // 11 neck
  "...mmllllllmm...", // 12 body
  "...mmmllllmmm...", // 13 lower body
  "..DDDmm..mmDDD..", // 14 paws
  "..DDDmm..mmDDD..", // 15 paw base
];

function PixelDog() {
  return (
    <div className="shrink-0" style={{ animation: "pipeline-bob 3s ease-in-out infinite" }}>
      <svg
        viewBox="0 0 16 16"
        width="80"
        height="80"
        shapeRendering="crispEdges"
        aria-hidden="true"
        className="drop-shadow-sm"
      >
        {/* Body pixels */}
        {DOG_GRID.map((row, y) =>
          row.split("").map((c, x) => {
            if (c === "." || c === "#" || c === "T") return null;
            return <rect key={`${x}-${y}`} x={x} y={y} width={1} height={1} fill={PX[c]} />;
          }),
        )}

        {/* Eyes — 2×2 black with white highlight (blinks via scaleY) */}
        <g style={{ animation: "pipeline-blink 4s ease-in-out infinite", transformOrigin: "8px 5.5px" }}>
          {/* Left eye */}
          <rect x={4} y={5} width={2} height={2} fill={PX.B} />
          <rect x={4} y={5} width={1} height={1} fill={PX.W} />
          {/* Right eye */}
          <rect x={10} y={5} width={2} height={2} fill={PX.B} />
          <rect x={10} y={5} width={1} height={1} fill={PX.W} />
        </g>

        {/* Tongue — tan base + pink overlay with panting animation */}
        <rect x={8} y={9} width={1} height={1} fill={PX.l} />
        <rect
          x={8}
          y={9}
          width={1}
          height={1}
          fill="#E8A0B0"
          style={{ animation: "pixel-pant 5s ease-in-out infinite" }}
        />

        {/* Blush (cheeks) */}
        <rect x={2} y={7} width={1} height={1} fill="#FF8A80" opacity={0.3} />
        <rect x={13} y={7} width={1} height={1} fill="#FF8A80" opacity={0.3} />
      </svg>
    </div>
  );
}
