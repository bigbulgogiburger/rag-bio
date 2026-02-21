"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import dynamic from "next/dynamic";
import { Document, Page, pdfjs } from "react-pdf";
import "react-pdf/dist/Page/AnnotationLayer.css";
import "react-pdf/dist/Page/TextLayer.css";
import { Button } from "@/components/ui/button";

pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;

function buildPagesFileName(baseName: string, pagesUrl: string): string {
  try {
    const params = new URL(pagesUrl, window.location.origin).searchParams;
    const from = params.get("from");
    const to = params.get("to");
    if (from && to) {
      const name = baseName.replace(/\.[^.]+$/, "");
      const ext = baseName.match(/\.[^.]+$/)?.[0] ?? ".pdf";
      return `${name}_p${from}-${to}${ext}`;
    }
  } catch {}
  return baseName;
}

function triggerBlobDownload(url: string, fallbackName: string) {
  fetch(url)
    .then((res) => {
      const disposition = res.headers.get("Content-Disposition");
      let name = fallbackName;
      if (disposition) {
        const match = disposition.match(/filename\*?=(?:UTF-8'')?([^;\s]+)/i);
        if (match) name = decodeURIComponent(match[1]);
      }
      return res.blob().then((blob) => ({ blob, name }));
    })
    .then(({ blob, name }) => {
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = name;
      document.body.appendChild(a);
      a.click();
      URL.revokeObjectURL(a.href);
      a.remove();
    })
    .catch(() => {
      window.open(url, "_blank");
    });
}

const PdfExpandModal = dynamic(() => import("@/components/ui/PdfExpandModal"), {
  ssr: false,
});

interface PdfViewerProps {
  url: string;
  initialPage?: number;
  downloadUrl?: string;
  pagesDownloadUrl?: string;
  fileName?: string;
}

export default function PdfViewer({
  url,
  initialPage = 1,
  downloadUrl,
  pagesDownloadUrl,
  fileName,
}: PdfViewerProps) {
  const [numPages, setNumPages] = useState<number>(0);
  const [currentPage, setCurrentPage] = useState(initialPage);
  const [loadError, setLoadError] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [pageWidth, setPageWidth] = useState(480);
  const [expandOpen, setExpandOpen] = useState(false);
  const [downloadMenuOpen, setDownloadMenuOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Reset state when document URL or initialPage changes
  useEffect(() => {
    setCurrentPage(initialPage);
    setNumPages(0);
    setLoadError(null);
  }, [url, initialPage]);

  useEffect(() => {
    if (!containerRef.current) return;
    const observer = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width;
      if (width) setPageWidth(Math.min(width - 16, 800));
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  // Close dropdown on outside click
  useEffect(() => {
    if (!downloadMenuOpen) return;
    const handleClick = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDownloadMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [downloadMenuOpen]);

  const onLoadSuccess = useCallback(({ numPages }: { numPages: number }) => {
    setNumPages(numPages);
    setLoadError(null);
  }, []);

  const onLoadError = useCallback(() => {
    setLoadError("PDF 파일을 불러올 수 없습니다.");
  }, []);

  if (loadError) {
    return (
      <div className="flex flex-col items-center space-y-4 p-6 text-center text-destructive" role="alert">
        <p>{loadError}</p>
        {downloadUrl && (
          <Button variant="outline" size="sm" asChild>
            <a href={downloadUrl} download>
              파일 직접 다운로드
            </a>
          </Button>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col overflow-hidden flex-1 min-h-0">
      <div className="flex items-center gap-1 px-2 py-1 border-b border-border bg-muted/50 shrink-0">
        <Button
          variant="outline"
          size="sm"
          onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
          disabled={currentPage <= 1}
          aria-label="이전 페이지"
        >
          이전
        </Button>
        <span className="text-sm text-muted-foreground min-w-[60px] text-center" aria-live="polite" aria-atomic="true">
          {currentPage} / {numPages || "..."}
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setCurrentPage((p) => Math.min(numPages, p + 1))}
          disabled={currentPage >= numPages}
          aria-label="다음 페이지"
        >
          다음
        </Button>

        <div className="flex items-center gap-1 ml-auto">
          {/* Expand Button */}
          <Button
            variant="outline"
            size="icon"
            className="h-8 w-8"
            onClick={() => setExpandOpen(true)}
            aria-label="확대 보기"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><polyline points="15 3 21 3 21 9"/><polyline points="9 21 3 21 3 15"/><line x1="21" y1="3" x2="14" y2="10"/><line x1="3" y1="21" x2="10" y2="14"/></svg>
          </Button>

          {/* Download */}
          {downloadUrl && (
            pagesDownloadUrl ? (
              <div className="relative" ref={dropdownRef}>
                <Button
                  size="sm"
                  onClick={() => setDownloadMenuOpen(!downloadMenuOpen)}
                  aria-haspopup="true"
                  aria-expanded={downloadMenuOpen}
                >
                  다운로드
                  <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>
                </Button>
                {downloadMenuOpen && (
                  <div className="absolute right-0 top-full mt-1 z-50 min-w-[180px] rounded-md border bg-popover p-1 shadow-md">
                    <button
                      className="flex w-full items-center rounded-sm px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
                      onClick={() => { setDownloadMenuOpen(false); triggerBlobDownload(downloadUrl!, fileName ?? "document.pdf"); }}
                    >
                      전체 문서 다운로드
                    </button>
                    <button
                      className="flex w-full items-center rounded-sm px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
                      onClick={() => { setDownloadMenuOpen(false); triggerBlobDownload(pagesDownloadUrl!, buildPagesFileName(fileName ?? "pages.pdf", pagesDownloadUrl!)); }}
                    >
                      근거 페이지만 다운로드
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <Button size="sm" onClick={() => triggerBlobDownload(downloadUrl!, fileName ?? "document.pdf")}>
                다운로드
              </Button>
            )
          )}
        </div>
      </div>
      {fileName && (
        <div className="text-xs text-muted-foreground px-2 py-1 shrink-0">{fileName}</div>
      )}
      <div className="overflow-hidden flex justify-center flex-1 min-h-0" ref={containerRef}>
        <Document
          file={url}
          onLoadSuccess={onLoadSuccess}
          onLoadError={onLoadError}
          loading={<div className="p-6 text-center text-muted-foreground" role="status" aria-live="polite">PDF 로딩 중...</div>}
        >
          <Page
            pageNumber={currentPage}
            width={pageWidth}
            renderTextLayer={true}
            renderAnnotationLayer={true}
          />
        </Document>
      </div>

      {/* Expand Modal */}
      <PdfExpandModal
        open={expandOpen}
        onOpenChange={setExpandOpen}
        url={url}
        initialPage={currentPage}
        numPages={numPages}
        downloadUrl={downloadUrl}
        pagesDownloadUrl={pagesDownloadUrl}
        fileName={fileName}
      />
    </div>
  );
}
