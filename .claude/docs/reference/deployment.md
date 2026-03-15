# Deployment

> 참조 시점: 배포, 인프라 변경, Terraform 작업, 서버 디버깅 시

## Live Architecture

```
[가비아 DNS]
  ├── app.infottyt.com → A → EC2 Elastic IP
  └── api.infottyt.com → A → EC2 Elastic IP

[EC2 t4g.small (ARM, 2GB RAM)] — ap-northeast-2 (서울)
  nginx (Let's Encrypt TLS)
  ├── app.infottyt.com → /opt/frontend/ (정적 파일 서빙)
  └── api.infottyt.com → localhost:8081 (리버스 프록시)

  Docker
  └── backend (eclipse-temurin:21-jre, -Xmx1g)
        └── /opt/app/app.jar

[RDS db.t4g.micro] ← PostgreSQL 16
```

## Key Facts

- **EC2 IP**: `terraform output ec2_public_ip` (Elastic IP)
- **SSH**: `ssh -i ~/.ssh/csrag-deployer ec2-user@<IP>`
- **보안 그룹**: SSH는 특정 IP만 허용. 배포 시 `aws ec2 authorize-security-group-ingress` 필요할 수 있음
- **TLS**: Let's Encrypt via Certbot (자동 갱신)
- **CORS**: `CORS_ALLOWED_ORIGINS` 환경변수 (`/opt/app/.env`)
- **로그인**: ID `bunh` / PW `ghGHgh123@`

## Deploy Commands

### 백엔드 배포
```bash
cd backend
./gradlew :app-api:bootJar -x test
scp -i ~/.ssh/csrag-deployer app-api/build/libs/app-api-0.1.0-SNAPSHOT.jar \
  ec2-user@<IP>:/opt/app/app.jar
ssh -i ~/.ssh/csrag-deployer ec2-user@<IP> \
  'cd /opt/app && docker compose down && docker compose up -d'
```

### 프론트엔드 배포
```bash
# .env.production에 NEXT_PUBLIC_API_BASE_URL=https://api.infottyt.com 자동 적용
cd frontend
npm run build
tar czf /tmp/frontend-out.tar.gz -C out .
scp -i ~/.ssh/csrag-deployer /tmp/frontend-out.tar.gz ec2-user@<IP>:/tmp/
ssh -i ~/.ssh/csrag-deployer ec2-user@<IP> \
  'sudo rm -rf /opt/frontend/* && sudo tar xzf /tmp/frontend-out.tar.gz -C /opt/frontend && sudo chown -R nginx:nginx /opt/frontend && rm /tmp/frontend-out.tar.gz'
```

## EC2 File Layout

```
/opt/app/
├── .env                    # 환경변수 (DB, OpenAI, CORS)
├── docker-compose.yml      # 백엔드 컨테이너 정의
├── app.jar                 # Spring Boot JAR
├── uploads/                # 업로드 파일
└── setup-tls.sh            # Let's Encrypt 초기 설정

/opt/frontend/              # Next.js static export
/etc/nginx/conf.d/
├── backend.conf            # api.infottyt.com → localhost:8081
└── frontend.conf           # app.infottyt.com → /opt/frontend/
```

## Terraform

```bash
cd infra/terraform
terraform plan     # 변경사항 확인
terraform apply    # 적용
terraform output   # IP, 배포 명령어, DNS 가이드
```

## 주의사항

- EC2 2GB RAM — Spring Boot 기동에 60-90초 소요
- SSH 보안 그룹 IP 제한 → 배포 후 반드시 제거 (`aws ec2 revoke-security-group-ingress`)
- Flyway 마이그레이션은 서버 기동 시 자동 실행 — 버전 충돌 주의
- 프론트엔드 `.env.production`은 빌드 타임 임베딩 — 런타임 변경 불가
