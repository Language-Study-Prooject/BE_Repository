# 프론트엔드 전달사항 - 채팅/게임 API 가이드

## 1. 아키텍처 구조 (업데이트됨)

### 채팅방과 게임방 분리
```
RoomType enum
├── CHAT ("chat") - 일반 채팅방
└── GAME ("game") - 게임방 (캐치마인드 등)

RoomStatus enum
├── WAITING ("waiting") - 대기 중
├── PLAYING ("playing") - 게임 진행 중
└── FINISHED ("finished") - 종료됨
```

### GSI1SK 인덱스 설계
```
GSI1PK: "ROOMS" (고정)
GSI1SK: {type}#{gameType}#{status}#{level}#{createdAt}

예시:
- CHAT#-#WAITING#beginner#2026-01-22T10:00:00Z   (일반 채팅방)
- GAME#CATCHMIND#WAITING#intermediate#2026-01-22T10:00:00Z (대기중 게임방)
- GAME#CATCHMIND#PLAYING#advanced#2026-01-22T10:00:00Z (진행중 게임방)
```

**핵심**: DB 레벨에서 `type`, `gameType`, `status`, `level` 조합으로 필터링 가능

---

## 2. 방 타입 (RoomType)

| 타입 | 코드 | 설명 |
|------|------|------|
| `CHAT` | `chat` | 일반 채팅방 |
| `GAME` | `game` | 게임방 (캐치마인드 등) |

---

## 3. 방 상태 (RoomStatus)

| 상태 | 코드 | 설명 | 게임 시작 가능 |
|------|------|------|:-------------:|
| `WAITING` | `waiting` | 대기 중 | O |
| `PLAYING` | `playing` | 게임 진행 중 | X |
| `FINISHED` | `finished` | 게임 종료됨 | O |

---

## 4. REST API 엔드포인트

### 채팅방 API (`/api/chat/rooms`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/rooms` | 채팅방/게임방 생성 |
| GET | `/rooms` | 방 목록 조회 (필터 지원) |
| GET | `/rooms/{roomId}` | 방 상세 조회 |
| POST | `/rooms/{roomId}/join` | 방 입장 (roomToken 발급) |
| POST | `/rooms/{roomId}/leave` | 방 퇴장 |
| DELETE | `/rooms/{roomId}` | 방 삭제 (방장만) |

### 게임 API (`/api/game`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/rooms/{roomId}/game/start` | 게임 시작 |
| POST | `/rooms/{roomId}/game/stop` | 게임 중단 |
| GET | `/rooms/{roomId}/game/status` | 게임 상태 조회 |
| GET | `/rooms/{roomId}/game/scores` | 점수판 조회 |

---

## 5. 방 목록 조회 쿼리 파라미터 (업데이트됨)

```
GET /api/chat/rooms?type=GAME&gameType=CATCHMIND&status=WAITING&level=intermediate&limit=10&cursor=xxx
```

| 파라미터 | 타입 | 설명 | 예시 |
|----------|------|------|------|
| `type` | string | 방 타입 필터 | `CHAT`, `GAME` |
| `gameType` | string | 게임 타입 | `CATCHMIND` |
| `status` | string | 상태 필터 | `WAITING`, `PLAYING`, `FINISHED` |
| `level` | string | 난이도 필터 | `beginner`, `intermediate`, `advanced` |
| `limit` | number | 조회 개수 (기본 10, 최대 20) | |
| `cursor` | string | 페이지네이션 커서 | |

### 필터 조합 예시

```bash
# 대기 중인 게임방만
GET /api/chat/rooms?type=GAME&status=WAITING

# 캐치마인드 게임방만
GET /api/chat/rooms?type=GAME&gameType=CATCHMIND

# 초급 난이도 채팅방
GET /api/chat/rooms?type=CHAT&level=beginner

# 진행 중인 고급 게임방
GET /api/chat/rooms?type=GAME&status=PLAYING&level=advanced
```

### 응답 예시

```json
{
  "success": true,
  "message": "Rooms retrieved",
  "data": {
    "rooms": [
      {
        "roomId": "abc-123",
        "name": "초보자 영어 스터디",
        "type": "GAME",
        "gameType": "CATCHMIND",
        "status": "WAITING",
        "level": "beginner",
        "currentMembers": 3,
        "maxMembers": 6,
        "currentRound": 0,
        "totalRounds": 5,
        "createdAt": "2026-01-22T10:00:00Z"
      }
    ],
    "nextCursor": "eyJQSyI6Ik...",
    "hasMore": true
  }
}
```

---

## 6. 방 생성 요청 (업데이트됨)

### 채팅방 생성
```json
{
  "name": "영어 스터디 채팅방",
  "type": "CHAT",
  "level": "beginner",
  "maxMembers": 6,
  "description": "초보자를 위한 영어 채팅방"
}
```

### 게임방 생성
```json
{
  "name": "캐치마인드 게임",
  "type": "GAME",
  "gameType": "CATCHMIND",
  "level": "intermediate",
  "maxMembers": 8,
  "description": "영어 단어 맞추기 게임"
}
```

---

## 7. 프론트엔드에서 방 타입 구분

### 방법 1: API 필터 사용 (권장)
```javascript
// 게임방만 조회
const gameRooms = await fetch('/api/chat/rooms?type=GAME');

// 대기 중인 게임방만
const waitingGames = await fetch('/api/chat/rooms?type=GAME&status=WAITING');

// 채팅방만
const chatRooms = await fetch('/api/chat/rooms?type=CHAT');
```

### 방법 2: 전체 조회 후 클라이언트 필터링
```javascript
const allRooms = await fetchRooms();

// 게임방만
const gameRooms = allRooms.filter(room => room.type === 'GAME');

// 채팅방만
const chatRooms = allRooms.filter(room => room.type === 'CHAT');

// 대기 중인 방만
const waitingRooms = allRooms.filter(room => room.status === 'WAITING');
```

---

## 8. WebSocket 연결

### 채팅/게임 WebSocket
```
wss://t378dif43l.execute-api.ap-northeast-2.amazonaws.com/dev?roomToken={roomToken}
```

### Grammar WebSocket
```
wss://ltrccmteo8.execute-api.ap-northeast-2.amazonaws.com/dev?token={jwtToken}
```

### 연결 순서
1. `POST /rooms/{roomId}/join` → `roomToken` 발급
2. WebSocket 연결 시 `roomToken` 쿼리 파라미터로 전달

---

## 9. WebSocket 메시지 타입 (messageType)

| 코드 | 타입 | 설명 |
|------|------|------|
| `MSG` | 일반 메시지 | 일반 채팅 메시지 |
| `VOICE` | 음성 메시지 | 음성 채팅 |
| `JOIN` | 입장 알림 | 사용자 입장 |
| `LEAVE` | 퇴장 알림 | 사용자 퇴장 |
| `GAME_START` | 게임 시작 | 게임 시작 알림 |
| `GAME_END` | 게임 종료 | 게임 종료 + 최종 순위 |
| `ROUND_START` | 라운드 시작 | 새 라운드 시작 |
| `ROUND_END` | 라운드 종료 | 정답 공개 |
| `ANSWER_CORRECT` | 정답 | 정답 맞춤 |
| `HINT` | 힌트 | 힌트 제공 |
| `SKIP` | 스킵 | 라운드 스킵 |
| `SYSTEM` | 시스템 | 시스템 메시지 |

---

## 10. 게임 명령어 (WebSocket)

채팅 메시지로 게임 명령어 전송:

| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/start` | 게임 시작 | 방장 (2명 이상 접속 시) |
| `/stop` | 게임 중단 | 방장 또는 게임 시작자 |
| `/skip` | 라운드 스킵 | 누구나 |
| `/hint` | 힌트 제공 | 출제자만 |
| `/score` | 점수 확인 | 누구나 |

---

## 11. 게임 시작 응답 예시

```json
{
  "messageId": "uuid",
  "roomId": "abc-123",
  "userId": "SYSTEM",
  "content": "게임 시작!\n총 5 라운드\n\n라운드 1 시작!\n출제자: user-456",
  "messageType": "GAME_START",
  "createdAt": "2026-01-22T10:00:00Z",
  "serverTime": "2026-01-22T10:00:00Z",
  "domain": "GAME",
  "type": "GAME",
  "status": "PLAYING",
  "currentRound": 1,
  "totalRounds": 5,
  "currentDrawerId": "user-456",
  "drawerOrder": ["user-456", "user-789", "user-123"]
}
```

---

## 12. 정답 체크 로직

- **한국어** 또는 **영어** 둘 다 정답으로 인정
- 대소문자 구분 없음
- 공백 무시

### 점수 계산
```
기본 점수: 10점
시간 보너스: (제한시간 - 경과시간) * 0.5
연속 정답 보너스: 연속정답수 * 2

총점 = 기본점수 + 시간보너스 + 연속정답보너스
```

---

## 13. 게임 설정

| 설정 | 기본값 | 환경변수 |
|------|--------|----------|
| 총 라운드 수 | 5 | `GAME_TOTAL_ROUNDS` |
| 라운드 제한 시간(초) | 60 | `GAME_ROUND_TIME_LIMIT` |
| 빠른 정답 기준(ms) | 5000 | `GAME_QUICK_GUESS_THRESHOLD_MS` |
| 게임 전체 제한(초) | 420 (7분) | `GAME_TIME_LIMIT_SECONDS` |

---

## 14. 주의사항

1. **roomToken은 한 번만 사용**: 재연결 시 새로 발급 필요
2. **WebSocket 연결 실패 시**: `POST /rooms/{roomId}/join`으로 새 토큰 발급
3. **게임 중 퇴장**: 자동으로 다음 출제자로 넘어감 (2명 미만 시 게임 종료)
4. **출제자는 정답 입력 불가**: 본인이 출제자일 때 채팅해도 정답 체크 안됨
5. **방 타입 변경 불가**: 생성 시 지정한 type은 변경 불가

---

## 15. 에러 코드

| 코드 | HTTP | 설명 |
|------|------|------|
| `ROOM_001` | 404 | 채팅방 없음 |
| `ROOM_002` | 409 | 채팅방 이미 존재 |
| `ROOM_003` | 400 | 채팅방 인원 초과 |
| `ROOM_004` | 400 | 채팅방 종료됨 |
| `ROOM_005` | 401 | 비밀번호 틀림 |
| `ROOM_006` | 403 | 방장 권한 없음 |
| `MEMBER_001` | 403 | 채팅방 멤버 아님 |
| `MEMBER_002` | 409 | 이미 참여 중 |
| `GAME_001` | 400 | 게임 시작 실패 |
| `GAME_002` | 400 | 게임 중단 실패 |
| `GAME_003` | 400 | 게임 진행 중 아님 |
| `GAME_004` | 409 | 게임 이미 진행 중 |
| `GAME_005` | 403 | 게임 시작자 아님 |
| `GAME_006` | 404 | 게임 없음 |
| `GAME_007` | 400 | 채팅방에서 게임 불가 |
| `GAME_008` | 400 | 게임 재시작 불가 |
| `GAME_009` | 403 | 방장만 게임 시작 가능 |

---

## 16. UI 구현 가이드

### 탭 구조 (권장)
```
[전체] [채팅방] [게임방]
```

### 게임방 상태 표시
```
대기 중 (WAITING) → 초록색 뱃지 "참여 가능"
진행 중 (PLAYING) → 빨간색 뱃지 "게임 중"
종료됨 (FINISHED) → 회색 뱃지 "종료"
```

### 게임방 카드 정보
```
┌─────────────────────────────┐
│ 캐치마인드 - 영어 단어 맞추기 │
│ [게임방] [intermediate]     │
│                             │
│ 👥 3/8명  🎮 대기 중        │
│ 🕐 2026-01-22 10:00        │
└─────────────────────────────┘
```
