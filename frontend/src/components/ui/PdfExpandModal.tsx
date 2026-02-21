"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { Document, Page, pdfjs } from "react-pdf";
import "react-pdf/dist/Page/AnnotationLayer.css";
import "react-pdf/dist/Page/TextLayer.css";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

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

interface PdfExpandModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  url: string;
  initialPage?: number;
  numPages: number;
  downloadUrl?: string;
  pagesDownloadUrl?: string;
  fileName?: string;
}

export default function PdfExpandModal({
  open,
  onOpenChange,
  url,
  initialPage = 1,
  numPages: externalNumPages,
  downloadUrl,
  pagesDownloadUrl,
  fileName,
}: PdfExpandModalProps) {
  const [currentPage, setCurrentPage] = useState(initialPage);
  const [internalNumPages, setInternalNumPages] = useState(externalNumPages);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [downloadMenuOpen, setDownloadMenuOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const [pageWidth, setPageWidth] = useState(800);

  const numPages = internalNumPages || externalNumPages;

  useEffect(() => {
    setCurrentPage(initialPage);
    setLoadError(null);
  }, [url, initialPage]);

  useEffect(() => {
    if (!open || !containerRef.current) return;
    const observer = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width;
      if (width) setPageWidth(Math.min(width - 32, 1200));
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, [open]);

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

  const onLoadSuccess = useCallback(({ numPages: n }: { numPages: number }) => {
    setInternalNumPages(n);
    setLoadError(null);
  }, []);

  const onLoadError = useCallback(() => {
    setLoadError("PDF 파일을 불러올 수 없습니다.");
  }, []);

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/60 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
        <Dialog.Content
          className="fixed left-[50%] top-[50%] z-50 flex max-h-[90vh] w-[90vw] max-w-[90vw] translate-x-[-50%] translate-y-[-50%] flex-col rounded-xl border bg-background shadow-lg data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-[48%] data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-[48%]"
          aria-label={fileName ? `PDF 확대 보기: ${fileName}` : "PDF 확대 보기"}
        >
          {/* Top Bar */}
          <div className="flex items-center gap-2 border-b border-border bg-muted/50 px-4 py-2 shrink-0 rounded-t-xl">
            {fileName && (
              <span className="text-sm font-medium text-foreground truncate max-w-[300px]" title={fileName}>
                {fileName}
              </span>
            )}

            <div className="flex items-center gap-1 ml-auto">
              {/* Page Navigation */}
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

              {/* Download */}
              {downloadUrl && (
                pagesDownloadUrl ? (
                  <div className="relative" ref={dropdownRef}>
                    <Button
                      variant="outline"
                      size="sm"
                      className="ml-2"
                      onClick={() => setDownloadMenuOpen(!downloadMenuOpen)}
                      aria-haspopup="true"
                      aria-expanded={downloadMenuOpen}
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
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
                  <Button size="sm" variant="outline" className="ml-2" onClick={() => triggerBlobDownload(downloadUrl!, fileName ?? "document.pdf")}>
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
                    다운로드
                  </Button>
                )
              )}

              {/* Close */}
              <Dialog.Close asChild>
                <Button variant="ghost" size="icon" className="ml-1" aria-label="닫기">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                </Button>
              </Dialog.Close>
            </div>
          </div>

          {/* PDF Content */}
          <div className="overflow-auto flex-1 min-h-0" ref={containerRef}>
            {loadError ? (
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
            ) : (
              <div className="flex justify-center p-4">
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
            )}
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
