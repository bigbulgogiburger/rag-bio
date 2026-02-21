import { Suspense } from "react";
import InquiryDetailClient from "./InquiryDetailClient";

export function generateStaticParams() {
  return [{ id: "_" }];
}

export default function InquiryDetailPage() {
  return (
    <Suspense fallback={
      <div className="flex items-center justify-center p-12 text-sm text-muted-foreground">
        불러오는 중...
      </div>
    }>
      <InquiryDetailClient />
    </Suspense>
  );
}
