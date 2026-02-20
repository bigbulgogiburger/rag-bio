import { useQuery, keepPreviousData } from "@tanstack/react-query";
import { listInquiries, type InquiryListParams } from "@/lib/api/client";

export const inquiriesKeys = {
  all: ["inquiries"] as const,
  lists: () => [...inquiriesKeys.all, "list"] as const,
  list: (params: InquiryListParams) =>
    [...inquiriesKeys.lists(), params] as const,
};

export function useInquiries(params: InquiryListParams = {}) {
  return useQuery({
    queryKey: inquiriesKeys.list(params),
    queryFn: () => listInquiries(params),
    staleTime: 30 * 1000,
    placeholderData: keepPreviousData,
  });
}
