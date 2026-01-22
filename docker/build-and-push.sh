#!/bin/bash
set -e

# Configuration
AWS_PROFILE="mzc"
AWS_REGION="ap-northeast-2"
ECR_REPO_NAME="group2-codebuild-image"
IMAGE_TAG="java21-sam"

export AWS_DEFAULT_REGION="${AWS_REGION}"
export AWS_PROFILE="${AWS_PROFILE}"

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --profile ${AWS_PROFILE} --region ${AWS_REGION} --query Account --output text)
ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO_NAME}"

echo "=== CodeBuild Custom Image Build & Push ==="
echo "AWS Profile: ${AWS_PROFILE}"
echo "AWS Account: ${AWS_ACCOUNT_ID}"
echo "ECR Repository: ${ECR_REPO_NAME}"
echo "Image: ${ECR_URI}:${IMAGE_TAG}"
echo ""

# 1. Create ECR repository (if not exists)
echo "[1/4] Creating ECR repository..."
aws ecr describe-repositories --repository-names ${ECR_REPO_NAME} --profile ${AWS_PROFILE} --region ${AWS_REGION} 2>/dev/null || \
    aws ecr create-repository --repository-name ${ECR_REPO_NAME} --profile ${AWS_PROFILE} --region ${AWS_REGION}

# 2. Login to ECR
echo "[2/4] Logging in to ECR..."
aws ecr get-login-password --profile ${AWS_PROFILE} --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_URI}

# 3. Build and push Docker image (using buildx for cross-platform)
echo "[3/4] Building and pushing Docker image (linux/amd64)..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Create buildx builder if not exists
docker buildx create --name multiarch --use 2>/dev/null || docker buildx use multiarch

# Build and push directly (avoids local platform issues on Apple Silicon)
docker buildx build \
    --platform linux/amd64 \
    --tag ${ECR_URI}:${IMAGE_TAG} \
    --tag ${ECR_URI}:latest \
    --push \
    ${SCRIPT_DIR}

echo "[4/4] Push complete"

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
