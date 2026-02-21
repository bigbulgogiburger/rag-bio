# PRD: AWS Free Tier 배포 (Terraform IaC)

## 1. 개요

Bio-Rad CS Copilot 애플리케이션을 AWS에 Terraform으로 배포한다.
2025년 7월 이후 생성된 AWS 계정의 크레딧 기반 Free Tier ($200, 6개월)에 최적화하며,
EC2 t4g.small Graviton 무료 프로모션(2026.12까지)을 활용한다.

## 2. 목표

- Terraform IaC로 재현 가능한 인프라 프로비저닝
- 월 크레딧 소모 $20 이하 유지 (6개월간 $200 크레딧 범위 내)
- HTTPS 지원 (ACM + CloudFront + 가비아 도메인)
- 과금 방지 안전장치 내장

## 3. 대상 아키텍처

```
[가비아 DNS]
  ├── app.{domain}  → CNAME → CloudFront Distribution
  └── api.{domain}  → A     → EC2 Elastic IP

┌─────────────────────────────────────────────────┐
│  CloudFront (Always Free: 1TB/월)               │
│  └── Origin: S3 (OAC, 정적 프론트엔드)           │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│  EC2 t4g.small (2 vCPU, 2GB RAM, ARM)           │
│  - Graviton 프로모션: $0/월 (2026.12까지)        │
│  - Docker + Spring Boot Backend                  │
│  - JVM: -Xmx1g -Xms512m                         │
│  - Swap 1GB                                      │
│  - EBS 20GB gp3                                  │
│  - Elastic IP                                    │
└──────────────────────┬──────────────────────────┘
                       │ Private (SG 제한)
┌──────────────────────▼──────────────────────────┐
│  RDS db.t4g.micro (PostgreSQL 16)                │
│  - 20GB gp2, Single-AZ                           │
│  - ~$14/월 (크레딧 차감)                          │
└─────────────────────────────────────────────────┘
```

## 4. 월간 비용 분석

| 리소스 | 사양 | 월 비용 | 비용 원천 |
|--------|------|---------|-----------|
| EC2 | t4g.small | $0 | Graviton 프로모션 |
| RDS | db.t4g.micro | ~$11.68 | 크레딧 |
| RDS Storage | 20GB gp2 | ~$2.30 | 크레딧 |
| EBS | 20GB gp3 | ~$1.60 | 크레딧 |
| Public IPv4 | 1개 | ~$3.65 | 크레딧 |
| S3 | <5GB | $0 | Always Free |
| CloudFront | <1TB | $0 | Always Free |
| ACM | 인증서 | $0 | Always Free |
| Route53 | 미사용 | $0 | - |
| **합계** | | **~$19.23/월** | |

- 6개월 총 크레딧 소모: ~$115 / $200 → 충분
- 6개월 이후: EC2 $0 유지, 나머지 ~$19/월 실비용

## 5. Terraform 리소스 목록

### 5.1 네트워크 (network.tf)
- VPC (`10.0.0.0/16`)
- Public Subnet x2 (2 AZ, RDS 요구사항)
- Internet Gateway + Route Table
- Security Group: backend (SSH + HTTP + HTTPS)
- Security Group: rds (PostgreSQL, backend SG에서만)

### 5.2 컴퓨팅 (ec2.tf)
- EC2 Instance: `t4g.small`, Amazon Linux 2023 ARM64
- Key Pair: SSH 공개키 등록
- Elastic IP: EC2에 연결
- credit_specification: `standard` (burst 과금 방지)

### 5.3 데이터베이스 (rds.tf)
- RDS Instance: `db.t4g.micro`, PostgreSQL 16
- Storage: 20GB gp2, 자동확장 비활성
- Single-AZ, Performance Insights 비활성
- Enhanced Monitoring 비활성
- Private Access Only (EC2에서만)

### 5.4 스토리지 (s3.tf)
- S3 Bucket: 프론트엔드 정적 파일
- Public Access 차단, CloudFront OAC만 접근

### 5.5 CDN (cloudfront.tf)
- CloudFront Distribution: S3 Origin (OAC)
- HTTPS redirect, SPA error handling
- Price Class 200 (아시아 포함)

### 5.6 인증서 (acm.tf)
- ACM Certificate (us-east-1): app.{domain}, api.{domain}
- DNS 검증 (가비아에서 CNAME 수동 추가)

### 5.7 권한 (iam.tf)
- EC2 Instance Profile: S3 읽기/쓰기

### 5.8 부트스트랩 (scripts/user-data.sh)
- Swap 설정, Docker 설치, 백엔드 실행

## 6. 과금 방지 안전장치

| 설정 | 이유 |
|------|------|
| `credit_specification = standard` | EC2 burst 과금 방지 |
| `multi_az = false` | RDS 2배 과금 방지 |
| `max_allocated_storage = 20` | RDS 자동확장 비활성 |
| `performance_insights_enabled = false` | PI 과금 방지 |
| `monitoring_interval = 0` | Enhanced Monitoring 과금 방지 |
| NAT Gateway 미생성 | $32/월 과금 방지 |
| Route53 미사용 | $0.50/월 절약 |

## 7. 프론트엔드 변경사항

- `next.config.mjs`: `output: 'standalone'` → `output: 'export'`
- 빌드 결과 `out/` 디렉토리를 S3에 업로드
- `NEXT_PUBLIC_API_BASE_URL`을 빌드 시 도메인으로 설정

## 8. 배포 절차

1. `terraform init && terraform plan && terraform apply`
2. ACM DNS 검증 레코드를 가비아에 추가
3. 가비아 DNS 설정 (app → CloudFront, api → EC2 IP)
4. 프론트엔드 빌드 → S3 업로드
5. EC2 SSH 접속 → 백엔드 Docker 실행

## 9. 범위 외

- CI/CD 파이프라인 (GitHub Actions 등)
- 모니터링/알림 (CloudWatch Alarm)
- Auto Scaling
- WAF/Shield
- 멀티 환경 (staging/production 분리)
