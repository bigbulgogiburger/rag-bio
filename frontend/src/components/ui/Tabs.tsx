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
  listClassName?: string;
}

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
              'px-4 sm:px-6 py-2.5 text-sm font-medium text-muted-foreground/70',
              'border-b-2 border-transparent -mb-px',
              'transition-all whitespace-nowrap rounded-t-md',
              'hover:text-foreground hover:bg-muted/50',
              'data-[state=active]:text-primary data-[state=active]:border-primary data-[state=active]:font-semibold data-[state=active]:bg-primary/5',
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
          className="focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 rounded-md animate-in fade-in duration-200"
        >
          {tab.content}
        </TabsPrimitive.Content>
      ))}
    </TabsPrimitive.Root>
  );
}
