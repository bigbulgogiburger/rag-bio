###############################################################################
# main.tf — Provider 설정
###############################################################################

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# 서울 리전 (기본)
provider "aws" {
  region = var.aws_region
}

# 버지니아 리전 (CloudFront ACM 인증서용)
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}
