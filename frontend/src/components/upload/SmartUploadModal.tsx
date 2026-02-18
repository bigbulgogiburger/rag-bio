"use client";

import { useState, useCallback } from "react";
import FileDropZone from "./FileDropZone";
import FileQueueItem from "./FileQueueItem";
import {
  uploadKbDocumentWithProgress,
  type UploadQueueFile,
} from "@/lib/api/client";
import { Button } from "@/components/ui/button";

interface SmartUploadModalProps {
  open: boolean;
  onClose: () => void;
  onComplete: () => void;
}

let fileCounter = 0;

function stripExtension(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot > 0 ? name.slice(0, dot) : name;
}

export default function SmartUploadModal({
  open,
  onClose,
  onComplete,
}: SmartUploadModalProps) {
  const [files, setFiles] = useState<UploadQueueFile[]>([]);
  const [uploading, setUploading] = useState(false);

  const handleFilesAdded = useCallback((newFiles: File[]) => {
    const items: UploadQueueFile[] = newFiles.map((file) => ({
      id: `file-${++fileCounter}`,
      file,
      status: "pending" as const,
      progress: 0,
      metadata: {
        title: stripExtension(file.name),
        category: "MANUAL",
        productFamily: "",
        description: "",
        tags: "",
      },
    }));
    setFiles((prev) => [...prev, ...items]);
  }, []);

  const handleRemove = useCallback((id: string) => {
    setFiles((prev) => prev.filter((f) => f.id !== id));
  }, []);

  const handleMetadataChange = useCallback(
    (id: string, field: string, value: string) => {
      setFiles((prev) =>
        prev.map((f) =>
          f.id === id
            ? { ...f, metadata: { ...f.metadata, [field]: value } }
            : f,
        ),
      );
    },
    [],
  );

  const handleUploadAll = async () => {
    const pending = files.filter((f) => f.status === "pending");
    if (pending.length === 0) return;

    setUploading(true);

    for (const item of pending) {
      // Mark as uploading
      setFiles((prev) =>
        prev.map((f) =>
          f.id === item.id ? { ...f, status: "uploading" as const, progress: 0 } : f,
        ),
      );

      try {
        const result = await uploadKbDocumentWithProgress(
          {
            file: item.file,
            title: item.metadata.title || stripExtension(item.file.name),
            category: item.metadata.category || "MANUAL",
            productFamily: item.metadata.productFamily || undefined,
            description: item.metadata.description || undefined,
            tags: item.metadata.tags || undefined,
          },
          (progress) => {
            setFiles((prev) =>
              prev.map((f) =>
                f.id === item.id ? { ...f, progress } : f,
              ),
            );
          },
        );

        setFiles((prev) =>
          prev.map((f) =>
            f.id === item.id
              ? { ...f, status: "success" as const, progress: 100, result }
              : f,
          ),
        );
      } catch (err) {
        setFiles((prev) =>
          prev.map((f) =>
            f.id === item.id
              ? {
                  ...f,
                  status: "error" as const,
                  error: err instanceof Error ? err.message : "업로드 실패",
                }
              : f,
          ),
        );
      }
    }

    setUploading(false);
  };

  const handleClose = () => {
    if (uploading) return;
    const hasSuccess = files.some((f) => f.status === "success");
    setFiles([]);
    if (hasSuccess) {
      onComplete();
    }
    onClose();
  };

  if (!open) return null;

  const pendingCount = files.filter((f) => f.status === "pending").length;
  const successCount = files.filter((f) => f.status === "success").length;
  const errorCount = files.filter((f) => f.status === "error").length;
  const totalCount = files.length;
  const allDone = totalCount > 0 && pendingCount === 0 && !uploading;
  const hasInvalid = files.some(
    (f) => f.status === "pending" && !f.metadata.title,
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm" onClick={handleClose} role="presentation">
      <div
        className="w-full max-w-3xl rounded-xl border bg-card shadow-2xl"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="smart-upload-title"
      >
        <div className="space-y-4 p-6">
          <div className="flex items-center justify-between">
            <h3 id="smart-upload-title" className="text-base font-semibold">스마트 문서 업로드</h3>
            {!uploading && (
              <Button
                variant="ghost"
                size="sm"
                onClick={handleClose}
                aria-label="닫기"
              >
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                  <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                </svg>
              </Button>
            )}
          </div>

          {/* Drop Zone */}
          <FileDropZone
            onFilesAdded={handleFilesAdded}
            disabled={uploading}
            currentFileCount={totalCount}
          />

          {/* File Queue */}
          {files.length > 0 && (
            <>
              <div className="flex items-center justify-between rounded-lg bg-muted/50 px-4 py-3 text-sm" aria-live="polite">
                <span>
                  총 {totalCount}개 파일
                  {successCount > 0 && (
                    <span className="text-success ml-2">
                      {successCount}건 완료
                    </span>
                  )}
                  {errorCount > 0 && (
                    <span className="text-destructive ml-2">
                      {errorCount}건 실패
                    </span>
                  )}
                </span>
                {pendingCount > 0 && !uploading && (
                  <span className="text-sm text-muted-foreground">{pendingCount}건 대기 중</span>
                )}
                {uploading && (
                  <span className="text-primary">업로드 중...</span>
                )}
              </div>

              <div className="space-y-3 max-h-[400px] overflow-y-auto">
                {files.map((item) => (
                  <FileQueueItem
                    key={item.id}
                    item={item}
                    onRemove={handleRemove}
                    onMetadataChange={handleMetadataChange}
                    disabled={uploading}
                  />
                ))}
              </div>
            </>
          )}
        </div>

        {/* Actions */}
        <hr className="border-t border-border mx-6" />

        <div className="flex items-center justify-end gap-3 px-6 pb-6 pt-4">
          {allDone ? (
            <Button onClick={handleClose}>
              완료 ({successCount}건 업로드됨)
            </Button>
          ) : (
            <>
              <Button
                variant="ghost"
                onClick={handleClose}
                disabled={uploading}
              >
                취소
              </Button>
              <Button
                onClick={handleUploadAll}
                disabled={uploading || pendingCount === 0 || hasInvalid}
              >
                {uploading
                  ? "업로드 중..."
                  : `전체 업로드 (${pendingCount}건)`}
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
