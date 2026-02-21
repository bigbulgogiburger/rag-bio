"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function InquiryFormPage() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/inquiries/new");
  }, [router]);
  return null;
}
