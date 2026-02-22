"use client";

import { useState } from "react";
import type { UploadQueueFile } from "@/lib/api/client";
import { KB_CATEGORY_LABELS, PRODUCT_FAMILY_LABELS } from "@/lib/i18n/labels";
import { cn } from "@/lib/utils";

interface FileQueueItemProps {
  item: UploadQueueFile;
  onRemove: (id: string) => void;
  onMetadataChange: (id: string, field: string, value: string) => void;
  disabled?: boolean;
}

function formatFileSize(bytes: number): string {
  if (bytes >= 1_048_576) {
    return `${(bytes / 1_048_576).toFixed(1)} MB`;
  }
  return `${(bytes / 1024).toFixed(1)} KB`;
}

function getFileExtension(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : "";
}

function FileIcon({ fileName }: { fileName: string }) {
  const ext = getFileExtension(fileName);
  let colorClass = "text-muted-foreground";
  if (ext === "pdf") colorClass = "text-red-600 dark:text-red-400";
  else if (ext === "doc" || ext === "docx") colorClass = "text-blue-600 dark:text-blue-400";

  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" className={colorClass}>
      <path
        d="M3 1h7l4 4v10a1 1 0 01-1 1H3a1 1 0 01-1-1V2a1 1 0 011-1z"
        fill="currentColor"
        opacity={0.15}
      />
      <path
        d="M3 1h7l4 4v10a1 1 0 01-1 1H3a1 1 0 01-1-1V2a1 1 0 011-1z"
        stroke="currentColor"
        strokeWidth={1.2}
      />
      <path d="M10 1v4h4" stroke="currentColor" strokeWidth={1.2} />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" className="text-emerald-600 dark:text-emerald-400">
      <path d="M3 8.5l3 3 7-7" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function RemoveIcon() {
  return (
    <svg width={14} height={14} viewBox="0 0 14 14" fill="none" aria-hidden="true">
      <path d="M3 3l8 8M11 3l-8 8" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" />
    </svg>
  );
}

const inputClasses = cn(
  'w-full rounded-md border border-input bg-transparent px-3 py-1.5 text-sm shadow-sm',
  'placeholder:text-muted-foreground',
  'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
  'disabled:cursor-not-allowed disabled:opacity-50',
);

export default function FileQueueItem({ item, onRemove, onMetadataChange, disabled }: FileQueueItemProps) {
  const [metaOpen, setMetaOpen] = useState(true);
  const isPending = item.status === "pending";
  const canRemove = (isPending || item.status === "error") && !disabled;

  return (
    <div>
      <div
        className={cn(
          'flex items-center gap-3 rounded-lg border p-3 transition-colors',
          item.status === 'uploading' && 'border-primary/30 bg-primary/5',
          item.status === 'success' && 'border-success/30 bg-success-light',
          item.status === 'error' && 'border-destructive/30 bg-destructive/5',
          !item.status || item.status === 'pending' ? 'border-border bg-card' : '',
        )}
      >
        <div className="flex-shrink-0">
          {item.status === "success" ? <CheckIcon /> : <FileIcon fileName={item.file.name} />}
        </div>

        <div className="flex-1 min-w-0">
          <div className="text-sm font-medium truncate">{item.file.name}</div>
          <div className="text-xs text-muted-foreground">{formatFileSize(item.file.size)}</div>
          {item.status === "error" && item.error && (
            <div className="text-xs text-destructive mt-0.5" role="alert">
              {item.error}
            </div>
          )}
        </div>

        <div className="flex-shrink-0">
          {canRemove && (
            <button
              type="button"
              onClick={() => onRemove(item.id)}
              aria-label="삭제"
              className="flex items-center p-1 text-muted-foreground hover:text-destructive transition-colors"
            >
              <RemoveIcon />
            </button>
          )}
        </div>
      </div>

      {item.status === "uploading" && (
        <div
          className="h-1 w-full rounded-full bg-muted mt-1 overflow-hidden"
          role="progressbar"
          aria-valuenow={item.progress}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label={`${item.file.name} 업로드 진행률`}
        >
          <div
            className="h-full rounded-full bg-primary transition-all duration-300"
            style={{ width: `${item.progress}%` }}
          />
        </div>
      )}

      {item.status === "success" && (
        <div className="h-1 w-full rounded-full bg-muted mt-1 overflow-hidden">
          <div className="h-full w-full rounded-full bg-success" />
        </div>
      )}

      {item.status === "error" && (
        <div className="h-1 w-full rounded-full bg-muted mt-1 overflow-hidden">
          <div className="h-full w-full rounded-full bg-destructive" />
        </div>
      )}

      {isPending && (
        <>
          <button
            type="button"
            onClick={() => setMetaOpen((v) => !v)}
            className="text-xs text-primary py-1 hover:underline"
            aria-expanded={metaOpen}
            aria-label={metaOpen ? "메타데이터 접기" : "메타데이터 펼치기"}
          >
            {metaOpen ? "메타데이터 접기 ▲" : "메타데이터 펼치기 ▼"}
          </button>

          {metaOpen && (
            <div className="grid grid-cols-3 gap-2 py-2">
              <input
                className={inputClasses}
                type="text"
                placeholder="제목"
                value={item.metadata.title}
                onChange={(e) => onMetadataChange(item.id, "title", e.target.value)}
                disabled={disabled}
                aria-label="제목"
              />
              <select
                className={inputClasses}
                value={item.metadata.category}
                onChange={(e) => onMetadataChange(item.id, "category", e.target.value)}
                disabled={disabled}
                aria-label="카테고리"
              >
                <option value="">카테고리 선택</option>
                {Object.entries(KB_CATEGORY_LABELS).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
              <select
                className={inputClasses}
                value={item.metadata.productFamily}
                onChange={(e) => onMetadataChange(item.id, "productFamily", e.target.value)}
                disabled={disabled}
                aria-label="제품군"
              >
                <option value="">제품군 선택</option>
                {Object.entries(PRODUCT_FAMILY_LABELS).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
          )}
        </>
      )}
    </div>
  );
}
