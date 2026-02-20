import { useQuery } from "@tanstack/react-query";
import { listAnswerDraftHistory } from "@/lib/api/client";

export const answerHistoryKeys = {
  all: ["answerHistory"] as const,
  list: (inquiryId: string) =>
    [...answerHistoryKeys.all, inquiryId] as const,
};

export function useAnswerHistory(
  inquiryId: string,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: answerHistoryKeys.list(inquiryId),
    queryFn: () => listAnswerDraftHistory(inquiryId),
    staleTime: 60 * 1000,
    enabled: options?.enabled !== false && !!inquiryId,
  });
}
