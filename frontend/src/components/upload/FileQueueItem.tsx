"use client";

import { useState } from "react";
import type { UploadQueueFile } from "@/lib/api/client";
import { KB_CATEGORY_LABELS } from "@/lib/i18n/labels";

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
  let color = "#94a3b8"; // grey default
  if (ext === "pdf") color = "#dc2626"; // red
  else if (ext === "doc" || ext === "docx") color = "#2563eb"; // blue

  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path
        d="M3 1h7l4 4v10a1 1 0 01-1 1H3a1 1 0 01-1-1V2a1 1 0 011-1z"
        fill={color}
        opacity={0.15}
      />
      <path
        d="M3 1h7l4 4v10a1 1 0 01-1 1H3a1 1 0 01-1-1V2a1 1 0 011-1z"
        stroke={color}
        strokeWidth={1.2}
      />
      <path d="M10 1v4h4" stroke={color} strokeWidth={1.2} />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <path d="M3 8.5l3 3 7-7" stroke="#059669" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round" />
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

export default function FileQueueItem({ item, onRemove, onMetadataChange, disabled }: FileQueueItemProps) {
  const [metaOpen, setMetaOpen] = useState(true);
  const isPending = item.status === "pending";
  const canRemove = (isPending || item.status === "error") && !disabled;

  const statusClass =
    item.status === "uploading"
      ? "uploading"
      : item.status === "success"
        ? "success"
        : item.status === "error"
          ? "error"
          : "";

  return (
    <div>
      <div className={`file-queue-item ${statusClass}`}>
        <div className="file-icon">
          {item.status === "success" ? <CheckIcon /> : <FileIcon fileName={item.file.name} />}
        </div>

        <div className="file-info">
          <div className="file-name">{item.file.name}</div>
          <div className="file-size">{formatFileSize(item.file.size)}</div>
          {item.status === "error" && item.error && (
            <div style={{ color: "var(--color-danger)", fontSize: "var(--font-size-xs)", marginTop: 2 }}>
              {item.error}
            </div>
          )}
        </div>

        <div className="file-actions">
          {canRemove && (
            <button
              type="button"
              onClick={() => onRemove(item.id)}
              aria-label="삭제"
              style={{
                background: "none",
                border: "none",
                cursor: "pointer",
                padding: 4,
                color: "var(--color-muted)",
                display: "flex",
                alignItems: "center",
              }}
            >
              <RemoveIcon />
            </button>
          )}
        </div>
      </div>

      {item.status === "uploading" && (
        <div className="progress-bar">
          <div
            className="progress-fill"
            style={{ width: `${item.progress}%` }}
          />
        </div>
      )}

      {item.status === "success" && (
        <div className="progress-bar">
          <div className="progress-fill complete" style={{ width: "100%" }} />
        </div>
      )}

      {item.status === "error" && (
        <div className="progress-bar">
          <div className="progress-fill error" style={{ width: "100%" }} />
        </div>
      )}

      {isPending && (
        <>
          <button
            type="button"
            onClick={() => setMetaOpen((v) => !v)}
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              fontSize: "var(--font-size-xs)",
              color: "var(--color-primary)",
              padding: "var(--space-xs) 0",
            }}
          >
            {metaOpen ? "메타데이터 접기 ▲" : "메타데이터 펼치기 ▼"}
          </button>

          {metaOpen && (
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "1fr 1fr 1fr",
                gap: "var(--space-sm)",
                padding: "var(--space-sm) 0",
              }}
            >
              <input
                className="input"
                type="text"
                placeholder="제목"
                value={item.metadata.title}
                onChange={(e) => onMetadataChange(item.id, "title", e.target.value)}
                disabled={disabled}
              />
              <select
                className="select"
                value={item.metadata.category}
                onChange={(e) => onMetadataChange(item.id, "category", e.target.value)}
                disabled={disabled}
              >
                <option value="">카테고리 선택</option>
                {Object.entries(KB_CATEGORY_LABELS).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
              <input
                className="input"
                type="text"
                placeholder="제품군"
                value={item.metadata.productFamily}
                onChange={(e) => onMetadataChange(item.id, "productFamily", e.target.value)}
                disabled={disabled}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
}
