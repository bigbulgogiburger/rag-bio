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
    <div className="filter-bar" role="search" aria-label="필터 검색">
      {fields.map((field) => {
        if (field.type === 'select') {
          return (
            <label key={field.key} className="label">
              <span style={{ fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', textTransform: 'uppercase' as const, letterSpacing: '0.03em', color: 'var(--color-muted)' }}>
                {field.label}
              </span>
              <select
                className="select"
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
            <label key={field.key} className="label">
              <span style={{ fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', textTransform: 'uppercase' as const, letterSpacing: '0.03em', color: 'var(--color-muted)' }}>
                {field.label}
              </span>
              <input
                type="date"
                className="input"
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
          <label key={field.key} className="label">
            <span style={{ fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', textTransform: 'uppercase' as const, letterSpacing: '0.03em', color: 'var(--color-muted)' }}>
              {field.label}
            </span>
            <input
              type="text"
              className="input"
              value={values[field.key] || ''}
              onChange={(e) => onChange(field.key, e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={field.placeholder}
              aria-label={field.label}
            />
          </label>
        );
      })}

      <button
        className="btn btn-primary"
        onClick={onSearch}
        style={{ alignSelf: 'flex-end' }}
      >
        <svg width="15" height="15" viewBox="0 0 15 15" fill="none" aria-hidden="true">
          <circle cx="6.5" cy="6.5" r="4.5" stroke="currentColor" strokeWidth="1.5" />
          <path d="M10 10L13.5 13.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
        검색
      </button>
    </div>
  );
}
