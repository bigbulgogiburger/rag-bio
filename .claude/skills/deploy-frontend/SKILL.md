---
name: deploy-frontend
description: 프론트엔드 배포 (Next.js static export → SCP → nginx 서빙). 프론트엔드 배포, UI 배포, frontend deploy 요청 시 사용. 프론트엔드 빌드 후 서버 반영이 필요할 때 사용.
---

## Purpose

Next.js 프론트엔드를 EC2 서버의 nginx에 정적 파일로 배포하는 전 과정을 안내합니다.
빌드 → 압축 → 전송 → 교체 순서로 진행됩니다.

## Prerequisites

- SSH 키: `~/.ssh/csrag-deployer`
- EC2 IP: `terraform output ec2_public_ip`
- 서버 경로: `/opt/frontend/` (nginx가 서빙)
- `.env.production`에 `NEXT_PUBLIC_API_BASE_URL=https://api.infottyt.com` 설정 확인

## Key Concept

Next.js는 `output: 'export'` 모드로 정적 파일을 생성합니다.
`NEXT_PUBLIC_API_BASE_URL`은 **빌드 타임에 임베딩**되므로 런타임에 변경 불가합니다.
`.env.production` 파일이 `npm run build` 시 자동 로드되므로 별도 환경변수 지정이 불필요합니다.

## Deployment Steps

### Step 1: 빌드 전 검증

```bash
cd frontend
npm run lint
npm run build
```

- `next.config.mjs`: `output: 'export'`, `trailingSlash: true`
- 빌드 결과: `frontend/out/` 디렉토리

### Step 2: 압축

```bash
tar czf /tmp/frontend-out.tar.gz -C frontend/out .
```

### Step 3: 전송

```bash
EC2_IP=$(cd infra/terraform && terraform output -raw ec2_public_ip)
scp -i ~/.ssh/csrag-deployer /tmp/frontend-out.tar.gz ec2-user@${EC2_IP}:/tmp/
```

### Step 4: 서버에서 교체

```bash
ssh -i ~/.ssh/csrag-deployer ec2-user@${EC2_IP} \
  'sudo rm -rf /opt/frontend/* && sudo tar xzf /tmp/frontend-out.tar.gz -C /opt/frontend && sudo chown -R nginx:nginx /opt/frontend && rm /tmp/frontend-out.tar.gz'
```

### Step 5: 확인

```bash
curl -sf https://app.infottyt.com/ | head -20
```

nginx 재시작은 불필요합니다 (정적 파일 교체만으로 즉시 반영).

## Dynamic Routes

`[id]` 같은 동적 라우트는 `generateStaticParams`로 빌드 시점에 생성됩니다.
nginx에서 SPA fallback이 필요한 경우 `frontend/serve.json`의 rewrite 규칙을 참고하세요:
- `/inquiries/:id` → `/inquiries/_/index.html`

## Troubleshooting

| 증상 | 원인 | 해결 |
|------|------|------|
| API 호출 실패 (CORS) | NEXT_PUBLIC_API_BASE_URL 불일치 | `.env.production` 확인 후 재빌드 |
| 404 에러 (동적 라우트) | nginx rewrite 누락 | `/etc/nginx/conf.d/frontend.conf` 확인 |
| 빌드 실패 (react-pdf) | SSR 호환성 | `dynamic(() => import(...), { ssr: false })` 확인 |
| 빈 화면 | JS 번들 경로 불일치 | `trailingSlash: true` 설정 확인 |

## EC2 Server Layout

```
/opt/frontend/
├── index.html              # 메인 페이지
├── _next/static/           # JS/CSS 번들 (content-hashed)
├── dashboard/index.html    # 대시보드
├── inquiries/index.html    # 문의 목록
├── inquiries/_/index.html  # 문의 상세 (동적 라우트 fallback)
└── knowledge-base/index.html # 지식 기반
```

## Related Files

| File | Purpose |
|------|---------|
| `frontend/next.config.mjs` | Static export, trailingSlash 설정 |
| `frontend/.env.production` | 프로덕션 API URL (빌드 타임 임베딩) |
| `frontend/.env.development` | 개발 API URL (localhost:8081) |
| `frontend/serve.json` | 로컬 테스트용 SPA rewrite 규칙 |
