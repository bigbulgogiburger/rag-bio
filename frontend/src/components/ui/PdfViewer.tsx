"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { Document, Page, pdfjs } from "react-pdf";
import "react-pdf/dist/Page/AnnotationLayer.css";
import "react-pdf/dist/Page/TextLayer.css";
import { Button } from "@/components/ui/button";

pdfjs.GlobalWorkerOptions.workerSrc = `//unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;

interface PdfViewerProps {
  url: string;
  initialPage?: number;
  downloadUrl?: string;
  fileName?: string;
}

export default function PdfViewer({
  url,
  initialPage = 1,
  downloadUrl,
  fileName,
}: PdfViewerProps) {
  const [numPages, setNumPages] = useState<number>(0);
  const [currentPage, setCurrentPage] = useState(initialPage);
  const [loadError, setLoadError] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [pageWidth, setPageWidth] = useState(480);

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
        {downloadUrl && (
          <Button size="sm" asChild className="ml-auto">
            <a href={downloadUrl} download>
              다운로드
            </a>
          </Button>
        )}
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
    </div>
  );
}
