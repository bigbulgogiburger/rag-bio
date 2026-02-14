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
      <h3>{title}</h3>
      {description && <p>{description}</p>}
      {action && (
        <button className="btn btn-primary" onClick={action.onClick}>
          {action.label}
        </button>
      )}
    </div>
  );
}
