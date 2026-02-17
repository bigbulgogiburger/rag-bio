interface EmptyStateProps {
  title: string;
  description?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}

/**
 * EmptyState 컴포넌트
 *
 * 사용 예:
 * <EmptyState
 *   title="등록된 문의가 없습니다"
 *   description="새 문의를 작성하여 CS 대응을 시작하세요."
 *   action={{ label: "문의 작성", onClick: () => router.push('/inquiries/new') }}
 * />
 */
export default function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="empty-state">
      {/* Decorative inbox/empty illustration */}
      <div
        style={{
          width: '80px',
          height: '80px',
          margin: '0 auto var(--space-lg)',
          borderRadius: 'var(--radius-xl)',
          background: 'var(--color-bg-soft)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <svg
          width="40"
          height="40"
          viewBox="0 0 40 40"
          fill="none"
          aria-hidden="true"
        >
          {/* Inbox tray */}
          <rect x="5" y="8" width="30" height="24" rx="4" stroke="var(--color-muted)" strokeWidth="1.5" fill="none" />
          {/* Inbox opening */}
          <path d="M5 22h10l2 4h6l2-4h10" stroke="var(--color-muted)" strokeWidth="1.5" strokeLinejoin="round" fill="none" />
          {/* Down arrow */}
          <path d="M20 4v12M16 12l4 4 4-4" stroke="var(--color-primary)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </div>

      <h3>{title}</h3>
      {description && <p>{description}</p>}
      {action && (
        <button className="btn btn-primary btn-pill" onClick={action.onClick}>
          {action.label}
        </button>
      )}
    </div>
  );
}
