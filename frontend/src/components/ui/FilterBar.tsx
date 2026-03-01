import { Search } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface FilterField {
  key: string;
  label: string;
  type: 'select' | 'text' | 'date';
  options?: { value: string; label: string }[];
  placeholder?: string;
}

interface FilterBarProps {
  fields: FilterField[];
  values: Record<string, string>;
  onChange: (key: string, value: string) => void;
  onSearch: () => void;
}

const inputClasses = cn(
  'w-full border border-border rounded-md px-3 py-2.5 text-sm',
  'bg-card text-foreground shadow-sm transition-colors',
  'hover:border-border/80 dark:hover:border-border/80',
  'focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/10',
);

/**
 * FilterBar 컴포넌트
 *
 * 사용 예:
 * <FilterBar
 *   fields={[
 *     { key: 'status', label: '상태', type: 'select', options: [
 *       { value: '', label: '전체' },
 *       { value: 'RECEIVED', label: '접수됨' },
 *     ]},
 *     { key: 'keyword', label: '검색어', type: 'text', placeholder: '질문 내용 검색' },
 *   ]}
 *   values={filters}
 *   onChange={(key, value) => setFilters({ ...filters, [key]: value })}
 *   onSearch={handleSearch}
 * />
 */
export default function FilterBar({
  fields,
  values,
  onChange,
  onSearch,
}: FilterBarProps) {
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      onSearch();
    }
  };

  return (
    <div
      className="grid grid-cols-1 gap-2 py-4 border-t border-border/50 sm:flex sm:items-end sm:flex-wrap"
      role="search"
      aria-label="필터 검색"
    >
      {fields.map((field) => {
        if (field.type === 'select') {
          return (
            <label key={field.key} className="grid gap-1.5 flex-1 min-w-0 sm:min-w-[160px]">
              <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {field.label}
              </span>
              <select
                className={inputClasses}
                value={values[field.key] || ''}
                onChange={(e) => onChange(field.key, e.target.value)}
                aria-label={field.label}
              >
                {field.options?.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          );
        }

        if (field.type === 'date') {
          return (
            <label key={field.key} className="grid gap-1.5 flex-1 min-w-0 sm:min-w-[160px]">
              <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                {field.label}
              </span>
              <input
                type="date"
                className={inputClasses}
                value={values[field.key] || ''}
                onChange={(e) => onChange(field.key, e.target.value)}
                onKeyDown={handleKeyDown}
                aria-label={field.label}
              />
            </label>
          );
        }

        // type === 'text'
        return (
          <label key={field.key} className="grid gap-1.5 flex-1 min-w-0 sm:min-w-[160px]">
            <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              {field.label}
            </span>
            <input
              type="text"
              className={inputClasses}
              value={values[field.key] || ''}
              onChange={(e) => onChange(field.key, e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={field.placeholder}
              aria-label={field.label}
            />
          </label>
        );
      })}

      <Button onClick={onSearch} className="self-end w-full sm:w-auto">
        <Search className="h-4 w-4" aria-hidden="true" />
        검색
      </Button>
    </div>
  );
}
