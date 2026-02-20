import { useQuery } from "@tanstack/react-query";
import { getLatestAnswerDraft } from "@/lib/api/client";

export const answerDraftKeys = {
  all: ["answerDraft"] as const,
  latest: (inquiryId: string) =>
    [...answerDraftKeys.all, "latest", inquiryId] as const,
};

export function useLatestAnswerDraft(
  inquiryId: string,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: answerDraftKeys.latest(inquiryId),
    queryFn: () => getLatestAnswerDraft(inquiryId),
    staleTime: 60 * 1000,
    enabled: options?.enabled !== false && !!inquiryId,
  });
}
