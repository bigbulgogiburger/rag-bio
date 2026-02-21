###############################################################################
# variables.tf — 입력 변수 정의
###############################################################################

variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "프로젝트 이름 (리소스 태그/네이밍에 사용)"
  type        = string
  default     = "csrag"
}

variable "environment" {
  description = "환경 (dev/staging/prod)"
  type        = string
  default     = "prod"
}

# ─── 도메인 ──────────────────────────────────────────────────────────────────

variable "domain_name" {
  description = "가비아 도메인 (예: example.com)"
  type        = string
}

variable "app_subdomain" {
  description = "프론트엔드 서브도메인"
  type        = string
  default     = "app"
}

variable "api_subdomain" {
  description = "백엔드 API 서브도메인"
  type        = string
  default     = "api"
}

# ─── EC2 ─────────────────────────────────────────────────────────────────────

variable "ssh_public_key" {
  description = "SSH 공개키 내용 (cat ~/.ssh/id_rsa.pub)"
  type        = string
}

variable "ssh_allowed_cidr" {
  description = "SSH 접속 허용 CIDR (본인 IP, 예: 1.2.3.4/32)"
  type        = string
}

# ─── RDS ─────────────────────────────────────────────────────────────────────

variable "db_name" {
  description = "데이터베이스 이름"
  type        = string
  default     = "csrag"
}

variable "db_username" {
  description = "데이터베이스 마스터 사용자"
  type        = string
  default     = "csrag"
}

variable "db_password" {
  description = "데이터베이스 마스터 비밀번호"
  type        = string
  sensitive   = true
}

# ─── 애플리케이션 ────────────────────────────────────────────────────────────

variable "openai_api_key" {
  description = "OpenAI API 키"
  type        = string
  sensitive   = true
  default     = ""
}

variable "openai_enabled" {
  description = "OpenAI 활성화 여부"
  type        = bool
  default     = false
}

variable "vector_db_provider" {
  description = "벡터 DB 프로바이더 (mock/qdrant/pinecone/weaviate)"
  type        = string
  default     = "mock"
}
