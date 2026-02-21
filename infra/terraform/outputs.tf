###############################################################################
# outputs.tf — 배포 후 필요한 출력값
###############################################################################

# ─── EC2 ─────────────────────────────────────────────────────────────────────

output "ec2_public_ip" {
  description = "EC2 Elastic IP (가비아에서 api.도메인 A 레코드로 설정)"
  value       = aws_eip.backend.public_ip
}

output "ec2_ssh_command" {
  description = "SSH 접속 명령어"
  value       = "ssh -i ~/.ssh/csrag-deployer ec2-user@${aws_eip.backend.public_ip}"
}

# ─── RDS ─────────────────────────────────────────────────────────────────────

output "rds_endpoint" {
  description = "RDS 엔드포인트"
  value       = aws_db_instance.main.endpoint
}

output "rds_connection_command" {
  description = "EC2에서 RDS 접속 명령어"
  value       = "psql -h ${aws_db_instance.main.address} -U ${var.db_username} -d ${var.db_name}"
}

# ─── S3 / CloudFront ────────────────────────────────────────────────────────

output "frontend_bucket" {
  description = "프론트엔드 S3 버킷 이름"
  value       = aws_s3_bucket.frontend.id
}

output "cloudfront_domain" {
  description = "CloudFront 도메인 (가비아에서 app.도메인 CNAME으로 설정)"
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "cloudfront_distribution_id" {
  description = "CloudFront Distribution ID (캐시 무효화용)"
  value       = aws_cloudfront_distribution.frontend.id
}

# ─── ACM 검증 ────────────────────────────────────────────────────────────────

output "acm_validation_records" {
  description = "가비아에 추가할 ACM DNS 검증 CNAME 레코드"
  value = {
    for dvo in aws_acm_certificate.main.domain_validation_options : dvo.domain_name => {
      type  = dvo.resource_record_type
      name  = dvo.resource_record_name
      value = dvo.resource_record_value
    }
  }
}

# ─── 가비아 DNS 설정 가이드 ─────────────────────────────────────────────────

output "gabia_dns_guide" {
  description = "가비아에 설정할 DNS 레코드 목록"
  value       = <<-EOT

    === 가비아 DNS 설정 가이드 ===

    1. API 서버 (A 레코드):
       호스트: ${var.api_subdomain}
       타입:   A
       값:     ${aws_eip.backend.public_ip}

    2. 프론트엔드 (A 레코드 — EC2 nginx 정적 서빙):
       호스트: ${var.app_subdomain}
       타입:   A
       값:     ${aws_eip.backend.public_ip}

    참고: CloudFront(${aws_cloudfront_distribution.frontend.domain_name})는
    S3 백업용으로 유지. DNS는 EC2를 직접 가리킴.

    3. ACM 인증서 검증 — 아래 CNAME 레코드를 추가하세요:
       (terraform output acm_validation_records 참조)

  EOT
}

# ─── 프론트엔드 배포 명령어 ──────────────────────────────────────────────────

output "frontend_deploy_commands" {
  description = "프론트엔드 빌드 및 배포 명령어"
  value       = <<-EOT

    # 프론트엔드 빌드 (static export)
    cd frontend
    NEXT_PUBLIC_API_BASE_URL=https://${var.api_subdomain}.${var.domain_name} npm run build

    # EC2에 정적 파일 업로드
    tar czf /tmp/frontend-out.tar.gz -C out .
    scp -i ~/.ssh/csrag-deployer /tmp/frontend-out.tar.gz ec2-user@${aws_eip.backend.public_ip}:/tmp/
    ssh -i ~/.ssh/csrag-deployer ec2-user@${aws_eip.backend.public_ip} \
      'sudo rm -rf /opt/frontend/* && sudo tar xzf /tmp/frontend-out.tar.gz -C /opt/frontend && sudo chown -R nginx:nginx /opt/frontend && rm /tmp/frontend-out.tar.gz'

    # S3 백업 (선택)
    aws s3 sync out/ s3://${aws_s3_bucket.frontend.id} --delete

  EOT
}

output "backend_deploy_commands" {
  description = "백엔드 빌드 및 배포 명령어"
  value       = <<-EOT

    # 백엔드 빌드
    cd backend
    ./gradlew :app-api:bootJar -x test

    # EC2에 JAR 업로드 + 재시작
    scp -i ~/.ssh/csrag-deployer app-api/build/libs/app-api-0.1.0-SNAPSHOT.jar ec2-user@${aws_eip.backend.public_ip}:/opt/app/app.jar
    ssh -i ~/.ssh/csrag-deployer ec2-user@${aws_eip.backend.public_ip} \
      'cd /opt/app && docker compose down && docker compose up -d'

  EOT
}
