import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  draftInquiryAnswer,
  type AnswerTone,
  type AnswerChannel,
} from "@/lib/api/client";
import { answerDraftKeys } from "@/hooks/queries/useAnswerDraft";
import { answerHistoryKeys } from "@/hooks/queries/useAnswerHistory";

interface GenerateDraftParams {
  inquiryId: string;
  question: string;
  tone?: AnswerTone;
  channel?: AnswerChannel;
}

export function useGenerateDraft() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ inquiryId, question, tone, channel }: GenerateDraftParams) =>
      draftInquiryAnswer(inquiryId, question, tone, channel),
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
