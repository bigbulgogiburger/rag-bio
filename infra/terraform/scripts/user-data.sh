#!/bin/bash
set -euo pipefail

exec > /var/log/user-data.log 2>&1
echo "=== User Data 시작: $(date) ==="

# ─── 1. 시스템 업데이트 ─────────────────────────────────────────────────────

dnf update -y

# ─── 2. Swap 1GB 생성 (안전장치) ────────────────────────────────────────────

if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=1024
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
  echo "Swap 1GB 생성 완료"
fi

# ─── 3. Docker 설치 (Amazon Linux 2023 ARM) ─────────────────────────────────

dnf install -y docker
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user

# docker-compose plugin 설치
mkdir -p /usr/local/lib/docker/cli-plugins
COMPOSE_VERSION=$(curl -s https://api.github.com/repos/docker/compose/releases/latest | grep tag_name | cut -d '"' -f 4)
curl -SL "https://github.com/docker/compose/releases/download/$${COMPOSE_VERSION}/docker-compose-linux-aarch64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

echo "Docker + Compose 설치 완료"

# ─── 4. nginx + certbot 설치 ─────────────────────────────────────────────────

dnf install -y nginx
systemctl enable nginx

# certbot 설치 (Let's Encrypt)
dnf install -y python3-pip
pip3 install certbot certbot-nginx

# nginx 설정: API 리버스 프록시 (초기에는 HTTP만, certbot이 HTTPS 추가)
cat > /etc/nginx/conf.d/backend.conf << 'NGINXEOF'
server {
    listen 80;
    server_name ${api_domain};

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 파일 업로드 크기 제한
        client_max_body_size 50M;
    }
}
NGINXEOF

# nginx 설정: 프론트엔드 정적 파일 서빙
mkdir -p /opt/frontend
cat > /etc/nginx/conf.d/frontend.conf << 'NGINXEOF'
server {
    listen 80;
    server_name ${app_domain};
    root /opt/frontend;
    index index.html;

    # SPA fallback: 파일이 없으면 index.html 반환
    location / {
        try_files $uri $uri/index.html $uri/ /index.html;
    }

    # 정적 자산 장기 캐싱 (Next.js _next/static은 content-hash 포함)
    location /_next/static/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
NGINXEOF

systemctl start nginx
echo "nginx 설치 완료 (certbot으로 TLS 설정 필요)"

# ─── 5. 애플리케이션 디렉토리 및 환경변수 ───────────────────────────────────

mkdir -p /opt/app/uploads

cat > /opt/app/.env << 'ENVEOF'
# Database
POSTGRES_HOST=${db_host}
POSTGRES_PORT=${db_port}
POSTGRES_DB=${db_name}
POSTGRES_USER=${db_username}
POSTGRES_PASSWORD=${db_password}

# Spring
SPRING_PROFILES_ACTIVE=docker

# OpenAI
OPENAI_API_KEY=${openai_api_key}
OPENAI_ENABLED=${openai_enabled}

# Vector DB
VECTOR_DB_PROVIDER=${vector_db_provider}

# CORS
CORS_ALLOWED_ORIGINS=https://${app_domain},http://localhost:3001
ENVEOF

chmod 600 /opt/app/.env

# ─── 6. docker-compose.yml 생성 (백엔드만) ───────────────────────────────────

cat > /opt/app/docker-compose.yml << 'COMPOSEEOF'
name: biorad
services:
  backend:
    image: eclipse-temurin:21-jre
    working_dir: /app
    command: >
      java
      -Xmx1g -Xms512m
      -XX:+UseG1GC
      -XX:MaxGCPauseMillis=200
      -jar app.jar
    env_file:
      - .env
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://$${POSTGRES_HOST}:$${POSTGRES_PORT}/$${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: $${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: $${POSTGRES_PASSWORD}
    ports:
      - "127.0.0.1:8081:8081"
    volumes:
      - ./app.jar:/app/app.jar:ro
      - ./uploads:/app/uploads
    restart: unless-stopped

COMPOSEEOF

chown -R ec2-user:ec2-user /opt/app

# ─── 7. TLS 설정 스크립트 (두 도메인 모두) ──────────────────────────────────

cat > /opt/app/setup-tls.sh << 'TLSEOF'
#!/bin/bash
# 가비아에서 두 도메인 모두 EC2 IP로 A 레코드 설정한 후 실행:
# sudo /opt/app/setup-tls.sh

set -euo pipefail

API_DOMAIN="${api_domain}"
APP_DOMAIN="${app_domain}"

echo "=== Let's Encrypt TLS 인증서 발급 ==="
echo "도메인: $API_DOMAIN, $APP_DOMAIN"
echo ""
echo "사전 조건:"
echo "  1. 가비아에서 A 레코드 설정 완료 (두 도메인 모두 → EC2 IP)"
echo "  2. DNS 전파 완료 (nslookup 으로 확인)"
echo ""

certbot --nginx -d "$API_DOMAIN" -d "$APP_DOMAIN" \
  --non-interactive --agree-tos --register-unsafely-without-email

# certbot 자동 갱신 cron
echo "0 0 1 * * root certbot renew --quiet" > /etc/cron.d/certbot-renew

echo "=== TLS 설정 완료 ==="
echo "https://$API_DOMAIN (API)"
echo "https://$APP_DOMAIN (Frontend)"
TLSEOF

chmod +x /opt/app/setup-tls.sh

echo "=== User Data 완료: $(date) ==="
echo ""
echo "다음 단계:"
echo "  1. app.jar를 /opt/app/에 업로드"
echo "  2. 프론트엔드 빌드 파일을 /opt/frontend/에 업로드"
echo "  3. cd /opt/app && docker compose up -d"
echo "  4. 가비아 DNS 설정 후: sudo /opt/app/setup-tls.sh"
