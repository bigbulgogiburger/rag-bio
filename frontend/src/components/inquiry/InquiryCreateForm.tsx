"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  createInquiry,
  uploadInquiryDocument,
} from "@/lib/api/client";
import { labelDocStatus, labelTone, PRODUCT_FAMILY_LABELS, labelProductFamily, IMAGE_LABELS } from "@/lib/i18n/labels";
import { Toast, Badge } from "@/components/ui";
import { Button } from "@/components/ui/button";

const inquirySchema = z.object({
  question: z.string().min(1, "질문을 입력해 주세요").min(10, "최소 10자 이상 입력해 주세요"),
  customerChannel: z.enum(["email", "messenger", "portal"]),
  answerTone: z.enum(["professional", "technical", "brief", "gilseon"]),
  productFamilies: z.array(z.string()).max(3).optional(),
});

type InquiryFormData = z.infer<typeof inquirySchema>;

const isImageFile = (file: File) => file.type.startsWith("image/");
const MAX_IMAGE_SIZE = 20 * 1024 * 1024; // 20MB

function getFileTypeBadge(file: File): { label: string; variant: "info" | "neutral" } {
  if (isImageFile(file)) return { label: "이미지", variant: "info" };
  if (file.name.toLowerCase().endsWith(".pdf")) return { label: "PDF", variant: "neutral" };
  return { label: "DOCX", variant: "neutral" };
}

function DocumentIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="48"
      height="48"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="text-muted-foreground"
      aria-hidden="true"
    >
      <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="16" x2="8" y1="13" y2="13" />
      <line x1="16" x2="8" y1="17" y2="17" />
      <polyline points="10 9 9 9 8 9" />
    </svg>
  );
}

function ClockIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="shrink-0"
    >
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </svg>
  );
}

export default function InquiryCreateForm() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [imagePreviewUrl, setImagePreviewUrl] = useState<string | null>(null);
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

  // Revoke object URL on cleanup to prevent memory leaks
  useEffect(() => {
    return () => {
      if (imagePreviewUrl) {
        URL.revokeObjectURL(imagePreviewUrl);
      }
    };
  }, [imagePreviewUrl]);

  const handleFileChange = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    const selected = event.target.files?.[0] ?? null;

    // Revoke previous preview URL
    if (imagePreviewUrl) {
      URL.revokeObjectURL(imagePreviewUrl);
      setImagePreviewUrl(null);
    }

    if (selected && isImageFile(selected) && selected.size > MAX_IMAGE_SIZE) {
      setToast({
        message: IMAGE_LABELS.imageSizeExceeded,
        variant: "error",
      });
      setFile(null);
      event.target.value = "";
      return;
    }

    setFile(selected);

    if (selected && isImageFile(selected)) {
      setImagePreviewUrl(URL.createObjectURL(selected));
    }
  }, [imagePreviewUrl]);

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
      if (imagePreviewUrl) {
        URL.revokeObjectURL(imagePreviewUrl);
        setImagePreviewUrl(null);
      }
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

      <form className="rounded-xl border bg-card p-4 shadow-sm space-y-6 sm:p-6" onSubmit={handleSubmit(onSubmit)}>
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
          <h3 className="text-base font-semibold">파일 첨부</h3>
          <p className="text-xs text-muted-foreground">PDF, DOC, DOCX, PNG, JPG, WEBP 형식을 지원합니다.</p>
          <label className="flex flex-col gap-1.5 text-sm font-medium">
            <div className="rounded-lg border-2 border-dashed border-border p-6 text-center transition-colors hover:border-primary/50 hover:bg-primary/5 cursor-pointer">
              <input
                type="file"
                accept=".pdf,.doc,.docx,image/png,image/jpeg,image/webp"
                onChange={handleFileChange}
                aria-label="파일 선택"
              />
              {!file && (
                <p className="text-sm text-muted-foreground">클릭하여 파일을 선택하거나 드래그하세요</p>
              )}
            </div>
          </label>

          {/* File preview */}
          {file && (
            <div className="flex items-start gap-3 rounded-lg border border-border bg-secondary/30 p-3">
              {/* Thumbnail or document icon */}
              <div className="shrink-0">
                {isImageFile(file) && imagePreviewUrl ? (
                  <div className="overflow-hidden rounded-md border border-border">
                    <img
                      src={imagePreviewUrl}
                      alt={file.name}
                      style={{ width: 48, height: 48, objectFit: "cover" }}
                      className="rounded-md"
                    />
                  </div>
                ) : (
                  <DocumentIcon />
                )}
              </div>

              {/* File info */}
              <div className="flex-1 min-w-0 space-y-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-sm font-medium truncate">{file.name}</span>
                  <Badge variant={getFileTypeBadge(file).variant}>
                    {getFileTypeBadge(file).label}
                  </Badge>
                </div>
                <p className="text-xs text-muted-foreground">
                  {file.size < 1024 * 1024
                    ? `${(file.size / 1024).toFixed(1)} KB`
                    : `${(file.size / (1024 * 1024)).toFixed(1)} MB`}
                </p>

                {/* Image analysis status placeholder */}
                {isImageFile(file) && (
                  <div className="flex items-center gap-1.5 text-xs text-muted-foreground mt-1">
                    <ClockIcon />
                    <span>{IMAGE_LABELS.imageAnalysisPending}</span>
                  </div>
                )}
              </div>

              {/* Remove button */}
              <button
                type="button"
                className="shrink-0 rounded-md p-1 text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
                onClick={() => {
                  if (imagePreviewUrl) {
                    URL.revokeObjectURL(imagePreviewUrl);
                    setImagePreviewUrl(null);
                  }
                  setFile(null);
                }}
                aria-label="파일 제거"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
              </button>
            </div>
          )}
        </div>

        <hr className="border-t border-border" />

        <div className="sticky bottom-20 z-10 -mx-4 bg-card px-4 py-3 sm:static sm:mx-0 sm:bg-transparent sm:px-0 sm:py-0 md:bottom-0">
          <Button
            size="lg"
            className="w-full rounded-full"
            type="submit"
            disabled={isSubmitting}
          >
            {isSubmitting ? "등록 중..." : "문의 등록"}
          </Button>
        </div>
      </form>
    </div>
  );
}
