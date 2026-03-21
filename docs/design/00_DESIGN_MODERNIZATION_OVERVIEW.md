# Bio-Rad CS 대응 허브 — 디자인 현대화 종합 계획

## 현재 상태 진단

### 강점
- HSL CSS 변수 기반 디자인 토큰 시스템 구축 완료
- Pretendard + JetBrains Mono 한국어 최적 폰트 스택
- Light/Dark 모드 완전 지원
- 44px 터치 타겟, safe-area-inset 등 모바일 기초 양호
- CVA(Class Variance Authority) 기반 컴포넌트 변형 체계
- Radix UI 기반 접근성 (ARIA, 키보드 내비게이션)

### 개선이 필요한 영역

| 영역 | 현재 | 목표 |
|------|------|------|
| **시각적 통일성** | 페이지마다 미세한 스타일 차이 (메트릭 카드, footer 하드코딩) | 100% 디자인 토큰 기반 |
| **레이아웃 다양성** | 모든 섹션이 동일한 수직 스택 패턴 | 섹션별 차별화된 레이아웃 |
| **카드 깊이감** | 단순 `border + shadow-sm` | Double-Bezel 또는 Glass 기법 |
| **타이포그래피 위계** | `text-xl` 한 단계만 사용 | 페이지 타이틀 `text-2xl~3xl`, 섹션 `text-lg`, 본문 분리 |
| **마이크로 인터랙션** | `transition-colors`만 사용 | hover scale, staggered fade-in, count-up |
| **모바일 경험** | 데스크톱 축소판 수준 | 모바일 퍼스트 전용 UI 패턴 |
| **Empty/Error 상태** | 텍스트 중심 | 일러스트 + 액션 가이드 |
| **네비게이션** | 기본 탑바 | Floating Glass Pill (데스크톱) |
| **Footer** | 하드코딩 slate 색상 | 디자인 토큰 통합 |
| **컬러 활용** | Primary Blue 단색 | 페이지 역할별 Accent Variation |

---

## 디자인 아키타입 선정

### 선정: **G. Dark Minimal** (Light 버전 변형)

Bio-Rad CS 허브는 **업무용 도구**이므로:
- 정보 밀도가 높되 호흡이 있는 레이아웃
- 신뢰감을 주는 절제된 색상 체계
- 빠른 스캔과 액션을 돕는 명확한 시각 위계
- Light 모드 기본, Dark 모드 완전 지원

### 컬러 시스템 현대화

```
현재 Primary: hsl(221, 83%, 53%) — 순수 Blue
변경 제안: hsl(224, 71%, 48%) — 더 깊고 세련된 Indigo-Blue

현재 Background: hsl(216, 33%, 97%) — 약간 푸른 회색
변경 제안: hsl(220, 14%, 96%) — 더 뉴트럴한 회색 (따뜻함 추가)
```

---

## 현대화 문서 목록

| # | 문서 | 대상 |
|---|------|------|
| 01 | [디자인 시스템 통합](./01_DESIGN_SYSTEM.md) | 토큰, 타이포, 컬러, 스페이싱, 컴포넌트 기반 |
| 02 | [레이아웃 & 네비게이션](./02_LAYOUT_NAVIGATION.md) | Header, Footer, Nav, Mobile Bottom Nav |
| 03 | [대시보드 페이지](./03_PAGE_DASHBOARD.md) | `/dashboard` |
| 04 | [문의 목록 페이지](./04_PAGE_INQUIRIES.md) | `/inquiries` |
| 05 | [문의 작성 페이지](./05_PAGE_INQUIRY_NEW.md) | `/inquiries/new` |
| 06 | [문의 상세 페이지](./06_PAGE_INQUIRY_DETAIL.md) | `/inquiries/[id]` (Info, Answer, History 탭) |
| 07 | [지식 기반 페이지](./07_PAGE_KNOWLEDGE_BASE.md) | `/knowledge-base` |
| 08 | [로그인 페이지](./08_PAGE_LOGIN.md) | `/login` |
| 09 | [모바일 전용 UX](./09_MOBILE_UX.md) | 모든 페이지의 모바일 특화 패턴 |

---

## 실행 우선순위

### Phase 1: 기반 (1~2일)
- 디자인 토큰 현대화 (globals.css, tailwind.config.ts)
- Header/Nav Floating Glass 전환
- Footer 토큰 통합

### Phase 2: 핵심 페이지 (3~4일)
- 대시보드 레이아웃 리디자인
- 문의 상세 페이지 (가장 복잡, 가장 중요)
- 문의 목록 테이블/카드 현대화

### Phase 3: 보조 페이지 (2~3일)
- 문의 작성 폼 UX 개선
- 지식 기반 페이지 현대화
- 로그인 페이지

### Phase 4: 모바일 & 모션 (2~3일)
- 모바일 전용 UX 패턴 적용
- 마이크로 인터랙션 추가
- 성능 최적화 검증
