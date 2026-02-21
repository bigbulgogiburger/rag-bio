###############################################################################
# rds.tf — RDS db.t4g.micro PostgreSQL 16
###############################################################################

# ─── DB Subnet Group ────────────────────────────────────────────────────────

resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet"
  subnet_ids = [aws_subnet.public_a.id, aws_subnet.public_c.id]

  tags = {
    Name = "${var.project_name}-db-subnet"
  }
}

# ─── RDS Instance ────────────────────────────────────────────────────────────

resource "aws_db_instance" "main" {
  identifier = "${var.project_name}-db"

  # 엔진
  engine         = "postgres"
  engine_version = "16"

  # 인스턴스 (ARM — 비용 절감)
  instance_class = "db.t4g.micro"

  # 스토리지 — 자동확장 비활성, 암호화 활성
  allocated_storage     = 20
  max_allocated_storage = 20
  storage_type          = "gp2"
  storage_encrypted     = true

  # 인증
  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  # 네트워크
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  # 가용성 — 단일 AZ (비용 절감)
  multi_az = false

  # 백업 (크레딧 기반 Free Tier — 7일 보관)
  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  # 비용 절감 설정
  performance_insights_enabled = false
  monitoring_interval          = 0

  # 삭제 정책
  skip_final_snapshot      = true
  deletion_protection      = false
  delete_automated_backups = true
  copy_tags_to_snapshot    = true

  tags = {
    Name = "${var.project_name}-db"
  }
}
