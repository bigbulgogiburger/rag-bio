import { uploadImage } from '@/lib/api/client';

export async function handleImageUpload(file: File, inquiryId: string): Promise<string> {
  if (!['image/png', 'image/jpeg'].includes(file.type)) {
    throw new Error('PNG 또는 JPEG 이미지만 지원됩니다');
  }
  if (file.size > 5 * 1024 * 1024) {
    throw new Error('이미지 크기는 5MB 이하여야 합니다');
  }

  const result = await uploadImage(file, inquiryId);
  return result.url;
}
