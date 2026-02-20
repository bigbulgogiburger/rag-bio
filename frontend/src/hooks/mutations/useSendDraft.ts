import { useMutation, useQueryClient } from "@tanstack/react-query";
import { sendAnswerDraft, type AnswerChannel } from "@/lib/api/client";
import { answerDraftKeys } from "@/hooks/queries/useAnswerDraft";
import { answerHistoryKeys } from "@/hooks/queries/useAnswerHistory";
import { inquiriesKeys } from "@/hooks/queries/useInquiries";

interface SendDraftParams {
  inquiryId: string;
  answerId: string;
  actor?: string;
  channel?: AnswerChannel;
  sendRequestId?: string;
}

export function useSendDraft() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      inquiryId,
      answerId,
      actor,
      channel,
      sendRequestId,
    }: SendDraftParams) =>
      sendAnswerDraft(inquiryId, answerId, actor, channel, sendRequestId),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: answerDraftKeys.latest(variables.inquiryId),
      });
      queryClient.invalidateQueries({
        queryKey: answerHistoryKeys.list(variables.inquiryId),
      });
      queryClient.invalidateQueries({ queryKey: inquiriesKeys.all });
    },
  });
}
