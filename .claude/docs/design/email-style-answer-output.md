# Email-Style Answer Output (Gmail 호환 인라인 이미지)

## 1. 현재 상태

| 항목 | 현재 | 목표 |
|------|------|------|
| 답변 포맷 | 순수 텍스트 (`whitespace-pre-wrap`) | Email-ready HTML (inline style + 인라인 이미지) |
| 인용 표시 | `(파일.pdf, p.XX)` 텍스트 | 클릭 가능한 링크 (유지) + 시각적 인용 블록 |
| 이미지 | 미지원 | 인라인 이미지 (hosted URL) |
| 복사/발송 | 텍스트 복사 | "Gmail에 복사" 버튼 → HTML 클립보드 복사 |

## 2. Gmail 이미지 임베딩 핵심 규칙

### 반드시 지켜야 할 것

| 규칙 | 이유 |
|------|------|
| **HTTPS 절대 URL**로 이미지 참조 | Gmail이 `googleusercontent.com` 프록시로 중계 |
| **inline style** 사용 | Gmail은 `<style>` 태그를 16KB까지만 허용, 외부 CSS 완전 미지원 |
| **`<table>` 기반 레이아웃** | flexbox, grid 미지원 |
| **PNG/JPEG 포맷만 사용** | SVG 차단, AVIF 미지원, WebP는 JPEG 변환됨 |
| **`<img>`에 width/height 명시** | 레이아웃 깨짐 방지 |
| **전체 HTML 102KB 미만** | 초과 시 Gmail이 이메일을 잘라냄 (clipping) |

### 절대 하면 안 되는 것

| 금지 사항 | 이유 |
|-----------|------|
| ~~base64 data URI~~ | Gmail에서 **완전 차단** — 이미지 미표시 |
| ~~`background-image` CSS~~ | Gmail 앱에서 미지원 |
| ~~CSS animation/keyframes~~ | 미지원 |
| ~~외부 CSS (`<link>`, `@import`)~~ | 완전 무시됨 |
| ~~상대 경로 이미지~~ | Gmail 프록시가 해석 불가 |

## 3. 아키텍처 설계

### 3.1 전체 흐름

```
[RAG Pipeline] → 텍스트 답변 + 인용 메타데이터
       ↓
[Rich Text Editor] ← 상담원이 편집 (이미지 삽입, 서식 조정)
       ↓
[Email HTML Renderer] → inline style + hosted image URL
       ↓
[클립보드 복사] → Gmail Compose에 붙여넣기
```

### 3.2 이미지 처리 파이프라인

```
상담원이 이미지 드래그/붙여넣기
       ↓
[Frontend] 파일 → FormData POST /api/v1/images/upload
       ↓
[Backend] 서버 저장 → HTTPS URL 반환
       ↓
[Editor] <img src="https://서버/images/{uuid}.png"> 삽입
       ↓
[Gmail 복사 시] 브라우저가 렌더링된 HTML+이미지 URL을 클립보드에 포함
       ↓
[Gmail] 이미지 URL을 프록시로 중계하여 표시
```

## 4. 백엔드 변경사항

### 4.1 이미지 업로드 API (신규)

```
POST /api/v1/images/upload
Content-Type: multipart/form-data

Request:
  - file: MultipartFile (PNG/JPEG, max 5MB)
  - inquiryId: UUID (선택, 연관 문의)

Response:
{
  "imageId": "uuid",
  "url": "https://서버도메인/api/v1/images/{uuid}.png",
  "width": 640,
  "height": 480,
  "format": "png",
  "sizeBytes": 123456
}
```

**구현 위치:**
- `ImageController.java` — REST 엔드포인트
- `ImageStorageService.java` — 파일 저장 (로컬 → 추후 S3/CDN)
- `ImageJpaEntity.java` — 메타데이터 저장
- `V31__create_images_table.sql` — Flyway 마이그레이션

### 4.2 이미지 서빙 API (신규)

```
GET /api/v1/images/{imageId}.{ext}
→ 이미지 바이너리 응답 (Content-Type: image/png 등)
→ Cache-Control: public, max-age=31536000 (1년)
```

### 4.3 답변 HTML 렌더링 (변경)

현재 `draft` 필드는 순수 텍스트. 두 가지 옵션:

**옵션 A: 에디터 HTML을 그대로 저장 (권장)**
- `draft` 필드를 HTML로 저장
- 에디터에서 생성한 HTML이 곧 답변
- 기존 plain text 답변과 구분하기 위해 `draftFormat` 필드 추가 (`TEXT` | `HTML`)

**옵션 B: 텍스트 + 별도 HTML 필드**
- `draft`는 기존 텍스트 유지
- `draftHtml` 필드 추가
- 에디터가 HTML 버전을 별도 저장

→ **옵션 A 권장**: 에디터 도입 후에는 HTML이 정본(source of truth)

### 4.4 DB 마이그레이션

```sql
-- V31__create_images_table.sql
CREATE TABLE images (
    id          UUID PRIMARY KEY,
    inquiry_id  UUID REFERENCES inquiries(id),
    file_name   VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes  BIGINT NOT NULL,
    width       INT,
    height      INT,
    storage_path VARCHAR(500) NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- V32__add_draft_format.sql
ALTER TABLE answer_drafts ADD COLUMN draft_format VARCHAR(10) DEFAULT 'TEXT';
```

## 5. 프론트엔드 변경사항

### 5.1 Rich Text Editor 도입

**선택: TipTap (기존 React 생태계 호환)**

이유:
- React/Next.js 네이티브 지원
- headless UI → 기존 shadcn/ui 디자인 시스템과 자연스러운 통합
- 이미지 extension 기본 제공
- 커스터마이징 자유도 높음
- 번들 사이즈 작음 (에디터 코어 ~45KB gzip)

**대안 고려:**
- CKEditor 5: Email Editing 기능 세트가 성숙하지만, 라이선스 비용 + 번들 크기 + 기존 UI와 이질감
- Quill: base64 기본 삽입이라 Gmail 호환 파이프라인 구현에 더 많은 커스텀 필요

### 5.2 에디터 구성

```typescript
// components/inquiry/AnswerEditor.tsx

import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import Table from '@tiptap/extension-table'

const extensions = [
  StarterKit.configure({
    // 이메일 호환 서식만 활성화
    heading: { levels: [2, 3] },
    bold: true,
    italic: true,
    bulletList: true,
    orderedList: true,
    blockquote: true,
    // 이메일 미지원 기능 비활성화
    codeBlock: false,
    code: false,
    strike: false,
  }),
  Image.configure({
    inline: true,
    allowBase64: false,  // base64 금지
    HTMLAttributes: {
      style: 'max-width: 100%; height: auto;',
    },
  }),
  Link.configure({
    openOnClick: false,
    HTMLAttributes: {
      style: 'color: #1a73e8; text-decoration: underline;',
    },
  }),
  Table.configure({
    // 이메일용 테이블
    HTMLAttributes: {
      style: 'border-collapse: collapse; width: 100%;',
    },
  }),
]
```

### 5.3 이미지 업로드 핸들러

```typescript
// lib/editor/imageUpload.ts

export async function handleImageUpload(file: File, inquiryId: string): Promise<string> {
  // 1. 유효성 검사
  if (!['image/png', 'image/jpeg'].includes(file.type)) {
    throw new Error('PNG 또는 JPEG 이미지만 지원됩니다')
  }
  if (file.size > 5 * 1024 * 1024) {
    throw new Error('이미지 크기는 5MB 이하여야 합니다')
  }

  // 2. 서버 업로드
  const formData = new FormData()
  formData.append('file', file)
  formData.append('inquiryId', inquiryId)

  const response = await fetch('/api/v1/images/upload', {
    method: 'POST',
    body: formData,
  })

  if (!response.ok) throw new Error('이미지 업로드 실패')

  const { url } = await response.json()
  return url  // HTTPS 절대 URL
}
```

### 5.4 에디터에서 이미지 삽입

```typescript
// 드래그 앤 드롭
editor.setOptions({
  editorProps: {
    handleDrop: (view, event, slice, moved) => {
      const files = event.dataTransfer?.files
      if (files?.length) {
        for (const file of files) {
          if (file.type.startsWith('image/')) {
            handleImageUpload(file, inquiryId).then(url => {
              editor.chain().focus().setImage({ src: url }).run()
            })
            return true
          }
        }
      }
      return false
    },
    handlePaste: (view, event) => {
      const items = event.clipboardData?.items
      if (items) {
        for (const item of items) {
          if (item.type.startsWith('image/')) {
            const file = item.getAsFile()
            if (file) {
              handleImageUpload(file, inquiryId).then(url => {
                editor.chain().focus().setImage({ src: url }).run()
              })
              return true
            }
          }
        }
      }
      return false
    },
  },
})
```

### 5.5 Gmail 복사 기능

```typescript
// lib/editor/gmailCopy.ts

export async function copyForGmail(editorHtml: string): Promise<void> {
  // 1. HTML을 inline style로 변환
  const inlinedHtml = convertToInlineStyles(editorHtml)

  // 2. 이메일 호환 wrapper 적용
  const emailHtml = wrapForEmail(inlinedHtml)

  // 3. 클립보드에 HTML로 복사
  const blob = new Blob([emailHtml], { type: 'text/html' })
  const plainBlob = new Blob([stripHtml(emailHtml)], { type: 'text/plain' })

  await navigator.clipboard.write([
    new ClipboardItem({
      'text/html': blob,
      'text/plain': plainBlob,  // fallback
    }),
  ])
}

function convertToInlineStyles(html: string): string {
  // TipTap 클래스 → inline style 변환
  // 예: .ProseMirror img → style="max-width:100%; height:auto;"
  // juice 라이브러리 사용 권장 (npm install juice)
  return juice(html, { /* options */ })
}

function wrapForEmail(content: string): string {
  return `
    <div style="font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.6; color: #202124;">
      ${content}
    </div>
  `
}
```

### 5.6 UI 레이아웃 변경 (InquiryAnswerTab)

```
┌──────────────────────────────────────────────────┐
│ [답변 초안 생성] 버튼                              │
│                                                   │
│ ┌───────────────────────────────────────────────┐ │
│ │ 답변 메타 (Verdict | Confidence | Tone)       │ │
│ └───────────────────────────────────────────────┘ │
│                                                   │
│ ┌───────────────────────────────────────────────┐ │
│ │ TipTap Editor Toolbar                         │ │
│ │ [B] [I] [H2] [H3] [•] [1.] [🔗] [📷] [📋→]  │ │
│ ├───────────────────────────────────────────────┤ │
│ │                                               │ │
│ │  답변 내용 (WYSIWYG)                          │ │
│ │                                               │ │
│ │  Bio-Rad의 QX200™ 시스템은...                  │ │
│ │                                               │ │
│ │  ┌─────────────────────────┐                  │ │
│ │  │  [인라인 이미지]          │                  │ │
│ │  │  (서버 hosted URL)       │                  │ │
│ │  └─────────────────────────┘                  │ │
│ │                                               │ │
│ │  자세한 내용은 매뉴얼을 참조하세요              │ │
│ │  (10000107223.pdf, p.94-95) ← 클릭 가능       │ │
│ │                                               │ │
│ └───────────────────────────────────────────────┘ │
│                                                   │
│ [Gmail에 복사 📋]  [검토 요청]  [승인]             │
└──────────────────────────────────────────────────┘
```

**[📋→] = "Gmail에 복사" 툴바 버튼**: 에디터 콘텐츠를 inline-styled HTML로 변환하여 클립보드에 복사

## 6. RAG 파이프라인 연동

### 6.1 Compose Step 출력 변환

RAG 파이프라인의 Compose Step은 현재 plain text를 출력. 이를 에디터에 로드하는 방식:

```typescript
// 답변 생성 후 에디터에 로드
const onDraftGenerated = (draft: AnswerDraftResult) => {
  if (draft.draftFormat === 'TEXT') {
    // 기존 plain text → 기본 HTML 변환
    const html = convertPlainTextToHtml(draft.draft)
    editor.commands.setContent(html)
  } else {
    // HTML 답변 그대로 로드
    editor.commands.setContent(draft.draft)
  }
}

function convertPlainTextToHtml(text: string): string {
  // 단락 분리
  return text
    .split('\n\n')
    .map(paragraph => `<p>${paragraph.replace(/\n/g, '<br>')}</p>`)
    .join('')
}
```

### 6.2 인용 → 하이퍼링크 변환

```typescript
function convertCitationsToLinks(html: string, evidences: EvidenceItem[]): string {
  // (파일.pdf, p.XX-YY) 패턴을 <a> 태그로 변환
  const citationRegex = /\(([^,가-힣\n]+\.pdf),\s*p\.(\d+)(?:-(\d+))?\)/gi

  return html.replace(citationRegex, (match, fileName, pageStart, pageEnd) => {
    const evidence = evidences.find(e => e.fileName === fileName)
    if (!evidence) return match

    const label = pageEnd ? `${fileName}, p.${pageStart}-${pageEnd}` : `${fileName}, p.${pageStart}`
    return `<a href="#" data-doc-id="${evidence.documentId}" data-page="${pageStart}" style="color: #1a73e8; text-decoration: underline; font-size: 12px;">[${label}]</a>`
  })
}
```

## 7. 의존성 추가

### Backend

```gradle
// build.gradle (app-api)
implementation 'org.springframework.boot:spring-boot-starter-web' // 이미 있음
// 이미지 리사이즈가 필요한 경우:
// implementation 'net.coobird:thumbnailator:0.4.20'
```

### Frontend

```bash
npm install @tiptap/react @tiptap/starter-kit @tiptap/extension-image \
  @tiptap/extension-link @tiptap/extension-table @tiptap/pm juice
```

| 패키지 | 용도 | 크기 (gzip) |
|--------|------|-------------|
| `@tiptap/react` | React 바인딩 | ~5KB |
| `@tiptap/starter-kit` | 기본 서식 | ~40KB |
| `@tiptap/extension-image` | 인라인 이미지 | ~2KB |
| `@tiptap/extension-link` | 하이퍼링크 | ~3KB |
| `@tiptap/extension-table` | 테이블 레이아웃 | ~5KB |
| `juice` | CSS inline 변환 | ~15KB |

## 8. 구현 단계

### Phase 1: 이미지 인프라 (백엔드)
1. `V31__create_images_table.sql` 마이그레이션
2. `ImageJpaEntity`, `ImageController`, `ImageStorageService` 구현
3. 이미지 서빙 엔드포인트 (GET + 캐싱 헤더)

### Phase 2: Rich Text Editor (프론트엔드)
1. TipTap 패키지 설치 + `AnswerEditor.tsx` 컴포넌트 구현
2. 이미지 드래그앤드롭/붙여넣기 → 서버 업로드 → URL 삽입
3. 에디터 툴바 (bold, italic, heading, list, image, link)
4. `InquiryAnswerTab.tsx`에 에디터 통합

### Phase 3: Gmail 호환 출력
1. `juice` 라이브러리로 inline style 변환
2. "Gmail에 복사" 버튼 구현 (Clipboard API)
3. 이메일용 HTML wrapper (`<table>` 기반 레이아웃)

### Phase 4: 답변 저장 형식 전환
1. `V32__add_draft_format.sql` 마이그레이션
2. 답변 저장/조회 시 `draftFormat` 처리
3. 기존 TEXT 답변 하위 호환 유지

## 9. 고려사항

### 보안
- 이미지 업로드 시 Content-Type 검증 (magic bytes)
- 최대 파일 크기 제한 (5MB)
- 이미지 URL에 랜덤 UUID 사용 (추측 불가)
- XSS 방지: 에디터 HTML sanitize (TipTap은 기본 제공)

### 성능
- 이미지 서빙에 Cache-Control 헤더 적용
- 대용량 이미지 자동 리사이즈 (선택적, 추후)
- CDN 도입 (추후, 현재는 서버 직접 서빙)

### Gmail 호환성 테스트 체크리스트
- [ ] Gmail 웹 (Chrome) 에서 이미지 정상 표시
- [ ] Gmail 모바일 앱에서 이미지 정상 표시
- [ ] Gmail에 붙여넣기 후 이미지 URL 보존 확인
- [ ] 이미지 차단 상태에서 alt 텍스트 표시 확인
- [ ] 전체 HTML 크기 102KB 미만 확인
- [ ] inline style 적용 확인 (외부 CSS 미사용)
