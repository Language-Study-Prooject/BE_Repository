#!/bin/bash

API_URL="https://ha3vg3u73g.execute-api.ap-northeast-2.amazonaws.com/dev"

echo "=== 채팅방 15개 생성 ==="
LEVELS=("beginner" "intermediate" "advanced")

for i in {1..15}; do
  LEVEL=${LEVELS[$((i % 3))]}
  echo "Creating room $i (level: $LEVEL)..."

  curl -s -X POST "$API_URL/chat/rooms" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"영어 스터디방 $i\", \"description\": \"함께 영어 공부해요 $i\", \"level\": \"$LEVEL\", \"maxMembers\": $((4 + i % 5)), \"createdBy\": \"user$((i % 5 + 1))\"}" | jq -r '.data.roomId'

  sleep 0.3
done

echo ""
echo "=== 첫 번째 방에 메시지 30개 생성 ==="

# 첫 번째 방 ID 가져오기
ROOM_ID=$(curl -s "$API_URL/chat/rooms?limit=1" | jq -r '.data.rooms[0].roomId')
echo "Room ID: $ROOM_ID"

MESSAGES=(
  "Hello everyone!"
  "Hi! Nice to meet you!"
  "How are you doing today?"
  "I'm fine, thank you!"
  "What are you studying?"
  "I'm learning English conversation."
  "That's great!"
  "Can you help me with pronunciation?"
  "Sure, I'd love to help!"
  "Let's practice together."
)

for i in {1..30}; do
  USER_ID="user$((i % 5 + 1))"
  MSG_IDX=$((i % 10))
  MESSAGE="${MESSAGES[$MSG_IDX]} (Message #$i)"

  echo "Sending message $i from $USER_ID..."

  curl -s -X POST "$API_URL/chat/rooms/$ROOM_ID/messages" \
    -H "Content-Type: application/json" \
    -d "{\"userId\": \"$USER_ID\", \"content\": \"$MESSAGE\"}" | jq -r '.data.messageId'

  sleep 0.2
done

echo ""
echo "=== 완료! ==="
echo ""
echo "테스트 명령어:"
echo "# 채팅방 목록 (페이지네이션)"
echo "curl '$API_URL/chat/rooms?limit=5'"
echo ""
echo "# 메시지 목록 (페이지네이션)"
echo "curl '$API_URL/chat/rooms/$ROOM_ID/messages?limit=10'"
