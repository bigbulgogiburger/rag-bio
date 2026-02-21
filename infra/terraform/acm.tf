###############################################################################
# acm.tf — ACM SSL 인증서 (us-east-1, CloudFront 요구사항)
###############################################################################

resource "aws_acm_certificate" "main" {
  provider = aws.us_east_1

  domain_name               = "${var.app_subdomain}.${var.domain_name}"
  subject_alternative_names = ["${var.api_subdomain}.${var.domain_name}"]
  validation_method         = "DNS"

  tags = {
    Name = "${var.project_name}-cert"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# ─── DNS 검증 (수동) ────────────────────────────────────────────────────────
# 가비아 DNS에 CNAME 레코드를 수동으로 추가한 후,
# terraform apply를 다시 실행하면 검증이 완료됩니다.
#
# 필요한 레코드는 다음 명령어로 확인:
#   terraform output acm_validation_records

resource "aws_acm_certificate_validation" "main" {
  provider        = aws.us_east_1
  certificate_arn = aws_acm_certificate.main.arn
  timeouts {
    create = "30m"
  }
}
