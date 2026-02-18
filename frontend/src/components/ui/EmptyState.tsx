import { Button } from '@/components/ui/button';

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}

export default function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="text-center py-16 px-6 text-muted-foreground">
      {/* Decorative inbox/empty illustration */}
      <div className="w-20 h-20 mx-auto mb-6 rounded-2xl bg-muted flex items-center justify-center">
        <svg
          width="40"
          height="40"
          viewBox="0 0 40 40"
          fill="none"
          aria-hidden="true"
        >
          {/* Inbox tray */}
          <rect x="5" y="8" width="30" height="24" rx="4" stroke="currentColor" strokeWidth="1.5" fill="none" className="text-muted-foreground/50" />
          {/* Inbox opening */}
          <path d="M5 22h10l2 4h6l2-4h10" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" fill="none" className="text-muted-foreground/50" />
          {/* Down arrow */}
          <path d="M20 4v12M16 12l4 4 4-4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="text-primary" />
        </svg>
      </div>

      <h3 className="text-lg font-semibold text-foreground mb-2">{title}</h3>
      {description && <p className="text-sm mb-6">{description}</p>}
      {action && (
        <Button className="rounded-full" onClick={action.onClick}>
          {action.label}
        </Button>
      )}
    </div>
  );
}
