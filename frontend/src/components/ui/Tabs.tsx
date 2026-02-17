'use client';

import { useState, useRef, useCallback } from 'react';

interface Tab {
  key: string;
  label: string;
  content: React.ReactNode;
}

interface TabsProps {
  tabs: Tab[];
  defaultTab?: string;
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
export default function Tabs({ tabs, defaultTab }: TabsProps) {
  const [activeTab, setActiveTab] = useState(defaultTab || tabs[0]?.key);
  const tabRefs = useRef<Map<string, HTMLButtonElement>>(new Map());

  const setTabRef = useCallback((key: string) => (el: HTMLButtonElement | null) => {
    if (el) {
      tabRefs.current.set(key, el);
    } else {
      tabRefs.current.delete(key);
    }
  }, []);

  const focusTab = (key: string) => {
    tabRefs.current.get(key)?.focus();
  };

  const handleKeyDown = (e: React.KeyboardEvent, tabKey: string) => {
    const currentIndex = tabs.findIndex((t) => t.key === activeTab);
    let targetKey: string | undefined;

    if (e.key === 'ArrowLeft') {
      e.preventDefault();
      const prevIndex = currentIndex > 0 ? currentIndex - 1 : tabs.length - 1;
      targetKey = tabs[prevIndex].key;
    } else if (e.key === 'ArrowRight') {
      e.preventDefault();
      const nextIndex = currentIndex < tabs.length - 1 ? currentIndex + 1 : 0;
      targetKey = tabs[nextIndex].key;
    } else if (e.key === 'Home') {
      e.preventDefault();
      targetKey = tabs[0].key;
    } else if (e.key === 'End') {
      e.preventDefault();
      targetKey = tabs[tabs.length - 1].key;
    } else if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      setActiveTab(tabKey);
      return;
    }

    if (targetKey) {
      setActiveTab(targetKey);
      focusTab(targetKey);
    }
  };

  const activeContent = tabs.find((tab) => tab.key === activeTab)?.content;

  return (
    <div>
      <div
        className="tabs-header"
        role="tablist"
        aria-label="탭 목록"
        aria-orientation="horizontal"
      >
        {tabs.map((tab) => (
          <button
            key={tab.key}
            ref={setTabRef(tab.key)}
            className={`tab-button ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
            onKeyDown={(e) => handleKeyDown(e, tab.key)}
            role="tab"
            aria-selected={activeTab === tab.key}
            aria-controls={`tabpanel-${tab.key}`}
            id={`tab-${tab.key}`}
            tabIndex={activeTab === tab.key ? 0 : -1}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div
        role="tabpanel"
        id={`tabpanel-${activeTab}`}
        aria-labelledby={`tab-${activeTab}`}
        tabIndex={0}
      >
        {activeContent}
      </div>
    </div>
  );
}
