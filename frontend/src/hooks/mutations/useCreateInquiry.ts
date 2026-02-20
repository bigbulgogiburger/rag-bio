import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createInquiry, type CreateInquiryPayload } from "@/lib/api/client";
import { inquiriesKeys } from "@/hooks/queries/useInquiries";

export function useCreateInquiry() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: CreateInquiryPayload) => createInquiry(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: inquiriesKeys.all });
    },
  });
}
