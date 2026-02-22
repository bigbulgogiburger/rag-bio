import { cn } from '@/lib/utils';

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

export default function DataTable<T>({
  columns,
  data,
  onRowClick,
  emptyMessage = '데이터가 없습니다',
}: DataTableProps<T>) {
  if (data.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
        <svg
          width="48"
          height="48"
          viewBox="0 0 48 48"
          fill="none"
          aria-hidden="true"
          className="mb-4 opacity-45"
        >
          <rect x="6" y="10" width="36" height="28" rx="4" stroke="currentColor" strokeWidth="2" fill="none" />
          <path d="M6 18h36" stroke="currentColor" strokeWidth="2" />
          <circle cx="12" cy="14" r="1.5" fill="currentColor" />
          <circle cx="17" cy="14" r="1.5" fill="currentColor" />
          <circle cx="22" cy="14" r="1.5" fill="currentColor" />
          <path d="M18 28h12M21 32h6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
        <p className="m-0 font-medium text-muted-foreground">
          {emptyMessage}
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
    <table className="w-full min-w-[640px] border-separate border-spacing-0" role="table" aria-label="데이터 테이블">
      <thead>
        <tr>
          {columns.map((column) => (
            <th
              key={column.key}
              scope="col"
              style={{ width: column.width }}
              className="text-left px-4 py-2.5 font-medium text-xs text-muted-foreground uppercase tracking-wider border-b border-border bg-muted/50 whitespace-nowrap first:rounded-tl-md last:rounded-tr-md"
            >
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
            className={cn(
              onRowClick && 'transition-colors hover:bg-primary/5 cursor-pointer'
            )}
            role={onRowClick ? 'button' : undefined}
            tabIndex={onRowClick ? 0 : undefined}
            onKeyDown={(e) => {
              if (onRowClick && (e.key === 'Enter' || e.key === ' ')) {
                e.preventDefault();
                onRowClick(item);
              }
            }}
          >
            {columns.map((column) => (
              <td
                key={column.key}
                className="px-4 py-3 text-sm border-b border-border/30 align-middle"
              >
                {column.render
                  ? column.render(item)
                  : String((item as any)[column.key] ?? '')}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
    </div>
  );
}
