###############################################################################
# ec2.tf — EC2 t4g.small (ARM/Graviton2) + Elastic IP + Key Pair
###############################################################################

# ─── AMI: Amazon Linux 2023 ARM64 ───────────────────────────────────────────

data "aws_ami" "al2023_arm" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-arm64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }
}

# ─── Key Pair ────────────────────────────────────────────────────────────────

resource "aws_key_pair" "deployer" {
  key_name   = "${var.project_name}-deployer"
  public_key = var.ssh_public_key

  tags = {
    Name = "${var.project_name}-deployer"
  }
}

# ─── EC2 Instance ────────────────────────────────────────────────────────────

resource "aws_instance" "backend" {
  ami                    = data.aws_ami.al2023_arm.id
  instance_type          = "t4g.small"
  key_name               = aws_key_pair.deployer.key_name
  vpc_security_group_ids = [aws_security_group.backend.id]
  subnet_id              = aws_subnet.public_a.id
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  # burst 크레딧 과금 방지
  credit_specification {
    cpu_credits = "standard"
  }

  # IMDSv2 강제 (SSRF 방지)
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    encrypted             = true
    delete_on_termination = true

    tags = {
      Name = "${var.project_name}-ebs"
    }
  }

  user_data = templatefile("${path.module}/scripts/user-data.sh", {
    db_host            = aws_db_instance.main.address
    db_port            = 5432
    db_name            = var.db_name
    db_username        = var.db_username
    db_password        = var.db_password
    openai_api_key     = var.openai_api_key
    openai_enabled     = var.openai_enabled
    vector_db_provider = var.vector_db_provider
    api_domain         = "${var.api_subdomain}.${var.domain_name}"
    app_domain         = "${var.app_subdomain}.${var.domain_name}"
  })

  tags = {
    Name = "${var.project_name}-backend"
  }

  depends_on = [aws_db_instance.main]
}

# ─── Elastic IP ──────────────────────────────────────────────────────────────

resource "aws_eip" "backend" {
  instance = aws_instance.backend.id
  domain   = "vpc"

  tags = {
    Name = "${var.project_name}-eip"
  }
}
