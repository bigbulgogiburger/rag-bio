import { useQuery } from "@tanstack/react-query";
import {
  listInquiryDocuments,
  getInquiryIndexingStatus,
} from "@/lib/api/client";

export const inquiryDocumentsKeys = {
  all: ["inquiryDocuments"] as const,
  list: (inquiryId: string) =>
    [...inquiryDocumentsKeys.all, "list", inquiryId] as const,
  indexingStatus: (inquiryId: string) =>
    [...inquiryDocumentsKeys.all, "indexingStatus", inquiryId] as const,
};

export function useInquiryDocuments(inquiryId: string) {
  return useQuery({
    queryKey: inquiryDocumentsKeys.list(inquiryId),
    queryFn: () => listInquiryDocuments(inquiryId),
    staleTime: 30 * 1000,
    enabled: !!inquiryId,
  });
}

export function useInquiryIndexingStatus(
  inquiryId: string,
  options?: { enabled?: boolean; refetchInterval?: number | false },
) {
  return useQuery({
    queryKey: inquiryDocumentsKeys.indexingStatus(inquiryId),
    queryFn: () => getInquiryIndexingStatus(inquiryId),
    staleTime: 10 * 1000,
    enabled: options?.enabled !== false && !!inquiryId,
    refetchInterval: options?.refetchInterval,
  });
}
