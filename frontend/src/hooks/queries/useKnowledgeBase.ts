import { useQuery, keepPreviousData } from "@tanstack/react-query";
import {
  listKbDocuments,
  getKbDocument,
  getKbStats,
  type KbDocumentListParams,
} from "@/lib/api/client";

export const kbKeys = {
  all: ["knowledgeBase"] as const,
  lists: () => [...kbKeys.all, "list"] as const,
  list: (params: KbDocumentListParams) =>
    [...kbKeys.lists(), params] as const,
  detail: (docId: string) => [...kbKeys.all, "detail", docId] as const,
  stats: () => [...kbKeys.all, "stats"] as const,
};

export function useKbDocuments(params: KbDocumentListParams = {}) {
  return useQuery({
    queryKey: kbKeys.list(params),
    queryFn: () => listKbDocuments(params),
    staleTime: 30 * 1000,
    placeholderData: keepPreviousData,
  });
}

export function useKbDocument(docId: string) {
  return useQuery({
    queryKey: kbKeys.detail(docId),
    queryFn: () => getKbDocument(docId),
    staleTime: 60 * 1000,
    enabled: !!docId,
  });
}

export function useKbStats() {
  return useQuery({
    queryKey: kbKeys.stats(),
    queryFn: getKbStats,
    staleTime: 30 * 1000,
  });
}
