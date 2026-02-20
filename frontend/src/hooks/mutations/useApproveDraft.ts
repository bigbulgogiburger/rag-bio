import { useMutation, useQueryClient } from "@tanstack/react-query";
import { approveAnswerDraft } from "@/lib/api/client";
import { answerDraftKeys } from "@/hooks/queries/useAnswerDraft";
import { answerHistoryKeys } from "@/hooks/queries/useAnswerHistory";

interface ApproveDraftParams {
  inquiryId: string;
  answerId: string;
  actor?: string;
  comment?: string;
}

export function useApproveDraft() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      inquiryId,
      answerId,
      actor,
      comment,
    }: ApproveDraftParams) =>
      approveAnswerDraft(inquiryId, answerId, actor, comment),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: answerDraftKeys.latest(variables.inquiryId),
      });
      queryClient.invalidateQueries({
        queryKey: answerHistoryKeys.list(variables.inquiryId),
      });
    },
  });
}
