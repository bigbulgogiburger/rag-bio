'use client';

import * as TabsPrimitive from '@radix-ui/react-tabs';
import { cn } from '@/lib/utils';

interface Tab {
  key: string;
  label: string;
  content: React.ReactNode;
}

interface TabsProps {
  tabs: Tab[];
  defaultTab?: string;
  /** Additional className for the tab list (e.g. sticky positioning) */
  listClassName?: string;
}

/**
 * Tabs 컴포넌트
 *
 * 사용 예:
 * <Tabs
 *   defaultTab="info"
 *   tabs={[
 *     { key: 'info', label: '기본 정보', content: <InfoTab /> },
 *     { key: 'analysis', label: '분석', content: <AnalysisTab /> },
 *   ]}
 * />
 */
export default function Tabs({ tabs, defaultTab, listClassName }: TabsProps) {
  return (
    <TabsPrimitive.Root defaultValue={defaultTab || tabs[0]?.key}>
      <TabsPrimitive.List
        className={cn("flex border-b border-border mb-6", listClassName)}
        aria-label="탭 목록"
      >
        {tabs.map((tab) => (
          <TabsPrimitive.Trigger
            key={tab.key}
            value={tab.key}
            className={cn(
              'px-6 py-2 text-sm font-medium text-muted-foreground',
              'border-b-2 border-transparent -mb-px',
              'transition-colors whitespace-nowrap',
              'hover:text-foreground hover:bg-muted/50',
              'data-[state=active]:text-primary data-[state=active]:border-primary data-[state=active]:font-semibold',
            )}
          >
            {tab.label}
          </TabsPrimitive.Trigger>
        ))}
      </TabsPrimitive.List>

      {tabs.map((tab) => (
        <TabsPrimitive.Content
          key={tab.key}
          value={tab.key}
          tabIndex={0}
          className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 rounded-md"
        >
          {tab.content}
        </TabsPrimitive.Content>
      ))}
    </TabsPrimitive.Root>
  );
}
