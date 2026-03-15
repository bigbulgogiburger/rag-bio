import { useState, useEffect } from 'react';
import { getPipelineStatus, type PipelineStatusResult } from '@/lib/api/client';

export function usePipelineStatus(inquiryId: string) {
  const [pipelineStatus, setPipelineStatus] = useState<PipelineStatusResult | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function fetchStatus() {
      try {
        const data = await getPipelineStatus(inquiryId);
        if (!cancelled) setPipelineStatus(data);
      } catch {
        if (!cancelled) setPipelineStatus({ status: 'IDLE', steps: [] });
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }
    fetchStatus();
    return () => { cancelled = true; };
  }, [inquiryId]);

  return { pipelineStatus, isLoading, setPipelineStatus };
}
