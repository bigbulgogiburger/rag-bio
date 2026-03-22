import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const badgeVariants = cva(
  'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold tracking-tight border whitespace-nowrap leading-relaxed',
  {
    variants: {
      variant: {
        info: 'bg-blue-50 text-sky-700 border-blue-200 dark:bg-blue-950 dark:text-sky-300 dark:border-blue-800',
        success: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950 dark:text-emerald-300 dark:border-emerald-800',
        warn: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-950 dark:text-amber-300 dark:border-amber-800',
        danger: 'bg-red-50 text-red-700 border-red-200 dark:bg-red-950 dark:text-red-300 dark:border-red-800',
        neutral: 'bg-slate-50 text-slate-500 border-slate-200 dark:bg-slate-900 dark:text-slate-400 dark:border-slate-700',
      },
    },
    defaultVariants: {
      variant: 'neutral',
    },
  }
);

interface BadgeProps {
  variant: 'info' | 'success' | 'warn' | 'danger' | 'neutral';
  children: React.ReactNode;
  style?: React.CSSProperties;
  className?: string;
}

export default function Badge({ variant, children, style, className }: BadgeProps) {
  return (
    <span className={cn(badgeVariants({ variant }), className)} role="status" style={style}>
      <svg
        width="7"
        height="7"
        viewBox="0 0 7 7"
        fill="none"
        aria-hidden="true"
        className={cn("shrink-0", variant === "warn" && "animate-pulse")}
      >
        <circle cx="3.5" cy="3.5" r="3.5" fill="currentColor" />
      </svg>
      {children}
    </span>
  );
}
