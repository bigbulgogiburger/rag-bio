interface Column<T> {
  key: string;
  header: string;
  render?: (item: T) => React.ReactNode;
  width?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  onRowClick?: (item: T) => void;
  emptyMessage?: string;
}

/**
 * DataTable 컴포넌트
 *
 * 사용 예:
 * <DataTable
 *   columns={[
 *     { key: 'question', header: '질문 요약' },
 *     { key: 'status', header: '상태', render: (item) => <Badge variant="success">{item.status}</Badge> },
 *   ]}
 *   data={inquiries}
 *   onRowClick={(item) => router.push(`/inquiries/${item.inquiryId}`)}
 *   emptyMessage="등록된 문의가 없습니다"
 * />
 */
export default function DataTable<T>({
  columns,
  data,
  onRowClick,
  emptyMessage = '데이터가 없습니다',
}: DataTableProps<T>) {
  if (data.length === 0) {
    return (
      <div className="empty-state">
        <svg
          width="48"
          height="48"
          viewBox="0 0 48 48"
          fill="none"
          aria-hidden="true"
          style={{ marginBottom: 'var(--space-md)', opacity: 0.45 }}
        >
          <rect x="6" y="10" width="36" height="28" rx="4" stroke="currentColor" strokeWidth="2" fill="none" />
          <path d="M6 18h36" stroke="currentColor" strokeWidth="2" />
          <circle cx="12" cy="14" r="1.5" fill="currentColor" />
          <circle cx="17" cy="14" r="1.5" fill="currentColor" />
          <circle cx="22" cy="14" r="1.5" fill="currentColor" />
          <path d="M18 28h12M21 32h6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
        <p style={{ margin: 0, fontWeight: 500, color: 'var(--color-text-secondary)' }}>
          {emptyMessage}
        </p>
      </div>
    );
  }

  return (
    <table className="data-table" role="table">
      <thead>
        <tr>
          {columns.map((column) => (
            <th key={column.key} style={{ width: column.width }}>
              {column.header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {data.map((item, index) => (
          <tr
            key={index}
            onClick={() => onRowClick?.(item)}
            style={{ cursor: onRowClick ? 'pointer' : 'default' }}
            tabIndex={onRowClick ? 0 : undefined}
            onKeyDown={(e) => {
              if (onRowClick && (e.key === 'Enter' || e.key === ' ')) {
                e.preventDefault();
                onRowClick(item);
              }
            }}
          >
            {columns.map((column) => (
              <td key={column.key}>
                {column.render
                  ? column.render(item)
                  : String((item as any)[column.key] ?? '')}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
