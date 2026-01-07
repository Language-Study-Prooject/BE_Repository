#!/bin/bash

# Group2 English Study - 통합 빌드 & 배포 스크립트
# 사용법: ./deploy.sh [build|deploy|all]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHATTING_DIR="$SCRIPT_DIR/chatting"

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

build() {
    log_info "=========================================="
    log_info "빌드 시작: Chat + Vocab 통합"
    log_info "=========================================="

    cd "$CHATTING_DIR"
    sam build

    log_info "빌드 완료!"
}

deploy() {
    log_info "=========================================="
    log_info "배포 시작: group2-englishstudy-chatting"
    log_info "=========================================="

    cd "$CHATTING_DIR"
    sam deploy --no-confirm-changeset

    # API URL 출력
    log_info "=========================================="
    log_info "배포 완료!"
    API_URL=$(aws cloudformation describe-stacks \
        --stack-name group2-englishstudy-chatting \
        --profile mzc \
        --region ap-northeast-2 \
        --query 'Stacks[0].Outputs[?OutputKey==`ApiUrl`].OutputValue' \
        --output text 2>/dev/null)

    if [ -n "$API_URL" ]; then
        log_info "API URL: $API_URL"
    fi
    log_info "=========================================="
}

validate() {
    log_info "템플릿 검증 중..."
    cd "$CHATTING_DIR"
    sam validate
    log_info "템플릿 검증 완료!"
}

status() {
    log_info "스택 상태 확인 중..."
    aws cloudformation describe-stacks \
        --stack-name group2-englishstudy-chatting \
        --profile mzc \
        --region ap-northeast-2 \
        --query 'Stacks[0].{Status:StackStatus,LastUpdated:LastUpdatedTime}' \
        --output table 2>/dev/null || log_warn "스택이 존재하지 않습니다."
}

delete() {
    log_warn "=========================================="
    log_warn "스택 삭제: group2-englishstudy-chatting"
    log_warn "=========================================="
    read -p "정말 삭제하시겠습니까? (y/N): " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        aws cloudformation delete-stack \
            --stack-name group2-englishstudy-chatting \
            --profile mzc \
            --region ap-northeast-2
        log_info "삭제 요청 완료. 'aws cloudformation wait stack-delete-complete'로 완료 대기 가능"
    else
        log_info "삭제 취소됨"
    fi
}

show_help() {
    echo "Group2 English Study - 빌드 & 배포 스크립트"
    echo ""
    echo "사용법: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  build     SAM 빌드만 실행"
    echo "  deploy    SAM 배포만 실행"
    echo "  all       빌드 + 배포 (기본값)"
    echo "  validate  템플릿 검증"
    echo "  status    스택 상태 확인"
    echo "  delete    스택 삭제"
    echo "  help      도움말 표시"
    echo ""
    echo "예시:"
    echo "  $0 all        # 빌드 후 배포"
    echo "  $0 build      # 빌드만"
    echo "  $0 deploy     # 배포만"
}

# 메인 로직
case "${1:-all}" in
    build)
        build
        ;;
    deploy)
        deploy
        ;;
    all)
        build
        deploy
        ;;
    validate)
        validate
        ;;
    status)
        status
        ;;
    delete)
        delete
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        log_error "알 수 없는 명령: $1"
        show_help
        exit 1
        ;;
esac
