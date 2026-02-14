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
    <div className="filter-bar">
      {fields.map((field) => {
        if (field.type === 'select') {
          return (
            <label key={field.key} className="label">
              <span style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-medium)' }}>
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
              <span style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-medium)' }}>
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
            <span style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-medium)' }}>
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
        검색
      </button>
    </div>
  );
}
