import { useMutation, useQueryClient } from "@tanstack/react-query";
import { uploadInquiryDocument } from "@/lib/api/client";
import { inquiryDocumentsKeys } from "@/hooks/queries/useInquiryDocuments";

export function useUploadDocument(inquiryId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (file: File) => uploadInquiryDocument(inquiryId, file),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: inquiryDocumentsKeys.list(inquiryId),
      });
      queryClient.invalidateQueries({
        queryKey: inquiryDocumentsKeys.indexingStatus(inquiryId),
      });
    },
  });
}
