import { useQuery } from "@tanstack/react-query";
import { getInquiry } from "@/lib/api/client";

export const inquiryKeys = {
  all: ["inquiry"] as const,
  detail: (id: string) => [...inquiryKeys.all, id] as const,
};

export function useInquiry(inquiryId: string) {
  return useQuery({
    queryKey: inquiryKeys.detail(inquiryId),
    queryFn: () => getInquiry(inquiryId),
    staleTime: 60 * 1000,
    enabled: !!inquiryId,
  });
}
