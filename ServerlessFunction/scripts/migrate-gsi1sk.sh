#!/bin/bash
# GSI1SK 마이그레이션 스크립트
# 기존: {level}#{createdAt}
# 신규: {type}#{gameType}#{status}#{level}#{createdAt}

set -e

AWS_PROFILE="${AWS_PROFILE:-mzc}"
AWS_REGION="ap-northeast-2"
TABLE_NAME="group2-englishstudy-chat"

echo "=== GSI1SK Migration Script ==="
echo "Profile: $AWS_PROFILE"
echo "Region: $AWS_REGION"
echo "Table: $TABLE_NAME"
echo ""

# GSI1에서 ROOMS로 시작하는 모든 방 조회
echo "Fetching rooms from GSI1..."

ROOMS=$(AWS_PROFILE=$AWS_PROFILE aws dynamodb query \
  --table-name $TABLE_NAME \
  --region $AWS_REGION \
  --index-name GSI1 \
  --key-condition-expression "GSI1PK = :pk" \
  --expression-attribute-values '{":pk": {"S": "ROOMS"}}' \
  --output json 2>/dev/null)

COUNT=$(echo "$ROOMS" | jq '.Items | length')
echo "Found $COUNT rooms to migrate"
echo ""

if [ "$COUNT" -eq "0" ]; then
  echo "No rooms to migrate. Exiting."
  exit 0
fi

# 각 방에 대해 GSI1SK 업데이트
echo "$ROOMS" | jq -c '.Items[]' | while read -r ROOM; do
  PK=$(echo "$ROOM" | jq -r '.PK.S')
  SK=$(echo "$ROOM" | jq -r '.SK.S')
  OLD_GSI1SK=$(echo "$ROOM" | jq -r '.GSI1SK.S')
  ROOM_TYPE=$(echo "$ROOM" | jq -r '.type.S // "CHAT"')
  GAME_TYPE=$(echo "$ROOM" | jq -r '.gameType.S // "-"')
  STATUS=$(echo "$ROOM" | jq -r '.status.S // "WAITING"')
  LEVEL=$(echo "$ROOM" | jq -r '.level.S // "beginner"')
  CREATED_AT=$(echo "$ROOM" | jq -r '.createdAt.S')

  # gameType이 null이면 "-"로 설정
  if [ "$GAME_TYPE" == "null" ]; then
    GAME_TYPE="-"
  fi

  # 이미 새 포맷인지 확인 (5개 부분으로 나뉘는지)
  PARTS=$(echo "$OLD_GSI1SK" | tr '#' '\n' | wc -l)
  if [ "$PARTS" -ge 5 ]; then
    echo "SKIP: $PK (already migrated: $OLD_GSI1SK)"
    continue
  fi

  # 새 GSI1SK 생성
  NEW_GSI1SK="${ROOM_TYPE}#${GAME_TYPE}#${STATUS}#${LEVEL}#${CREATED_AT}"

  echo "Migrating: $PK"
  echo "  Old GSI1SK: $OLD_GSI1SK"
  echo "  New GSI1SK: $NEW_GSI1SK"

  # DynamoDB 업데이트
  AWS_PROFILE=$AWS_PROFILE aws dynamodb update-item \
    --table-name $TABLE_NAME \
    --region $AWS_REGION \
    --key "{\"PK\": {\"S\": \"$PK\"}, \"SK\": {\"S\": \"$SK\"}}" \
    --update-expression "SET GSI1SK = :gsi1sk" \
    --expression-attribute-values "{\":gsi1sk\": {\"S\": \"$NEW_GSI1SK\"}}" \
    2>/dev/null

  echo "  Done!"
  echo ""
done

echo "=== Migration Complete ==="
