'use client';

import { useRef, useCallback, useState } from 'react';
import { cn } from '@/lib/utils';

interface FileDropZoneProps {
  onFilesAdded: (files: File[]) => void;
  accept?: string;
  maxSizeMB?: number;
  maxFiles?: number;
  disabled?: boolean;
  currentFileCount?: number;
}

const DEFAULT_ACCEPT = '.pdf,.doc,.docx';
const DEFAULT_MAX_SIZE_MB = 50;
const DEFAULT_MAX_FILES = 10;

function parseAcceptExtensions(accept: string): string[] {
  return accept.split(',').map((ext) => ext.trim().toLowerCase());
}

function formatAcceptLabel(accept: string): string {
  return parseAcceptExtensions(accept)
    .map((ext) => ext.replace('.', '').toUpperCase())
    .join(', ');
}

export default function FileDropZone({
  onFilesAdded,
  accept = DEFAULT_ACCEPT,
  maxSizeMB = DEFAULT_MAX_SIZE_MB,
  maxFiles = DEFAULT_MAX_FILES,
  disabled = false,
  currentFileCount = 0,
}: FileDropZoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  const validateFiles = useCallback(
    (fileList: FileList): File[] => {
      const extensions = parseAcceptExtensions(accept);
      const maxBytes = maxSizeMB * 1024 * 1024;
      const remaining = maxFiles - currentFileCount;

      const files = Array.from(fileList);

      if (files.length > remaining) {
        alert(
          remaining <= 0
            ? `최대 ${maxFiles}개 파일까지 업로드할 수 있습니다. 더 이상 추가할 수 없습니다.`
            : `최대 ${maxFiles}개 파일까지 업로드할 수 있습니다. ${remaining}개만 추가할 수 있습니다.`,
        );
        return [];
      }

      const errors: string[] = [];
      const valid: File[] = [];

      for (const file of files) {
        const ext = '.' + file.name.split('.').pop()?.toLowerCase();
        if (!extensions.includes(ext)) {
          errors.push(`"${file.name}" - 지원하지 않는 파일 형식입니다.`);
          continue;
        }
        if (file.size > maxBytes) {
          errors.push(`"${file.name}" - 파일 크기가 ${maxSizeMB}MB를 초과합니다.`);
          continue;
        }
        valid.push(file);
      }

      if (errors.length > 0) {
        alert(errors.join('\n'));
      }

      return valid;
    },
    [accept, maxSizeMB, maxFiles, currentFileCount],
  );

  const handleDragEnter = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (!disabled) setDragOver(true);
    },
    [disabled],
  );

  const handleDragOver = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (!disabled) setDragOver(true);
    },
    [disabled],
  );

  const handleDragLeave = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setDragOver(false);
    },
    [],
  );

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setDragOver(false);
      if (disabled) return;

      const valid = validateFiles(e.dataTransfer.files);
      if (valid.length > 0) {
        onFilesAdded(valid);
      }
    },
    [disabled, validateFiles, onFilesAdded],
  );

  const handleClick = useCallback(() => {
    if (disabled) return;
    inputRef.current?.click();
  }, [disabled]);

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      if (!e.target.files || e.target.files.length === 0) return;

      const valid = validateFiles(e.target.files);
      if (valid.length > 0) {
        onFilesAdded(valid);
      }
      // Reset input so the same file can be re-selected
      e.target.value = '';
    },
    [validateFiles, onFilesAdded],
  );

  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-8 text-center transition-colors cursor-pointer',
        'border-border hover:border-primary/50 hover:bg-primary/5',
        dragOver && 'border-primary bg-primary/5',
        disabled && 'pointer-events-none opacity-50',
      )}
      onDragEnter={handleDragEnter}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      onClick={handleClick}
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-label="파일 업로드 영역"
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          handleClick();
        }
      }}
    >
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={accept}
        onChange={handleInputChange}
        className="hidden"
        aria-hidden="true"
      />

      <div className="mb-3 text-muted-foreground">
        <svg
          width="48"
          height="48"
          viewBox="0 0 48 48"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="M24 32V16M24 16L18 22M24 16L30 22"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M8 32V36C8 38.2091 9.79086 40 12 40H36C38.2091 40 40 38.2091 40 36V32"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>

      <p className="text-sm font-semibold text-foreground">파일을 드래그하여 업로드</p>
      <p className="text-xs text-muted-foreground mt-1">또는 클릭하여 파일 선택</p>
      <p className="text-xs text-muted-foreground/70 mt-2">
        {formatAcceptLabel(accept)} 파일 지원 (최대 {maxSizeMB}MB, {maxFiles}개)
      </p>
    </div>
  );
}
