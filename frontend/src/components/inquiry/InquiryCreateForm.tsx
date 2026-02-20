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
import { labelDocStatus, labelTone } from "@/lib/i18n/labels";
import { Toast } from "@/components/ui";
import { Button } from "@/components/ui/button";

const inquirySchema = z.object({
  question: z.string().min(1, "질문을 입력해 주세요").min(10, "최소 10자 이상 입력해 주세요"),
  customerChannel: z.enum(["email", "messenger", "portal"]),
  answerTone: z.enum(["professional", "technical", "brief"]),
});

type InquiryFormData = z.infer<typeof inquirySchema>;

export default function InquiryCreateForm() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [toast, setToast] = useState<{ message: string; variant: "success" | "error" | "warn" | "info" } | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<InquiryFormData>({
    resolver: zodResolver(inquirySchema),
    defaultValues: { question: "", customerChannel: "email", answerTone: "professional" },
  });

  const onSubmit = async (data: InquiryFormData) => {
    setToast(null);

    try {
      const inquiry = await createInquiry({
        question: data.question,
        customerChannel: data.customerChannel,
        preferredTone: data.answerTone,
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

      // Redirect immediately to answer tab
      router.push(`/inquiries/${inquiry.inquiryId}?tab=answer`);
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
                {(["professional", "technical", "brief"] as const).map((tone) => (
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
