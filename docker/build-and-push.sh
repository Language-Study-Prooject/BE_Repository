#!/bin/bash
set -e

# Configuration
AWS_REGION="ap-northeast-2"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REPO_NAME="group2-codebuild-image"
IMAGE_TAG="java21-sam"

ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO_NAME}"

echo "=== CodeBuild Custom Image Build & Push ==="
echo "AWS Account: ${AWS_ACCOUNT_ID}"
echo "ECR Repository: ${ECR_REPO_NAME}"
echo "Image: ${ECR_URI}:${IMAGE_TAG}"
echo ""

# 1. Create ECR repository (if not exists)
echo "[1/4] Creating ECR repository..."
aws ecr describe-repositories --repository-names ${ECR_REPO_NAME} --region ${AWS_REGION} 2>/dev/null || \
    aws ecr create-repository --repository-name ${ECR_REPO_NAME} --region ${AWS_REGION}

# 2. Login to ECR
echo "[2/4] Logging in to ECR..."
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_URI}

# 3. Build Docker image
echo "[3/4] Building Docker image..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker build -t ${ECR_REPO_NAME}:${IMAGE_TAG} ${SCRIPT_DIR}

# 4. Tag and push
echo "[4/4] Pushing to ECR..."
docker tag ${ECR_REPO_NAME}:${IMAGE_TAG} ${ECR_URI}:${IMAGE_TAG}
docker tag ${ECR_REPO_NAME}:${IMAGE_TAG} ${ECR_URI}:latest
docker push ${ECR_URI}:${IMAGE_TAG}
docker push ${ECR_URI}:latest

echo ""
echo "=== SUCCESS ==="
echo "Image pushed: ${ECR_URI}:${IMAGE_TAG}"
echo ""
echo "Next steps:"
echo "1. Update CodeBuild project to use custom image:"
echo "   Image: ${ECR_URI}:${IMAGE_TAG}"
echo "   Image pull credentials: Service role"
echo ""
echo "2. Add ECR pull permission to CodeBuild service role"
