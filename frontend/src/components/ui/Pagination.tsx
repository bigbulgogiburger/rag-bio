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
          className={`pagination-btn ${i === page ? 'active' : ''}`}
          onClick={() => onPageChange(i)}
          disabled={i === page}
          aria-label={`페이지 ${i + 1}`}
          aria-current={i === page ? 'page' : undefined}
        >
          {i + 1}
        </button>
      );
    }

    return buttons;
  };

  return (
    <div className="pagination" role="navigation" aria-label="페이지네이션">
      <div className="pagination-info">
        전체 <strong style={{ color: 'var(--color-text)', fontWeight: 600 }}>{totalElements}</strong>건 중 {startItem}-{endItem}건
      </div>

      <div className="pagination-buttons">
        <button
          className="pagination-btn"
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0}
          aria-label="이전 페이지"
        >
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true" style={{ marginRight: '2px' }}>
            <path d="M8.5 3L4.5 7L8.5 11" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          이전
        </button>

        {renderPageButtons()}

        <button
          className="pagination-btn"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          aria-label="다음 페이지"
        >
          다음
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true" style={{ marginLeft: '2px' }}>
            <path d="M5.5 3L9.5 7L5.5 11" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-xs)' }}>
        <label
          htmlFor="pagination-size-select"
          style={{
            fontSize: 'var(--font-size-xs)',
            color: 'var(--color-muted)',
            whiteSpace: 'nowrap',
          }}
        >
          표시:
        </label>
        <select
          id="pagination-size-select"
          value={size}
          onChange={(e) => onSizeChange(Number(e.target.value))}
          className="select"
          style={{
            width: 'auto',
            padding: '0.3rem 0.5rem',
            fontSize: 'var(--font-size-xs)',
            borderRadius: 'var(--radius-sm)',
          }}
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
