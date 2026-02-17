"use client";

import { useState, useCallback } from "react";
import { Document, Page, pdfjs } from "react-pdf";
import "react-pdf/dist/Page/AnnotationLayer.css";
import "react-pdf/dist/Page/TextLayer.css";

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

  const onLoadSuccess = useCallback(({ numPages }: { numPages: number }) => {
    setNumPages(numPages);
    setLoadError(null);
  }, []);

  const onLoadError = useCallback(() => {
    setLoadError("PDF 파일을 불러올 수 없습니다.");
  }, []);

  if (loadError) {
    return (
      <div className="pdf-viewer-error">
        <p>{loadError}</p>
        {downloadUrl && (
          <a href={downloadUrl} className="btn btn-sm" download>
            파일 직접 다운로드
          </a>
        )}
      </div>
    );
  }

  return (
    <div className="pdf-viewer">
      <div className="pdf-toolbar">
        <button
          className="btn btn-sm"
          onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
          disabled={currentPage <= 1}
        >
          이전
        </button>
        <span className="pdf-page-info">
          {currentPage} / {numPages || "..."}
        </span>
        <button
          className="btn btn-sm"
          onClick={() => setCurrentPage((p) => Math.min(numPages, p + 1))}
          disabled={currentPage >= numPages}
        >
          다음
        </button>
        {downloadUrl && (
          <a href={downloadUrl} className="btn btn-sm btn-primary" download style={{ marginLeft: "auto" }}>
            다운로드
          </a>
        )}
      </div>
      {fileName && (
        <div className="pdf-filename muted">{fileName}</div>
      )}
      <div className="pdf-document-wrapper">
        <Document
          file={url}
          onLoadSuccess={onLoadSuccess}
          onLoadError={onLoadError}
          loading={<div className="pdf-loading">PDF 로딩 중...</div>}
        >
          <Page
            pageNumber={currentPage}
            width={480}
            renderTextLayer={true}
            renderAnnotationLayer={true}
          />
        </Document>
      </div>
    </div>
  );
}
