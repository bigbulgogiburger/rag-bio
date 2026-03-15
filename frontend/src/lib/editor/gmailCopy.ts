import juice from 'juice';
import { EMAIL_STYLES } from './emailStyles';

/** TipTap HTML -> Gmail 호환 인라인 스타일 HTML 변환 */
export function convertToInlineStyles(html: string): string {
  const cssRules = `
    h2 { ${EMAIL_STYLES.heading2} }
    h3 { ${EMAIL_STYLES.heading3} }
    p { ${EMAIL_STYLES.paragraph} }
    img { ${EMAIL_STYLES.image} }
    a { ${EMAIL_STYLES.link} }
    blockquote { ${EMAIL_STYLES.blockquote} }
    ul { ${EMAIL_STYLES.bulletList} }
    ol { ${EMAIL_STYLES.orderedList} }
    li { ${EMAIL_STYLES.listItem} }
    table { ${EMAIL_STYLES.table} }
    td { ${EMAIL_STYLES.tableCell} }
    th { ${EMAIL_STYLES.tableHeaderCell} }
    strong, b { ${EMAIL_STYLES.bold} }
    em, i { ${EMAIL_STYLES.italic} }
  `;

  const fullHtml = `<style>${cssRules}</style>${html}`;
  return juice(fullHtml, {
    removeStyleTags: true,
    preserveImportant: false,
  });
}

/** 이메일용 HTML wrapper */
export function wrapForEmail(content: string): string {
  return `<div style="${EMAIL_STYLES.wrapper}">${content}</div>`;
}

/** HTML -> plain text 변환 (fallback용) */
export function stripHtml(html: string): string {
  if (typeof window === 'undefined') return html.replace(/<[^>]*>/g, '');
  const doc = new DOMParser().parseFromString(html, 'text/html');
  return doc.body.textContent || '';
}

export interface EmailValidationResult {
  isValid: boolean;
  warnings: string[];
}

/** 이메일 HTML 유효성 검증 */
export function validateEmailHtml(html: string): EmailValidationResult {
  const warnings: string[] = [];

  // 102KB 제한 확인
  const sizeKB = new Blob([html]).size / 1024;
  if (sizeKB > 102) {
    warnings.push(`HTML 크기가 ${Math.round(sizeKB)}KB입니다. Gmail은 102KB 이상을 잘라냅니다.`);
  }

  // base64 이미지 확인
  if (html.includes('data:image/')) {
    warnings.push('base64 이미지가 포함되어 있습니다. Gmail에서 표시되지 않습니다.');
  }

  // 외부 CSS 확인
  if (html.includes('<link') || html.includes('@import')) {
    warnings.push('외부 CSS가 포함되어 있습니다. Gmail에서 무시됩니다.');
  }

  // SVG 이미지 확인
  if (html.includes('<svg') || html.includes('.svg')) {
    warnings.push('SVG 이미지가 포함되어 있습니다. Gmail에서 차단됩니다.');
  }

  return {
    isValid: warnings.length === 0,
    warnings,
  };
}

/** Gmail 클립보드 복사 (핵심 기능) */
export async function copyForGmail(editorHtml: string): Promise<EmailValidationResult> {
  // 1. inline style 변환
  const inlinedHtml = convertToInlineStyles(editorHtml);

  // 2. 이메일 wrapper 적용
  const emailHtml = wrapForEmail(inlinedHtml);

  // 3. 유효성 검증
  const validation = validateEmailHtml(emailHtml);

  // 4. 클립보드 복사 (HTML + plain text 동시)
  try {
    const htmlBlob = new Blob([emailHtml], { type: 'text/html' });
    const plainBlob = new Blob([stripHtml(emailHtml)], { type: 'text/plain' });

    await navigator.clipboard.write([
      new ClipboardItem({
        'text/html': htmlBlob,
        'text/plain': plainBlob,
      }),
    ]);
  } catch {
    // Clipboard API 미지원 시 fallback
    const textarea = document.createElement('textarea');
    textarea.value = stripHtml(emailHtml);
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
    validation.warnings.push('HTML 복사가 지원되지 않아 텍스트로 복사되었습니다.');
  }

  return validation;
}
