interface BadgeProps {
  variant: 'info' | 'success' | 'warn' | 'danger' | 'neutral';
  children: React.ReactNode;
  style?: React.CSSProperties;
}

const dotColors: Record<BadgeProps['variant'], string> = {
  info: '#0284c7',
  success: '#059669',
  warn: '#d97706',
  danger: '#dc2626',
  neutral: '#94a3b8',
};

/**
 * Badge 컴포넌트
 *
 * 사용 예:
 * <Badge variant="success">인덱싱 완료</Badge>
 * <Badge variant="danger">실패</Badge>
 */
export default function Badge({ variant, children, style }: BadgeProps) {
  const className = `badge badge-${variant}`;

  return (
    <span className={className} role="status" style={style}>
      <svg
        width="6"
        height="6"
        viewBox="0 0 6 6"
        fill="none"
        aria-hidden="true"
        style={{ flexShrink: 0 }}
      >
        <circle cx="3" cy="3" r="3" fill={dotColors[variant]} />
      </svg>
      {children}
    </span>
  );
}
