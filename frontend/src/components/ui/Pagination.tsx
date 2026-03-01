import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';

interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
  sizeOptions?: number[];
}

/**
 * Pagination 컴포넌트
 *
 * 사용 예:
 * <Pagination
 *   page={0}
 *   totalPages={8}
 *   totalElements={142}
 *   size={20}
 *   onPageChange={(page) => setPage(page)}
 *   onSizeChange={(size) => setSize(size)}
 * />
 */
export default function Pagination({
  page,
  totalPages,
  totalElements,
  size,
  onPageChange,
  onSizeChange,
  sizeOptions = [20, 50, 100],
}: PaginationProps) {
  const startItem = totalElements === 0 ? 0 : page * size + 1;
  const endItem = Math.min((page + 1) * size, totalElements);

  const renderPageButtons = () => {
    const buttons: React.ReactNode[] = [];
    const maxVisible = 5;

    let startPage = Math.max(0, page - Math.floor(maxVisible / 2));
    let endPage = Math.min(totalPages - 1, startPage + maxVisible - 1);

    if (endPage - startPage < maxVisible - 1) {
      startPage = Math.max(0, endPage - maxVisible + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
      buttons.push(
        <button
          key={i}
          className={cn(
            'inline-flex items-center justify-center min-w-[32px] h-8 px-2 text-xs font-medium rounded-md border transition-colors',
            i === page
              ? 'bg-primary text-primary-foreground border-primary font-semibold shadow-sm'
              : 'border-border bg-card text-foreground hover:bg-muted/50',
          )}
          onClick={() => onPageChange(i)}
          disabled={i === page}
          aria-label={`페이지 ${i + 1}`}
          aria-current={i === page ? 'page' : undefined}
        >
          {i + 1}
        </button>,
      );
    }

    return buttons;
  };

  return (
    <div
      className="flex flex-col gap-3 py-4 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:gap-4"
      role="navigation"
      aria-label="페이지네이션"
    >
      <div className="text-center sm:text-left">
        전체{' '}
        <strong className="text-foreground font-semibold">{totalElements}</strong>
        건 중 {startItem}-{endItem}건
      </div>

      <div className="flex items-center justify-center gap-1">
        <button
          className={cn(
            'inline-flex items-center gap-1 h-8 px-3 text-xs font-medium rounded-md border border-border bg-card transition-colors',
            page === 0
              ? 'opacity-50 cursor-not-allowed'
              : 'hover:bg-muted/50 text-foreground',
          )}
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0}
          aria-label="이전 페이지"
        >
          <ChevronLeft className="h-3.5 w-3.5" aria-hidden="true" />
          이전
        </button>

        <span className="hidden sm:contents">
          {renderPageButtons()}
        </span>

        <span className="inline-flex items-center px-2 text-xs font-medium text-foreground sm:hidden">
          {page + 1} / {totalPages}
        </span>

        <button
          className={cn(
            'inline-flex items-center gap-1 h-8 px-3 text-xs font-medium rounded-md border border-border bg-card transition-colors',
            page >= totalPages - 1
              ? 'opacity-50 cursor-not-allowed'
              : 'hover:bg-muted/50 text-foreground',
          )}
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          aria-label="다음 페이지"
        >
          다음
          <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      </div>

      <div className="hidden sm:flex items-center gap-2">
        <label
          htmlFor="pagination-size-select"
          className="text-xs text-muted-foreground whitespace-nowrap"
        >
          표시:
        </label>
        <select
          id="pagination-size-select"
          value={size}
          onChange={(e) => onSizeChange(Number(e.target.value))}
          className="w-auto border border-border rounded-md px-2 py-1.5 text-xs bg-card text-foreground shadow-sm transition-colors hover:border-border/80 dark:hover:border-border/80 focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/10"
          aria-label="페이지당 항목 수"
        >
          {sizeOptions.map((option) => (
            <option key={option} value={option}>
              {option}건
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
