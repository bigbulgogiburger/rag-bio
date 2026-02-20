import { useQuery } from "@tanstack/react-query";
import { getOpsMetrics } from "@/lib/api/client";

export const dashboardKeys = {
  all: ["dashboard"] as const,
  metrics: () => [...dashboardKeys.all, "metrics"] as const,
};

export function useDashboardMetrics() {
  return useQuery({
    queryKey: dashboardKeys.metrics(),
    queryFn: getOpsMetrics,
    staleTime: 30 * 1000,
  });
}
