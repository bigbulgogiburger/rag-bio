# Frontend Guide

> 참조 시점: 프론트엔드 컴포넌트/페이지 개발, UI 수정, 스타일링 작업 시

## 기술 스택

Next.js 14 (App Router) / React 18 / TypeScript / CSS Variables (globals.css)

## 디렉토리 구조

```
src/app/                          — Next.js App Router pages
├── page.tsx                      — Client redirect → /dashboard
├── dashboard/page.tsx            — Ops dashboard (metrics + recent 5 inquiries)
├── inquiries/page.tsx            — Inquiry list (filter, pagination, search)
├── inquiries/new/page.tsx        — Create inquiry form
├── inquiries/[id]/page.tsx       — Inquiry detail (server wrapper + Suspense + generateStaticParams)
├── inquiries/[id]/InquiryDetailClient.tsx — Inquiry detail client (4 tabs: info, answer, history)
├── inquiry/new/page.tsx          — Client redirect → /inquiries/new
└── knowledge-base/page.tsx       — Knowledge Base management (CRUD, indexing, stats)

src/components/
├── app-shell-nav.tsx             — Top navigation (4 menus: 대시보드, 문의 목록, 문의 작성, 지식 기반)
├── ui/                           — Design system components
│   ├── Badge.tsx                 — Status badge (variant: info/success/warn/danger/neutral)
│   ├── DataTable.tsx             — Generic table (columns, row click, keyboard nav)
│   ├── Pagination.tsx            — Page navigation + size selector
│   ├── Tabs.tsx                  — Tab switching (arrow keys, ARIA)
│   ├── Toast.tsx                 — Auto-dismiss notifications
│   ├── EmptyState.tsx            — Empty state with CTA
│   ├── FilterBar.tsx             — Filter fields (select/text/date) + search
│   ├── PipelineProgressBadge.tsx — RAG 파이프라인 진행 상태 뱃지
│   └── index.ts                  — Barrel export
└── inquiry/                      — Inquiry detail tab components
    ├── InquiryCreateForm.tsx     — Create form (question + channel + documents)
    ├── InquiryInfoTab.tsx        — Info tab (query details + document list + indexing)
    ├── InquiryAnalysisTab.tsx    — Analysis tab (evidence + verdict + sourceType badges)
    ├── InquiryAnswerTab.tsx      — Answer tab (draft → review → approve → send workflow)
    ├── InquiryHistoryTab.tsx     — History tab (version history)
    ├── PipelineProgress.tsx      — 파이프라인 진행 UI
    └── index.ts                  — Barrel export

src/lib/
├── api/client.ts                 — Typed API client (모든 백엔드 API 호출)
└── i18n/labels.ts                — Korean label mappings (verdict, status, channel, error 등)
```

## Design System

- **Design tokens**: `globals.css`에서 CSS 변수 정의 (`--color-*`, `--space-*`, `--font-*`, `--radius-*`, `--shadow-*`, `--transition-*`)
- **하드코딩 금지**: 색상/크기는 반드시 CSS 변수 사용
- **Responsive**: Breakpoints — 1279px (tablet), 767px (mobile)
- **Accessibility**: `focus-visible`, ARIA roles (tablist/tab/tabpanel, table, alert), keyboard navigation
- **UI 언어**: Korean (ko) — `labels.ts`에서 영문 enum → 한국어 변환

## Build Notes

- `next.config.mjs`: `output: 'export'`, `trailingSlash: true`
- `NEXT_PUBLIC_API_BASE_URL`은 빌드 타임 임베딩 (런타임 변경 불가)
  - `.env.development` → `http://localhost:8081` (`npm run dev`)
  - `.env.production` → `https://api.infottyt.com` (`npm run build`)
  - 배포 빌드 시 별도 환경변수 지정 불필요
- `middleware.ts` 없음 (static export 미지원, AuthProvider가 클라이언트 인증 처리)
- 동적 라우트 `[id]`는 `generateStaticParams` + Suspense boundary 사용

## 주의사항

- 마크다운 서식 사용 금지 (답변 UI에서 순수 텍스트만 렌더링)
- 새 UI 컴포넌트는 `src/components/ui/` 아래, 도메인별은 `src/components/{domain}/`
- API 응답의 영문 enum을 직접 표시하지 않음 — 반드시 `labels.ts` 경유
