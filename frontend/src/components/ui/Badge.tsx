interface BadgeProps {
  variant: 'info' | 'success' | 'warn' | 'danger' | 'neutral';
  children: React.ReactNode;
  style?: React.CSSProperties;
}

/**
 * Badge 컴포넌트
 *
 * 사용 예:
 * <Badge variant="success">인덱싱 완료</Badge>
 * <Badge variant="danger">실패</Badge>
 */
export default function Badge({ variant, children, style }: BadgeProps) {
  const className = variant === 'neutral' ? 'badge' : `badge badge-${variant}`;

  return (
    <span className={className} role="status" style={style}>
      {children}
    </span>
  );
}
