"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  createInquiry,
  uploadInquiryDocument,
} from "@/lib/api/client";
import { labelDocStatus, labelTone, PRODUCT_FAMILY_LABELS, labelProductFamily } from "@/lib/i18n/labels";
import { Toast } from "@/components/ui";
import { Button } from "@/components/ui/button";

const inquirySchema = z.object({
  question: z.string().min(1, "질문을 입력해 주세요").min(10, "최소 10자 이상 입력해 주세요"),
  customerChannel: z.enum(["email", "messenger", "portal"]),
  answerTone: z.enum(["professional", "technical", "brief", "gilseon"]),
  productFamilies: z.array(z.string()).max(3).optional(),
});

type InquiryFormData = z.infer<typeof inquirySchema>;

export default function InquiryCreateForm() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [toast, setToast] = useState<{ message: string; variant: "success" | "error" | "warn" | "info" } | null>(null);
  const [selectedProductFamilies, setSelectedProductFamilies] = useState<string[]>([]);
  const [pfSelectValue, setPfSelectValue] = useState("");

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<InquiryFormData>({
    resolver: zodResolver(inquirySchema),
    defaultValues: { question: "", customerChannel: "email", answerTone: "gilseon" },
  });

  const onSubmit = async (data: InquiryFormData) => {
    setToast(null);

    try {
      const inquiry = await createInquiry({
        question: data.question,
        customerChannel: data.customerChannel,
        preferredTone: data.answerTone,
        ...(selectedProductFamilies.length > 0 ? { productFamilies: selectedProductFamilies } : {}),
      });

      if (file) {
        const upload = await uploadInquiryDocument(inquiry.inquiryId, file);
        setToast({
          message: `문의 ${inquiry.inquiryId.slice(0, 8)} 생성 완료 / 파일 ${upload.fileName} 업로드 완료 (${labelDocStatus(upload.status)})`,
          variant: "success",
        });
      } else {
        setToast({
          message: `문의 ${inquiry.inquiryId.slice(0, 8)} 생성 완료 (${labelDocStatus(inquiry.status)})`,
          variant: "success",
        });
      }

      // Reset form
      reset();
      setFile(null);
      setSelectedProductFamilies([]);
      setPfSelectValue("");

      // Redirect immediately to answer tab (use window.location for static export)
      window.location.href = `/inquiries/${inquiry.inquiryId}/?tab=answer`;
    } catch (error) {
      setToast({
        message: error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.",
        variant: "error",
      });
    }
  };

  return (
    <div className="space-y-4">
      {toast && (
        <Toast
          message={toast.message}
          variant={toast.variant}
          onClose={() => setToast(null)}
          duration={toast.variant === "success" ? 2000 : 5000}
        />
      )}

      <form className="rounded-xl border bg-card p-6 shadow-sm space-y-6" onSubmit={handleSubmit(onSubmit)}>
        <h2 className="text-xl font-semibold tracking-tight">새 고객 문의 등록</h2>
        <p className="text-sm text-muted-foreground">고객의 기술 문의를 등록하고 문서를 첨부할 수 있습니다.</p>

        <hr className="border-t border-border" />

        <div className="space-y-4">
          <h3 className="text-base font-semibold">문의 내용</h3>
          <label className="flex flex-col gap-1.5 text-sm font-medium">
            질문
            <textarea
              className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring min-h-[120px] resize-y"
              rows={5}
              placeholder="고객 기술 문의 내용을 입력하세요"
              aria-invalid={!!errors.question}
              aria-describedby={errors.question ? "question-error" : undefined}
              {...register("question")}
            />
            {errors.question && (
              <p id="question-error" className="text-xs text-destructive mt-1" role="alert">{errors.question.message}</p>
            )}
          </label>

          {/* Product Family Tag Picker */}
          <div className="space-y-2">
            <label className="text-sm font-medium">
              관련 제품군 (최대 3개)
            </label>
            <div className="flex items-center gap-2">
              <select
                className="flex-1 rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm"
                value={pfSelectValue}
                onChange={(e) => setPfSelectValue(e.target.value)}
                disabled={selectedProductFamilies.length >= 3}
              >
                <option value="">제품군 선택</option>
                {Object.keys(PRODUCT_FAMILY_LABELS)
                  .filter((key) => !selectedProductFamilies.includes(key))
                  .map((key) => (
                    <option key={key} value={key}>
                      {labelProductFamily(key)}
                    </option>
                  ))}
              </select>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={!pfSelectValue || selectedProductFamilies.length >= 3}
                onClick={() => {
                  if (pfSelectValue && !selectedProductFamilies.includes(pfSelectValue)) {
                    setSelectedProductFamilies((prev) => [...prev, pfSelectValue]);
                    setPfSelectValue("");
                  }
                }}
              >
                추가
              </Button>
            </div>
            {selectedProductFamilies.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {selectedProductFamilies.map((pf) => (
                  <span
                    key={pf}
                    className="inline-flex items-center gap-1 rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-xs font-medium text-primary"
                  >
                    {labelProductFamily(pf)}
                    <button
                      type="button"
                      className="ml-0.5 rounded-full p-0.5 hover:bg-primary/20 transition-colors"
                      onClick={() => setSelectedProductFamilies((prev) => prev.filter((v) => v !== pf))}
                      aria-label={`${labelProductFamily(pf)} 제거`}
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-1.5 text-sm font-medium">
              채널
              <select
                className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm"
                {...register("customerChannel")}
              >
                <option value="email">이메일</option>
                <option value="messenger">메신저</option>
                <option value="portal">포털</option>
              </select>
            </label>

            <label className="flex flex-col gap-1.5 text-sm font-medium">
              답변 톤
              <select
                className="w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm"
                {...register("answerTone")}
              >
                {(["professional", "technical", "brief", "gilseon"] as const).map((tone) => (
                  <option key={tone} value={tone}>
                    {labelTone(tone)}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

        <hr className="border-t border-border" />

        <div className="space-y-4">
          <h3 className="text-base font-semibold">문서 첨부</h3>
          <label className="flex flex-col gap-1.5 text-sm font-medium">
            파일 (PDF/DOC/DOCX)
            <div className="rounded-lg border-2 border-dashed border-border p-6 text-center transition-colors hover:border-primary/50 hover:bg-primary/5 cursor-pointer">
              <input
                type="file"
                accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
              {file ? (
                <p className="text-sm text-muted-foreground">{file.name} ({(file.size / 1024).toFixed(1)} KB)</p>
              ) : (
                <p className="text-sm text-muted-foreground">클릭하여 파일을 선택하거나 드래그하세요</p>
              )}
            </div>
          </label>
        </div>

        <hr className="border-t border-border" />

        <Button
          size="lg"
          className="w-full rounded-full"
          type="submit"
          disabled={isSubmitting}
        >
          {isSubmitting ? "등록 중..." : "문의 등록"}
        </Button>
      </form>
    </div>
  );
}
