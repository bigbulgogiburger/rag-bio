---
name: deploy-backend
description: 백엔드 배포 (JAR 빌드 → SCP → Docker 재시작). 백엔드 배포, 서버 배포, 프로덕션 반영, backend deploy 요청 시 사용. 배포 명령어가 기억나지 않을 때도 사용.
---

## Purpose

Spring Boot 백엔드를 EC2 서버에 배포하는 전 과정을 단계별로 안내합니다.
배포는 JAR 빌드 → SCP 전송 → Docker 재시작 순서로 진행되며, 각 단계에서 실패 시 롤백 가이드를 제공합니다.

## Prerequisites

- SSH 키: `~/.ssh/csrag-deployer`
- EC2 IP: `terraform output ec2_public_ip` 또는 Elastic IP 직접 확인
- 서버 경로: `/opt/app/` (app.jar, docker-compose.yml, .env)

## Deployment Steps

### Step 1: 빌드 전 검증

```bash
cd backend
./gradlew build
```

- 테스트 실패 시 배포 중단
- JaCoCo 커버리지 확인 (line 80%, branch 68% 최소)

### Step 2: bootJar 생성

```bash
cd backend
./gradlew :app-api:bootJar -x test
```

- 출력: `app-api/build/libs/app-api-0.1.0-SNAPSHOT.jar`
- 테스트는 Step 1에서 이미 통과했으므로 `-x test`로 스킵

### Step 3: JAR 전송

```bash
EC2_IP=$(cd infra/terraform && terraform output -raw ec2_public_ip)
scp -i ~/.ssh/csrag-deployer \
  backend/app-api/build/libs/app-api-0.1.0-SNAPSHOT.jar \
  ec2-user@${EC2_IP}:/opt/app/app.jar
```

### Step 4: Docker 재시작

```bash
ssh -i ~/.ssh/csrag-deployer ec2-user@${EC2_IP} \
  'cd /opt/app && docker compose down && docker compose up -d'
```

### Step 5: 헬스체크

```bash
ssh -i ~/.ssh/csrag-deployer ec2-user@${EC2_IP} \
  'sleep 15 && curl -sf http://localhost:8081/actuator/health || echo "HEALTH CHECK FAILED"'
```

- Spring Boot 기동에 10~20초 소요 (t4g.small, -Xmx1g)
- 실패 시: `docker logs` 확인

## Rollback

이전 JAR이 필요하면 서버에서 직접 복원:
```bash
ssh -i ~/.ssh/csrag-deployer ec2-user@${EC2_IP} \
  'cd /opt/app && docker compose down && docker compose up -d'
```

롤백용 JAR 백업이 없으므로, 배포 전 로컬에서 이전 커밋의 JAR을 보관하는 것을 권장합니다.

## Troubleshooting

| 증상 | 원인 | 해결 |
|------|------|------|
| Connection refused (SCP) | SSH 키 권한 또는 Security Group | `chmod 600 ~/.ssh/csrag-deployer`, SG 22번 포트 확인 |
| Docker 시작 실패 | 포트 충돌 또는 메모리 부족 | `docker ps -a`, `free -m` 확인 |
| Health check 실패 | DB 연결 또는 환경변수 누락 | `/opt/app/.env` 확인, RDS 접근성 확인 |
| OOM Killed | JVM 힙 초과 (t4g.small = 2GB) | `-Xmx1g` 확인, swap 1GB 설정 확인 |

## EC2 Server Layout

```
/opt/app/
├── .env                    # DB, OpenAI, CORS 환경변수
├── docker-compose.yml      # 백엔드 컨테이너 정의
├── app.jar                 # Spring Boot JAR (이 파일을 교체)
└── uploads/                # 업로드된 문서 파일
```

## Related Files

| File | Purpose |
|------|---------|
| `backend/Dockerfile` | Multi-stage 빌드 (temurin:21-jdk → temurin:21-jre) |
| `backend/app-api/build.gradle` | bootJar 설정, JaCoCo 커버리지 |
| `infra/terraform/ec2.tf` | EC2 인스턴스 정의 (t4g.small) |
| `infra/terraform/scripts/user-data.sh` | EC2 초기 설정 스크립트 |
